# FORGE UML class diagram

The diagram set documents the source reviewed on 24 October 2026. There are **104 main Java source files**, **104 top-level types**, and **61 named nested types**, for **165 named types**. All top-level types appear across the first 14 sheets. The fifteenth sheet explains cross-layer dependencies. This is a class diagram set, not a database ER diagram.

- [Printable UML PDF](../submission/FORGE-UML.pdf)
- [Combined readable PlantUML source](../classes.puml)
- [Full declared-member reference](CLASS-REFERENCE.md)
- [Machine-readable source inventory and hashes](source-inventory.json)
- [Every named type with complete declared members in PlantUML](complete-members.puml)

## Readable sheets

| Sheet | Topic |
|---|---|
| [01](01_roles_payments.svg) | User inheritance and payment interface realization |
| [02](02_products_carts.svg) | Product, Cart, specifications and value objects |
| [03](03_orders_reviews.svg) | Order snapshots, fulfillment events and reviews |
| [04](04_checkout_services.svg) | Authentication, carts and checkout services |
| [05](05_inventory_services.svg) | Inventory, accounts, reviews and admin services |
| [06](06_customer_ai.svg) | Local customer retrieval and optional AI generation |
| [07](07_ai_manager.svg) | Validated commands, reviewed drafts and sales |
| [08](08_ai_transport.svg) | Transport, quota metadata, errors, caching and diagnostics |
| [09](09_persistence.svg) | SQLite repositories and database initialization |
| [10](10_application.svg) | Entry point, all six Services components and exceptions |
| [11](11_customer_screens.svg) | Customer screen navigation |
| [12](12_admin_screens.svg) | One active admin module and revenue presentation |
| [13](13_customer_components.svg) | Customer assistant, robot and shopping components |
| [14](14_shared_components.svg) | State, editors, overlays and motion helpers |
| [15](15_cross_layer.svg) | Dependencies between application layers |

## Notation and scope

`+` public, `#` protected, `-` private, `~` package access. Generalization represents extends; dashed realization represents implements. A filled diamond represents owned collections; ordinary arrows represent stored references; dependency arrows represent construction or calls. Hidden links in the source arrange boxes only and are not relationships.

The readable sheets show selected declared operations, omit repeated overloads and abbreviate long parameter lists. Their captions disclose that selection. CLASS-REFERENCE.md and complete-members.puml preserve every explicitly declared field, constructor and method, including package/private members and the 61 named nested types. Records have private final component storage and public generated component accessors. The inventory omits anonymous classes, method bodies, initializers and compiler-generated methods such as record equals or enum values.

User inheritance, payment realization, Cart/CartItem and Order/OrderItem ownership are explicit. Customer ownership of a persisted Order is represented by customerId in code, not a fabricated Customer object field. Generic library types and JavaFX base classes appear in the full reference rather than overwhelming the presentation sheets.

## Regenerate after code changes

Documentation tools are under tools/documentation. They do not run the application, open its database, import the saved key or call the AI provider. Java's compiler parser reads the main Java sources; Gson serializes the inventory. Python then produces the readable and complete member sources. The source hashes detect a stale inventory.

From the project root, with JAVA_HOME pointing to a working JDK:

```powershell
$forgeJava = Join-Path $env:JAVA_HOME 'bin/java.exe'
$forgeGson = Join-Path $PWD '.tools/m2/com/google/code/gson/gson/2.13.2/gson-2.13.2.jar'
& $forgeJava -cp $forgeGson tools/documentation/SourceInventory.java `
  $PWD docs/uml/source-inventory.json
python tools/documentation/generate_uml.py
```

If a top-level type was added or removed, update the generator's sheet assignments; it intentionally fails until every type has exactly one home sheet. Inspect cross-layer.puml manually when wiring or calls change. The inventory generator excludes field initializer values, so configuration secrets cannot enter it through initializers.

Export SVG and PNG with a local PlantUML JAR. The submitted exports used PlantUML 1.2026.9 with the embedded Smetana layout. A renderer JAR is a documentation tool, not a FORGE runtime dependency, and is not committed with this package.

```powershell
& $forgeJava -jar .tools/plantuml.jar -charset UTF-8 -tsvg `
  'docs/uml/0*.puml' 'docs/uml/1*.puml'
& $forgeJava -jar .tools/plantuml.jar -charset UTF-8 -tpng `
  'docs/uml/0*.puml' 'docs/uml/1*.puml'
& $forgeJava -jar .tools/plantuml.jar -checkonly docs/uml/complete-members.puml
python tools/documentation/check_documentation.py
```

Supply your own local renderer at .tools/plantuml.jar, downloaded from the PlantUML project or Maven Central. Source inventory is local; do not paste source or keys into an online renderer.

To rebuild the PDF artifacts, use Python on Windows with Arial installed, install tools/documentation/requirements.txt into your chosen documentation environment and run `python tools/documentation/build_report.py`. This reads PROJECT-GUIDE.md, the SVG sheets and curated screenshot/evidence manifests. It does not rerun tests; refresh verification separately after code changes. Visually review the resulting PDFs after every layout change. No Python or PlantUML dependency is added to pom.xml or required to run FORGE.
