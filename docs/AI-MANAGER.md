# FORGE AI Manager

Sign in as an administrator and choose **AI Manager** in the Manage sidebar. The module occupies the same content area as Products, Orders and Customers. Switching modules cancels pending reads and discards unsaved previews.

## Commands

- “Fix missing content”, “fill in product details”, or “products with no description” prepare missing-content drafts.
- “Show low stock” finds active products with five or fewer units.
- “Find inactive laptops with stock” queries current inventory.
- “Show this week's sales and restock attention” calculates local order totals and lists low stock.
- “Deactivate active PC parts under $20 with low stock” previews status changes. Prices are USD; “under” is exclusive.

Quick actions show the current content count, low stock, sales and inactive inventory. The Run button and Enter submit the command. A shimmer appears inside the results area while work is pending; reduced motion displays static placeholders.

## Interpretation and fallback

The primary path uses the existing Gemini configuration with declared functions: `fix_content`, `find_low_stock`, `query_products`, `sales_summary`, and `update_status`. A `clarify` function handles unsupported or ambiguous requests. Function arguments are validated before local Java services retrieve data; no generated SQL is executed.

The initial **AI ready** badge means the saved configuration is present; **AI Active** appears after a successful request. If the AI is unconfigured, the badge shows **Offline mode**. A failed configured request shows **Local fallback** with the failing stage (command interpretation or content drafting), HTTP/status details, and a **Retry command** button. Keys are redacted from error details. No API failure is silently treated as loss of internet connectivity.

Token similarity and synonym groups infer common commands, including minor spelling errors. Local requests accept categories, product #IDs and basic price/stock filters. Ambiguous names or exclusions ask for a clearer selection. Local fallback can query inventory, calculate reports and prepare status previews, but cannot generate AI content. Each Run checks the AI again; a failed request does not permanently switch it off. HTTP 429 has a shared 60-second cooldown, which the panel explains.

Gemini 3 admin requests use low thinking effort and a 4,096-token planning budget to reduce response truncation. Incomplete tool calls and drafts are rejected; a token-limit failure is identified as `MAX_TOKENS`. See [Google's thinking documentation](https://ai.google.dev/gemini-api/docs/generate-content/thinking) for model-specific behavior.

## Content review

Generated descriptions and specifications are editable previews. Existing nonempty fields are preserved. **Research missing specifications online** is optional and off initially. When enabled, Gemini searches for exact manufacturer models. Proposed specifications require an exact-model flag and retrieved source URLs; administrators must verify the variant and evidence before applying. Missing photos are never fabricated or silently marked complete. Sources remain attached to the saved change audit.

Search access and quotas depend on the configured model/project. If drafting fails, matched products remain visible with a recovery message; no automatic retry or paid model fallback occurs. Drafts contain at most 20 matching products per request, and the result explicitly identifies larger selections.

## Applying changes

Expand a draft, edit its fields if necessary, and deselect products you do not want to change. **Apply reviewed changes** saves the selected batch in one SQLite transaction. Products that changed since the preview cause the complete batch to roll back. A batch cannot be applied twice. The update only touches reviewed description/specification/status fields, preserving photos, prices, stock and receipts.

Committed batches and before/after values are recorded in `admin_ai_batches` and `admin_ai_changes`. Inventory rows and metrics are re-read after applying. Other admin modules read fresh database records when opened. Leaving during an already approved save does not cancel that transaction.

## Sales and privacy

Sales calculations run locally. Calendar periods use the machine's time zone; this week begins Monday. Order value includes recorded orders in the period, while paid value only includes orders marked PAID. Payments remain simulated. Restock attention reflects current active stock, not a predictive sales model.

Command text and relevant public catalog text can be sent to Google Gemini. Customer/order rows, addresses and account names are not included in model requests. The module's How it works link explains the external provider and free-tier data use. All service entry points enforce administrator access.
