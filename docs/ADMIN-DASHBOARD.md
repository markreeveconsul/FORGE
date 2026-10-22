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

## Revenue explorer

Revenue Performance provides 7/30/90-day presets and a Custom dates control (up to 366 days), a previous-period overlay, range navigation, and refresh. Hover anywhere inside the plot to inspect the nearest date; click to see the contributing orders. Keyboard users can focus the chart, use Left/Right, press Enter for the order breakdown, and Escape to clear it.

Paid revenue includes only orders whose recorded payment status is PAID. All order value is a separate metric that includes every payment status. Both use recorded order totals, not today's product prices or line-item quantity sums. Aggregation uses integer cents; averages use decimal currency rounding. Days without orders remain zero. The dashed comparison uses the immediately preceding period with the same number of calendar days; no percentage is invented when its total is zero. Today is explicitly marked incomplete.

The chart groups by the order's creation date in the displayed computer timezone, because FORGE does not record payment timestamps. It therefore shows the current paid value of orders placed in that period, rather than cash collected on a historical payment date. Future-dated orders are excluded, and invalid records are counted rather than silently assigned a date. Refresh preserves the selected range/metric and keeps the chart visible while reading SQLite.
