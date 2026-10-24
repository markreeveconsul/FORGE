# FORGE AI reliability

FORGE AI uses one shared transport for chat, review summaries, visual search and AI Manager generation. Local catalog/order retrieval and database operations stay local.

## Retries and timeouts

- At most three attempts per generation request, with increasing waits and jitter.
- Retry temporary HTTP 408/500/502/503/504 failures and transient I/O timeouts.
- A 429 is retried only when structured quota metadata identifies a minute limit with a nonzero or unspecified allowance and the provider supplies a short delay (10 seconds or less). Daily, unknown-window and zero-allowance failures do not retry automatically.
- Daily/unknown quotas, access errors, unsupported models, invalid requests, DNS and TLS failures do not automatically retry.
- Retry-After and Google RetryInfo are honored. Quota cooldowns are shared across AI features.
- The total transport budget is 90 seconds per request; later attempts receive only the remaining time. Chat has a 40-second attempt limit; image/admin generation has a 60-second attempt limit.
- Cancellation interrupts in-flight requests and backoff waits. A cancelled provider request might already have consumed quota.
- A brief three-second spacing guard limits rapid different chat/image requests. Cache hits bypass this guard.
- Admin planning and drafting can make separate calls; each has its own bounded transport budget. Retries never execute an approval or database write.

## Caches and visual payloads

All caches are bounded, memory-only and expire. No uploaded reference photo is written to disk.

- Eligible public catalog/review answers: two minutes, at most 24 entries / 256 KB.
- Visual matches: two minutes, at most eight entries / 128 KB.
- Prepared catalog thumbnails: ten minutes, at most 64 entries / 4 MB.
- Visual result keys include a digest of the reference image and current catalog records/photos. Changes invalidate matching cached results. Returned cards still use current local records.
- Answers with cart/order records, conversation history, user preferences or unrecognized context fields are not cached.
- Admin plans, drafts, sales totals and database mutations are not cached.
- New chat and sign-in/sign-out clear these session caches. Failed and incomplete responses are not cached.
- Reference photos retain up to 512 pixels on the longest edge; catalog comparison photos use 256 pixels, JPEG quality 0.72. Encoded thumbnails are reused.
- Up to 40 active products with photos are compared; the 4 MB payload guard refuses oversized requests rather than silently dropping candidates.
- Internal reasoning text is excluded from visual JSON parsing; incomplete/truncated responses are rejected.
- Gemini 3 requests use low thinking effort. Unsupported custom sampling settings are omitted for Gemini 3.6+ / later major versions.

## Diagnostics

Admin > AI Manager > AI diagnostics shows the last 100 request events from the current process.

Metadata is saved locally to data/ai-diagnostics.log, rotating to data/ai-diagnostics.previous.log at approximately 512 KB. Both paths are inside the already Git-ignored data directory.

Events contain only timestamp, feature, model, outcome/error category, attempt number, HTTP status, duration, payload bytes and sanitized provider quota metadata (quota ID/metric, model, resource, time window, reported allowance and suggested retry delay). They never contain API keys, request/response text, uploaded images, customer details or identifiers. Provider error text is not written to the diagnostic log.

Error messages distinguish timeout, quota, access, model, request validation, service availability and transport failures. A fallback to local records does not mean the device lost internet access.

## Quota explanations and help

Customer assistant > AI limits and Admin > AI Manager > AI limits explain shared usage and display the latest restriction reported in this session. This is historical information, not live usage. FORGE does not invent used/remaining counters or a reset time.

Only google.rpc.QuotaFailure violation quotaId/quotaMetric fields establish a minute or daily window. Generic error text, Help links, descriptions and subjects do not establish an exceeded daily quota. Conflicting or missing fields remain unknown. Only a safe model dimension is retained; project IDs, subjects and free-form descriptions are never logged.

Reported quotaValue is an allowance, not remaining usage. Zero is shown as an access/configuration issue to check. Retry-After and typed google.rpc.RetryInfo hints are retained, taking the longer valid delay. A short hint does not confirm that a daily allowance will reset then. Multiple violations are preserved; any confirmed daily violation prevents automatic quota retries.

Old log entries cannot be reconstructed because raw provider responses were never stored. New failed requests include the safe quota fields; diagnostics and the AI limits help do not make extra API requests.

## Verification and limits

AiReliabilityTest uses fake responses, injected clocks and controlled waits. It spends no live API quota. Existing AI Manager and robot UI checks exercise reviewed proposals and customer error feedback.

These changes reduce repeated work and recover from temporary faults. They do not increase provider quotas or guarantee server latency. Retries may take longer than immediately reporting an error and may consume additional generation quota.

Provider references:
- https://ai.google.dev/gemini-api/docs/troubleshooting
- https://ai.google.dev/gemini-api/docs/rate-limits
