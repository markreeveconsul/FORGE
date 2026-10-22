# FORGE admin dashboard

The JavaFX workspace follows `forge_store_dashboard_redesign.html`: a full-height charcoal navigation rail, white FORGE wordmark, light/obsidian themes, breadcrumb header, rounded metric and chart cards, inventory and report tables, and a dedicated AI command workspace.

Administrators land on Overview. All counts and values come from SQLite rather than the reference HTML's demo arrays. Revenue Performance sums PAID orders per local calendar day for the last 7 or 30 days. Total Value includes all recorded order totals; payments remain simulated. Growth percentages and forecasts are not invented.

## Controls

- The theme button or **Ctrl+D** switches Light/Dark Obsidian for the admin workspace.
- **Ctrl+K** opens the searchable command menu. Enter runs the first matching command; Escape dismisses overlays.
- **Add Product** opens the existing validated product editor in a modal, including photos and specifications.
- Double-click a product or order row to inspect it in a right drawer. The product drawer's Edit action opens the product editor.
- Press Enter or leave a price/stock field to save an inline inventory edit. Values are validated and stale edits are rejected; other product fields are preserved.
- Select inventory checkboxes to reveal the bulk toolbar. Bulk AI Fix and Set Inactive open AI Manager with the selected IDs for review (maximum 20 per batch).
- Export CSV opens a save dialog and exports the selected inventory. Text cells are quoted and formula-like values are escaped.

AI Manager retains validated tool calling, local fallback, error details, reviewable drafts, and atomic application. Its terminal records actual task activity rather than simulated database changes or invented reasoning. See [AI-MANAGER.md](AI-MANAGER.md).

The storefront and login retain their own theme. The all-white wordmark is in the admin navigation rail. Native desktop typography and controls may differ slightly from browser rendering of the reference.
