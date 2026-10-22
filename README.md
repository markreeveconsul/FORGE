# FORGE — Java OOP E-commerce System

A small JavaFX desktop store with SQLite persistence. Built as a readable course project, not a production commerce service.

## Start on Windows

Open a PowerShell terminal in this folder:

```powershell
.\build.ps1 Run
```

The helper finds a JDK through JAVA_HOME or Program Files/Java, then Maven through MAVEN_HOME, PATH, or the Oracle Java VS Code extension installed on this machine. It does not change global settings. Dependencies are cached in .tools/m2. The first run on a new machine needs internet access.

If your PowerShell policy prevents running local scripts, use your IDE's Maven support with the goals `compile javafx:run`, or run `mvn compile javafx:run` in a terminal with Maven configured. Do not double-click the ordinary JAR; it is not a standalone installer.

**Requirements:** JDK 21 or newer and Maven. Verified here with JDK 26.0.1 on Windows. The compiler targets Java 21; JavaFX is pinned to 21.0.6. Linux checks also passed with Temurin JDK 21.0.12.1 and Maven 3.9.9; presentation-machine display checks remain manual.

| Role | Username | Password |
|---|---|---|
| Customer | customer | customer123 |
| Administrator | admin | admin123 |

These are public demonstration credentials. Password hashes, rather than plaintext passwords, are stored in SQLite.

## What works

- Role-based login and service-level permission checks.
- Tech collections: PC Sets, PC Parts, Laptops, Phones & Tablets, Peripherals & Accessories, and Other Electronics.
- Old clothing/home items stay inactive in inventory for replacement; product content and historical receipts are preserved.
- Guest storefront, product details, debounced search suggestions, labeled category/budget/custom-price filters, and name/price sorting.
- Guest/customer saved items, persistent customer profiles, and a saved delivery address book.
- Admin main-photo and gallery uploads (up to five photos), preview, replacement/removal, persistent descriptions and optional specifications.
- Admin create/edit, stock adjustments, and product deactivation.
- Per-item cart controls, live cart counts, removal with Undo, and responsive shared order summaries.
- Cash on delivery and simulated wallet approval/decline.
- Transactional checkout: order and stock changes commit together.
- Customer-specific order cards, delivery timelines, manual tracking, cancellation and reviewed Buy again with current stock/prices.
- Admin order processing, independent simulated payment collection, category-specific specifications/configurations, and receipt-preserving product deletion.
- Persistent customer carts with guest merging and price/stock revalidation on restoration.
- SQLite persistence at `data/shop.db`, created on first launch.

Prices use **USD** as a demonstration choice. Shipping is free; taxes are omitted. No real payment is processed. Guest carts last for the current session. Customer carts persist across sign-out and restart; guest selections merge on sign-in. Restored carts report price/availability adjustments and do not reserve stock.

## Verify

```powershell
.\build.ps1 Test
.\build.ps1 Smoke
.\build.ps1 Package
```

Test runs the JUnit service/integration suite with fresh temporary databases. Smoke runs the real JavaFX controls against a separate database and regenerates 26 scene snapshots. Package runs tests and produces a normal Maven JAR in target; runtime dependencies are still required.

Cross-platform Maven alternatives: `mvn test`, `mvn package`, `mvn javafx:run`. The PowerShell smoke helper is Windows-specific.

## Read the code in this order

1. `model/Product.java` — constructor validation and private immutable fields.
2. `model/User.java`, `Customer.java`, `Admin.java`, `Guest.java` — inheritance and permissions.
3. `model/Cart.java` — controlled mutation and protected collections.
4. `payment/PaymentMethod.java` and both implementations — abstraction and polymorphism.
5. `service/CheckoutService.java` — the complete business transaction.
6. `persistence/ProductRepository.java` and `OrderRepository.java` — SQL only.
7. `ui/CheckoutView.java` — button calls service; GUI does not calculate the order.
8. `ShopApp.java` — application entry point and dependency wiring.

All Java paths above are under `src/main/java/edu/course/shop`.

## Deliverables

- [Order processing, structured specifications, saved carts and product deletion](docs/SHOPPING-WORKFLOWS.md)

- [Approved next UI implementation and verification](docs/NEXT-UI.md)

- [Nike-inspired design elevation](docs/DESIGN-ELEVATION.md)

- [Storefront and admin photo guide](docs/STOREFRONT-UPDATE.md)
- [UI refresh and reference research](docs/UX-REFRESH.md)
- [Ten-section project guide](docs/PROJECT-GUIDE.md)
- [Class diagram source](docs/classes.puml)
- [Testing evidence](docs/TESTING.md)
- [Defense questions](docs/DEFENSE.md)
- [Screenshots](docs/screenshots/)

Keep the UML and guide updated when you change class signatures or business rules. The documentation is a working draft to adapt to your instructor's exact required section names.

## Boundaries

Single running application, small local catalog, trusted desktop environment. No registration, network server, real payment integration, real refunds, discounts, multi-currency, or live carrier tracking. Local fulfillment states and simulated cancellation/refund records are supported. SQLite is local persistence, not a remote security boundary. Users who can alter the source or database can alter this demo.

Product/category designs deliberately avoid an artificial product inheritance tree: categories are an enum, while the user hierarchy has real permission behavior. Layouts are written in Java to ease the transition from Swing; FXML is optional and not required for separation of responsibilities.

## References

- [OpenJFX setup and Maven guide](https://openjfx.io/openjfx-docs/)
- [SQLite JDBC](https://github.com/xerial/sqlite-jdbc)
- [SQLite transactions](https://www.sqlite.org/lang_transaction.html)
