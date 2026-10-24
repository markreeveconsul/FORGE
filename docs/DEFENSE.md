# FORGE defense preparation

Use the actual source while rehearsing. Code paths below are relative to src/main/java/edu/course/shop. The report uses the assignment's ten required sections; this page supports the personal defense and does not certify understanding.

## Explain the four pillars

1. **Encapsulation:** Product uses private final fields, validation and defensive photo copies. Cart keeps its map private and returns a list copy. Explain why getters do not require unrestricted setters.
2. **Inheritance:** Customer, Admin and Guest extend abstract User. They share identity getters but supply different permissions. Private parent fields are not directly accessible to subclasses.
3. **Polymorphism:** CheckoutService calls payment.pay(total) through PaymentMethod. COD accepts with PENDING; an approved wallet returns PAID; a declined wallet rejects. There is no payment-class switch in checkout.
4. **Abstraction:** User requires role behavior and cannot be instantiated directly. PaymentMethod exposes the payment contract without requiring checkout to know the concrete simulation.

Open model/User.java and its subclasses, model/Product.java, model/Cart.java, payment/PaymentMethod.java, both implementations, and service/CheckoutService.java. Demonstrate the examples rather than only reciting definitions.

## Trace a purchase

CheckoutView collects fields -> CheckoutService.checkout verifies Customer/cart ownership -> one JDBC connection starts a transaction -> ProductRepository.find reloads products -> conditional deductStock prevents overselling -> PaymentMethod.pay supplies behavior -> OrderRepository.save writes snapshots -> saved cart rows are cleared -> commit -> Cart.clearAfterCheckout.

A failure rolls back the writes and leaves the cart available. OrderItems preserve the purchased name and price even after a catalog edit or deletion. A real payment gateway would require recovery for external side effects; SQLite rollback cannot undo a charge at another service.

## Trace AI Manager

AiManagerView receives a command -> AiManagerService.prepare enforces administrator access -> ManagerAi.classify requests one typed function call -> ManagerCommand validates arguments -> local services select records -> optional ManagerAi.draft returns content -> the administrator reviews/selects/edits -> AiManagerService.apply delegates reviewed changes to ProductService.applyManagerChanges.

The model never runs SQL. Preview does not save. Missing photos require upload; existing nonempty text is preserved. Source URLs and an exact-model flag are required for proposed researched specifications, and a human still checks the variant. A stale product invalidates the complete batch. Audit rows record before and after values; a batch cannot be applied twice.

## Explain the customer assistant and image search

CatalogAssistant performs local retrieval and supports catalog/category/budget context. AssistantPanel renders records as product cards even without specifications. Broader explanations use GeminiAssistant after approval. Missing facts cannot establish compatibility. Do not claim that all customer intent handling is Gemini function calling; the customer retrieval path contains local parsing, whereas AI Manager explicitly uses declared functions.

Image search sends a resized reference and up to 40 active catalog photos with public facts to the configured Gemini model. Returned IDs must belong to the catalog and meet the implemented threshold. This is constrained visual matching, not an image index of the internet. Privacy identifies the external provider even though the interface feature is branded FORGE AI.

Review-summary and image approvals are separate from general chat approval. Consent and preferences are local to the OS account and application user identifier. Do not describe that storage as secure cloud account synchronization.

## Explain sales and limitations

RevenueAnalytics aggregates integer cents and zero-fills the selected local dates. PAID records define paid revenue; order value includes other recorded orders. The chart groups by order creation date, not settlement time. Low stock is a threshold alert, not a predictive model. Payments, refunds and fulfillment are simulated or manually recorded, with no live carrier or bank connection.

AI limits are shared by the provider project/model. One content command can use separate planning and drafting requests. A good network connection cannot prevent a 429 or 503. The app retries only bounded transient failures and uses structured quota metadata; it never invents remaining daily counts. Mock tests validate the application behavior but not current provider uptime or answer accuracy.

## Demonstrate failures and a small code change

- Enter invalid price, quantity or stock and identify both UI feedback and model/service validation.
- Attempt a product update using a Customer at the service layer and explain the permission rejection.
- Decline the simulated wallet; verify stock, order rows and cart remain consistent.
- Prepare an AI change, edit the product elsewhere, then explain why a stale batch must roll back.
- Explain a local fallback alongside an online error without describing the device as offline.
- Practice changing the Product name-length limit, its message and its test together, then run build.ps1 Test and update UML if an API signature changed.

## Rehearsal record

Automated evidence on 24 October 2026: 175 JUnit tests and 121 native UI assertions passed. Instructor title approval, personal code understanding, presentation-machine checks and the actual defense are still human responsibilities. Each member should trace a workflow and answer follow-up questions without relying on memorized claims.
