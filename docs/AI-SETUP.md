# Ask FORGE (optional Gemini demo)

Open **Ask FORGE** from the storefront launcher or footer. Category and budget listings run locally
from the current catalog. For an AI explanation, cart review, comparison, or order explanation, the
assistant first explains what will be shared and waits for consent. Order questions require a separate
approval before the order summary is retrieved. Up to four short chat turns may be sent as follow-up
context. The assistant cannot change accounts, inventory, carts, or orders; adding products always
requires a separate confirmation in FORGE.

## Free-tier setup

1. Create an API key at https://aistudio.google.com/apikey for a project **without billing enabled**.
2. The default is `gemini-3.8-flash`: Google's current free-tier Flash model. Review its current quota
   and terms before using it.
   Google lists free input/output at https://ai.google.dev/gemini-api/docs/pricing . Availability can change.
3. Start **start FORGE .bat** or run `.\build.ps1 Run`. The launcher asks you to confirm the free-tier project,
   then requests the key at a masked prompt. FORGE saves it encrypted with Windows DPAPI for your account on this PC,
   so later launches won't ask again.
   To start from PowerShell without the launcher's prompts, use:

```powershell
$forgeKey = Read-Host '' -AsSecureString
$env:GEMINI_API_KEY = [System.Net.NetworkCredential]::new('', $forgeKey).Password
$env:FORGE_AI_FREE_TIER_CONFIRMED = 'true'
$env:FORGE_AI_MODEL = 'gemini-3.8-flash'
.\build.ps1 Run
```

The key is not stored in Java, pom.xml, `.env`, screenshots, or Git. Only the encrypted `.forge-gemini-key.xml`
file is saved locally, and `.gitignore` excludes it. Windows can decrypt it only for your account on this PC.
Delete that file to make FORGE forget the key; revoke the key in AI Studio if you want to invalidate it.
For a packaged app shared with other people, put the API integration behind an authenticated server;
do not distribute your developer key. This direct API connection is for your local prototype.

The confirmation variable is your acknowledgement, **not a billing check or spending cap**.
The API key cannot tell this app whether Google has enabled billing. Keep billing disabled on Google
to meet your free-only requirement. No automatic retries, model fallbacks, web grounding, or paid tools
are used. Requests have a 40-second timeout, 1,000-character questions, 1,536 output-token cap,
15-second spacing and a 60-second pause after HTTP 429. Google's quota can still be lower.

## Information sent

- Specifications: matching product catalog text, price and stock; no photos. The assistant can include
  up to six matching products for search follow-ups and comparisons.
- Cart: at most 20 distinct items, quantities, snapshot prices and current product records.
  Price/stock warnings and totals are computed locally. Compatibility is not certified.
- Orders: up to five recent orders belonging to the signed-in customer, projected to number, date,
  total, item quantity and simulated payment status. No address, username, payment method or credentials.
- The current question and up to four short conversation turns are sent, so do not enter sensitive information. Free-tier inputs/outputs
  may be used to improve Google's products; use demo data.

Only questions requiring an AI answer, after explicit consent, send requests. Closing the panel cancels the local request (Google may
already have received it). Answers are plain text, not executable actions, and disappear on close.
Source records shown below an answer are from FORGE, not generated citations. The model can still
make mistakes. The app has no carrier tracking or real delivery: never interpret payment status as shipping.

Missing key: local product listings continue to work; AI explanations show setup guidance and make no cloud calls.
Quota exhaustion, missing models, authentication failures, blocked/incomplete answers and network failures show readable messages.
Tests use fake responses, never a real API key or paid requests.
