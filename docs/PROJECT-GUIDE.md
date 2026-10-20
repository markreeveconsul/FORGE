# Project guide

## 1. Project overview
Market is a single-store JavaFX desktop application. Customers browse products, manage a cart, place simulated orders, and view history. Administrators maintain products and stock. The project demonstrates four OOP pillars through a connected workflow.

## 2. Objectives and scope
The store supports login/roles, catalog management, text and image search, cart, checkout/payment choice, inventory updates, verified product reviews, order history, and persistence. Six demo products and two accounts are created on an empty database. Excluded: registration, real money, delivery tracking, discounts, refunds, multiple concurrent application instances, and a web server. Default currency is USD with two decimal places.

## 3. Technology decisions
Java source targets JDK 21. JavaFX controls and CSS provide the GUI; Maven manages dependencies; SQLite/JDBC provides local persistence. JavaFX layouts are written directly in Java so a student familiar with Swing can trace construction and events without learning FXML at the same time.

SQLite is appropriate because the data is relational and checkout needs an atomic transaction. A server database adds deployment work without serving this single-instance scope. JavaFX was selected for layout and styling flexibility; it does not itself demonstrate the four OOP pillars.

No Spring, ORM, dependency injection framework, or network API is needed. ShopApp explicitly constructs repositories and services.

## 4. Architecture and class responsibilities
The call direction is GUI -> services -> models/repositories -> SQLite. Models never import JavaFX or JDBC.

| Package/class | Responsibility |
|---|---|
| model.User, Customer, Admin, Guest | Immutable identity, role-specific permission behavior |
| model.Product | Validate and expose an immutable product snapshot |
| model.Cart, CartItem | Own cart lines, quantities, and totals |
| model.Order, OrderItem | Immutable historical purchase snapshots |
| model.ProductReview | Public review from a customer with a completed purchase |
| model.Category | Fixed category choices |
| model.Money | Exact decimal validation, formatting, minor-unit conversion |
| payment.PaymentMethod | Contract for a local simulated payment decision |
| CashOnDeliveryPayment | Accept order with payment pending |
| SimulatedWalletPayment | Return paid or declined result for demonstrations |
| AuthService / Passwords | Authentication / password hashing |
| ProductService | Catalog queries and authorized admin changes |
| CartService | Ownership checks and fresh product lookup before cart updates |
| CheckoutService | Validate and coordinate one database transaction |
| OrderService | Return only the signed-in customer's history |
| DatabaseManager | Connections, schema creation, and first-run seed data |
| UserRepository | Credential lookup and role reconstruction |
| ProductRepository | Product reads/writes and conditional stock deduction |
| ProductReviewRepository / ProductReviewService | Persist reviews and require a completed purchase before a customer can write one |
| OrderRepository | Order/item persistence and customer-scoped retrieval |
| ShopApp / Services | Entry point / dependency wiring |
| ShopWindow | Session state, cart badge, checkout draft, and active navigation |
| LoginView, CatalogView, ProductDetailView, CartView, CheckoutView, ConfirmationView, OrdersView, AdminView | One screen each |
| Motion | Session motion preference and reusable hover effects |
| CollectionArtwork / StoreIcon | Original offline demo illustrations / consistent navigation icons |
| EditorialHeader | Layered headset campaign with headphone search, collection navigation and animation lifecycle |
| ProductCard / ProductArtwork | Reusable product card / uploaded photo, original demo illustration or neutral placeholder |
| ProductPhotos / ProductPhotoEditor | Image validation / main-photo and gallery-angle drafts |
| ProductGallery | Full photo view, actual uploaded thumbnail selection and image enlargement |
| VisualProductSearch | Rank catalog photos against a chosen local image using color histograms; no image upload or network request |
| ProductReviewsView | Show verified-purchase reviews, collect reviews, and request an optional Gemini summary |
| CatalogState / DeliveryAddress | Retained filters / validated delivery value object |
| OrderSummary / CheckoutDraft | Reusable cart summary / temporary checkout input |
| Ui | Shared components, inline feedback, error presentation, background task wrapper |
| exception package | Validation, out-of-stock, and database failure signals |

Constructors receive required dependencies. User and Product fields are private/final; their public getters expose values, not writable fields. Cart has intentional mutation methods instead of exposing its map. Records generate private final fields and public accessors; Order also copies its item list.

## 5. OOP pillars and UML

