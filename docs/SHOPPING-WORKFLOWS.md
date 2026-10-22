# Shopping and order workflows

These features run locally with JavaFX and SQLite. They do not require an AI request.

## Order processing

Open **Orders** in the admin sidebar and select an order row. The processing card shows the purchase-time items, address, payment record and delivery timeline.

- Confirmed → Packed → Shipped → Delivered.
- Shipping requires a carrier name and tracking number. These are manually recorded details, not a live carrier integration.
- An admin can cancel Confirmed or Packed orders. Cancellation restores stock once, in the same transaction as the delivery/payment update and timeline entry.
- Customers can cancel their own Confirmed, unpaid orders from **My orders**. Packed, shipped, delivered and paid orders cannot be cancelled through that customer button.
- Cancelling a paid order records a **simulated refund**; no real payment service is contacted.
- Delivery and payment are independent. A delivered COD order remains pending until an admin chooses **Record COD payment collected · demo**.
- Stale status updates, invalid transitions and duplicate cancellations are rejected.

Existing orders receive a Confirmed delivery state during additive migration; FORGE does not invent past shipping or delivery events. Historical prices, names and totals remain unchanged.

## Structured specifications and configurations

The product editor shows identity and category-specific fields, including Brand, Model, SKU and Variant. Laptop templates include CPU, GPU, RAM, memory type, storage, screen size and refresh rate. PC-part templates include socket, VRAM, physical clearance and power requirements. Custom facts remain available under Additional specifications / Edit as text.

Use explicit units, for example `16 GB`, `1 TB`, `650 W` and `300 mm`. Duplicate recognized keys and invalid quantities are rejected. Existing unchanged legacy specifications are preserved when editing unrelated fields.

Configurations are separate inventory products with their own price and stock. Give them the same Brand and exact Model, a different Variant and a unique SKU. Their product page then offers a Configuration selector. No variant or specification is guessed from the name.

The filter drawer offers available Brand, RAM, VRAM, Storage, Memory type, Socket, Connection and Color values from the current catalog/search. Applying a filter shows a removable chip. Products without a selected fact do not match that fact; they remain listed when the filter is cleared.

Specifications retain the compatible key:value storage format used by existing detail views and AI integrations.

## Persistent customer carts

Signed-in cart additions, quantity changes, removals and confirmed assistant cart changes are persisted. A failed save leaves the visible cart unchanged. Guests keep a session-only cart, which merges with the saved customer cart on sign-in.

Restoration reads current products and prices, removes unavailable items, limits quantities to available stock and displays any adjustments. Cart contents are not stock reservations. Checkout still revalidates availability and prices.

Successful checkout clears saved cart rows in the same SQLite transaction as the order and stock deduction. Failed or declined checkout preserves the saved cart. Signing out clears the current session but keeps the customer's saved cart private until they sign in again. No cross-device synchronization is provided by the local database.

## Delete product

In admin Products, select a row and choose **Delete product**. The confirmation identifies the product. Cancelling leaves inventory unchanged.

Deletion removes the item from the storefront and inventory list and clears its saved-item/cart links. A deleted marker retains the underlying record so old receipts, audit records and cancellation stock references remain valid. It cannot be reactivated through ordinary edits or AI status changes. Deactivate an item instead when you expect to offer it again.

## Verification

`CommerceWorkflowTest` covers migrations, persistence failures, rollback, ownership checks, stock restoration, concurrent checkout/cancellation, specification validation, SKU uniqueness and receipt-preserving deletion.

`CommerceUiSmoke <evidence-directory>` runs synthetic customer/admin data through filtering, configuration selection, saved-cart restoration, checkout, tracking updates, deletion confirmation and the specification editor. It never uses the production database or a live AI provider.