| Pillar | Concrete code | Defense explanation |
|---|---|---|
| Encapsulation | Product constructor; Cart.add/setQuantity/getItems | Invalid product state is rejected at construction; callers cannot edit the cart's internal collection. Getters do not require matching setters. |
| Inheritance | Customer extends User; Admin extends User | Both share identity, while subclasses implement different permissions. |
| Polymorphism | CheckoutService calls payment.pay(total); ProductService calls actor.canManageProducts() | A base type/interface reference invokes the concrete object's overridden method at runtime. |
| Abstraction | abstract User; interface PaymentMethod | A user must provide role behavior; checkout needs the payment contract without depending on the implementation. |

The source diagram is classes.puml. Render with a PlantUML viewer. It includes domain, services, persistence, GUI, and exception relationships. Standard-library types are shown only where useful.

Composition: Cart owns its CartItems; Order owns its OrderItems. Association: CartItem refers to a Product that also exists outside the cart. Customer-to-order is represented by a stored customerId, not a Java Customer field. Do not claim every database foreign key is a direct Java object association.

## 6. Data design and transaction flow

| Table | Important fields |
|---|---|
| users | id, unique username, password_hash, role |
| products | id, name, category, price_cents, stock, active, description, photo (BLOB), specifications |
| product_photos | product_id, position (0–3), photo (BLOB); additional gallery angles |
| orders | id, customer_id, created_at, address, payment_method, payment_status, total_cents |
| order_items | order_id, product_id, product_name, unit_price_cents, quantity |
| product_reviews | product_id, customer_id, rating, review, created_at; one row per product/customer, only after purchase |

SQLite foreign keys are enabled on every connection. Money is BigDecimal in Java and integer cents in storage. Order timestamps are stored as UTC instants and displayed in the machine's local timezone.

Checkout:
1. Check customer/cart ownership, nonempty cart, address, and payment selection.
2. Open a connection and disable autocommit.
3. Reload each product; reject inactive products or changed prices.
4. Deduct stock with a conditional UPDATE requiring sufficient stock.
5. Invoke payment.pay(total); reject a declined result.
6. Insert the order and purchase-time line snapshots.
7. Commit, then clear the cart.
8. On a validation/SQL/runtime failure inside the transaction, roll back and preserve the cart.

All repositories in checkout receive the SAME connection. Opening an independent connection for each write would break this transaction. Real payment services have external side effects that a SQLite rollback cannot undo; this design intentionally uses local simulations only.

Products are deactivated instead of deleted. Order items preserve names and prices so historical receipts survive catalog edits. Photos are bounded SQLite BLOBs, copied defensively by Product. An additive migration preserves existing rows. See STOREFRONT-UPDATE.md for upload instructions and the storage tradeoff.

Guests may browse and build a cart; checkout and history require an authenticated Customer. CartService transfers guest snapshots and checks for changes without silently changing prices.

The cart is not a reservation. A price change is rejected rather than silently charging the customer a new price.

## 7. GUI and navigation

| Screen | Components | Backend calls |
|---|---|---|
| Login | TextField, PasswordField, Button | AuthService.login |
| Catalog | TilePane of ProductCards, text/image search, category/sort controls | ProductService.search; VisualProductSearch; CartService.add |
| Product details | Uploaded gallery, description/specifications, stock, quantity control, verified reviews and optional AI summary | ProductService.get; CartService.add; ProductReviewService; GeminiAssistant |
| Cart | Item rows, +/- and quantity fields, OrderSummary | CartService.update; Cart.remove; Cart.getTotal |
| Checkout | Recipient/street/city/postal fields, payment ComboBox, inline errors, OrderSummary | CheckoutService.checkout |
| Orders | TableView and formatted receipt panel | OrderService.history |
| Admin | Filterable TableView, add/edit form, description and photo editor, field errors, low-stock alert list | ProductService.search/save; AdminService.lowStock |

Ui.async runs database/hashing operations in a JavaFX Task on a background thread. Its completion callbacks update controls on the JavaFX application thread. The active scene is disabled while an operation initiated from an attached screen is in progress, preventing duplicate submissions.

The CSS is in src/main/resources/shop.css. Screenshots in docs/screenshots are actual JavaFX scene snapshots generated through the GUI smoke runner; they are not design mockups.

## 8. Validation and error handling

| Input/situation | Rule | User feedback |
|---|---|---|
| Login fields | Both required | Username and password are required. |
| Invalid credentials | Exact username lookup and hash verification | Incorrect username or password. |
| Product name | Trimmed, 1–80 characters | Product name is required and must be at most 80 characters. |
| Photo | PNG/JPEG, <=5 MiB, dimensions <=4096 each, decodes successfully | Inline upload error; previous photo retained |
| Product reviews | Signed-in customer, completed purchase, rating 1–5, 8–800 characters; one editable review per customer/product | Purchase and field validation messages |
| Description | Optional, <=1,000 characters | Description must be at most 1,000 characters. |
| Recipient/city/postal | Required; validated by DeliveryAddress | Field-specific messages |
| Category | Must be chosen | Choose a category. |
| Price | Numeric, >0, <=1,000,000, at most 2 decimals | Numeric/range/decimal-specific message |
| Stock | Whole number, 0–100,000 | Whole-number or stock-range message |
| Cart quantity | Whole number, 1–100,000, <=available stock | Quantity/range or OutOfStockException message |
| Empty cart | Cannot check out | Your cart is empty. |
| Delivery address | Trimmed, 5–250 characters | Enter a delivery address between 5 and 250 characters. |
| Missing payment | Must select a method | Choose a payment method. |
| Declined wallet | Roll back transaction | Payment declined; cart and stock unchanged |
| Product price changed | Require cart update before checkout | Price changed; update its cart quantity to review |
| Inactive product | Cannot purchase | Remove unavailable product from cart |
| Unauthorized admin action | Service checks canManageProducts() | Administrator access is required. |
| Database failure | Roll back and retain cause | Database could not complete operation; try again |
| No table selection | Select before acting | Select a row first. |

ValidationException represents a user-correctable issue. OutOfStockException specializes it. StorageException wraps SQL/IO failures; the UI displays a useful message and logs the underlying stack trace.

## 9. Testing and implementation milestones
See TESTING.md for verified results and manual checks. ShopTest uses a fresh temporary SQLite file per test; no tests alter the user's data/shop.db. GuiSmoke uses a separate database and fires actual JavaFX buttons.

Completed:
- [x] Domain models and OOP contracts
- [x] SQLite schema and repositories
- [x] Services and atomic checkout
- [x] JavaFX screens and styling
- [x] Validation and failure-case tests
- [x] GUI smoke flow and seventeen snapshots
- [x] Maven build and Windows helper
- [x] Documentation and UML source

Before submission:
- [ ] Personally trace every public method used in the demo
- [ ] Run the project on the presentation machine
- [ ] Test resizing, keyboard-only navigation, and long input on that machine
- [ ] Adapt documentation headings to the instructor's actual ten-section template
- [ ] Rehearse a small live modification and update UML when signatures change
- [ ] Capture any additional screenshots your instructor specifically requires

Suggested remaining two-week work: days 1–3 understand models and services; days 4–6 practice JavaFX changes; days 7–9 run manual cases and make small refinements; days 10–12 reconcile documentation/UML; days 13–14 rehearse and avoid adding features.

## 10. Limitations, rubric, and future work

| Rubric | Evidence |
|---|---|
| OOP 60 | Explicit four-pillar table, role hierarchy, payment interface, private state |
| GUI 10 | Eight separate screens, shared styling, errors, working navigation |
| Functionality 10 | Connected catalog -> cart -> checkout -> order history workflow |
| Quality 5 | Focused packages/classes, explicit dependencies, tests |
| Validation 5 | Domain/service checks plus GUI parsing and useful messages |
| Documentation/UML 5 | This guide, class diagram, screenshots, testing record |
| Defense 5 | DEFENSE.md plus personal walkthrough and live-edit rehearsal |

A working implementation does not guarantee marks: the student must understand and explain it. Database persistence is supporting infrastructure, not evidence of OOP by itself.

Potential later work: FXML after understanding JavaFX, persisted carts, discounts through a pricing policy, packaged runtime image. Avoid adding those until the current code can be explained confidently.

Image search is an offline visual similarity feature: it ranks active catalog products by color-histogram similarity and skips products without a photo. It does not recognize objects or infer product type. Review summaries send only the product name, ratings and review text to Gemini after separate consent; reviewer names and account details are excluded. The admin overview lists active products with five or fewer units and links to inventory editing; this is a threshold alert, not a sales forecast.

## Approved UI/UX refinement

The existing task branch carries ongoing improvements. The storefront hero remains unchanged. Login uses a large, proportional PC crop; purchase actions use orange, catalog cards show brief Added feedback and the toast offers Review cart. Cart and checkout stack below 920 px of content width, preserving one outer scroll surface. Drawer keyboard focus cycles within the overlay and returns to a visible trigger.

Gallery uploads support one main photo plus four real additional angles. Optional specifications use one key/value per line. The additive database migration preserves products and receipts; product metadata, primary photo and gallery rows are saved in one transaction. Existing stock-only edits preserve the gallery and specifications. Removing the main photo in the editor clears its gallery; canceling an edit preserves saved data.


## Next UI implementation

See [Approved next UI](NEXT-UI.md) for saved items/profile/address persistence, search suggestions, range filters, gallery zoom, order cards, Buy again, Undo, responsive layouts, photo framing and motion. Both existing campaign artworks/designs are retained.
