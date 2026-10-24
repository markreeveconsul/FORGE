# FORGE Java class reference

Generated from Java compiler syntax trees. The source inventory records the exact file coverage and named types. It omits method bodies, field initializers and anonymous classes. Source links and source SHA-256 values are retained in source-inventory.json.

`+` public, `#` protected, `-` private, `~` package access. Record components have private final storage and implicit public component accessors. Compiler-generated record equals/hashCode/toString and enum values/valueOf are not listed as explicitly declared methods. The readable UML sheets select operations; this reference preserves every declared member.

## edu.course.shop.Services

Source: `src/main/java/edu/course/shop/Services.java:4`. Kind: record. Modifiers: public.

```plantuml
class "Services" as edu_course_shop_Services <<record>> {
  -auth : AuthService
  -products : ProductService
  -checkout : CheckoutService
  -orders : OrderService
  -admin : AdminService
  -reviews : ProductReviewService
  +accounts() : AccountService
}
```

## edu.course.shop.ShopApp

Source: `src/main/java/edu/course/shop/ShopApp.java:10`. Kind: class. Modifiers: public final.

```plantuml
class "ShopApp" as edu_course_shop_ShopApp {
  +start(stage : Stage) : void
  {static} +main(args : String[]) : void
}
```

Extends: `Application`. Implements: `(none)`.

## edu.course.shop.exception.OutOfStockException

Source: `src/main/java/edu/course/shop/exception/OutOfStockException.java:2`. Kind: class. Modifiers: public final.

```plantuml
class "OutOfStockException" as edu_course_shop_exception_OutOfStockException {
  +OutOfStockException(name : String)
}
```

Extends: `ValidationException`. Implements: `(none)`.

## edu.course.shop.exception.StorageException

Source: `src/main/java/edu/course/shop/exception/StorageException.java:2`. Kind: class. Modifiers: public final.

```plantuml
class "StorageException" as edu_course_shop_exception_StorageException {
  +StorageException(cause : Throwable)
}
```

Extends: `RuntimeException`. Implements: `(none)`.

## edu.course.shop.exception.ValidationException

Source: `src/main/java/edu/course/shop/exception/ValidationException.java:2`. Kind: class. Modifiers: public.

```plantuml
class "ValidationException" as edu_course_shop_exception_ValidationException {
  +ValidationException(message : String)
}
```

Extends: `RuntimeException`. Implements: `(none)`.

## edu.course.shop.model.Admin

Source: `src/main/java/edu/course/shop/model/Admin.java:2`. Kind: class. Modifiers: public final.

```plantuml
class "Admin" as edu_course_shop_model_Admin {
  +Admin(id : long, username : String)
  +canManageProducts() : boolean
  +canShop() : boolean
  +getRole() : String
}
```

Extends: `User`. Implements: `(none)`.

## edu.course.shop.model.Cart

Source: `src/main/java/edu/course/shop/model/Cart.java:9`. Kind: class. Modifiers: public final.

```plantuml
class "Cart" as edu_course_shop_model_Cart {
  -customerId : long
  -items : Map<Long, CartItem>
  -persistence : Consumer<List<CartItem>>
  +Cart(customerId : long)
  +getCustomerId() : long
  +getItems() : List<CartItem>
  +attachPersistence(save : Consumer<List<CartItem>>) : void
  +add(product : Product, quantity : int) : void
  +setQuantity(product : Product, quantity : int) : void
  +remove(id : long) : void
  +clear() : void
  +replaceWith(replacement : Cart) : void
  +clearAfterCheckout() : void
  -commit(next : Map<Long, CartItem>) : void
  +getTotal() : BigDecimal
}
```

## edu.course.shop.model.CartItem

Source: `src/main/java/edu/course/shop/model/CartItem.java:4`. Kind: class. Modifiers: public final.

```plantuml
class "CartItem" as edu_course_shop_model_CartItem {
  -product : Product
  -quantity : int
  +CartItem(product : Product, quantity : int)
  +getProduct() : Product
  +getQuantity() : int
  +getSubtotal() : BigDecimal
}
```

## edu.course.shop.model.Category

Source: `src/main/java/edu/course/shop/model/Category.java:6`. Kind: enum. Modifiers: public.

```plantuml
enum "Category" as edu_course_shop_model_Category {
  PC_SETS
  PC_PARTS
  LAPTOPS
  PHONES_TABLETS
  PERIPHERALS
  ELECTRONICS
  CLOTHING
  HOME
  -label : String
  ~Category(label : String)
  +label() : String
  +isRetired() : boolean
  {static} +shopping() : List<Category>
  +shoppingIndex() : int
}
```

## edu.course.shop.model.Customer

Source: `src/main/java/edu/course/shop/model/Customer.java:2`. Kind: class. Modifiers: public final.

```plantuml
class "Customer" as edu_course_shop_model_Customer {
  +Customer(id : long, username : String)
  +canManageProducts() : boolean
  +canShop() : boolean
  +getRole() : String
}
```

Extends: `User`. Implements: `(none)`.

## edu.course.shop.model.DeliveryAddress

Source: `src/main/java/edu/course/shop/model/DeliveryAddress.java:5`. Kind: record. Modifiers: public.

```plantuml
class "DeliveryAddress" as edu_course_shop_model_DeliveryAddress <<record>> {
  -recipient : String
  -street : String
  -city : String
  -postalCode : String
  +DeliveryAddress(recipient : String, street : String, city : String, postalCode : String)
  +formatted() : String
}
```

## edu.course.shop.model.FulfillmentStatus

Source: `src/main/java/edu/course/shop/model/FulfillmentStatus.java:3`. Kind: enum. Modifiers: public.

```plantuml
enum "FulfillmentStatus" as edu_course_shop_model_FulfillmentStatus {
  CONFIRMED
  PACKED
  SHIPPED
  DELIVERED
  CANCELLED
  -label : String
  ~FulfillmentStatus(label : String)
  +toString() : String
  +next() : List<FulfillmentStatus>
}
```

## edu.course.shop.model.Guest

Source: `src/main/java/edu/course/shop/model/Guest.java:3`. Kind: class. Modifiers: public final.

```plantuml
class "Guest" as edu_course_shop_model_Guest {
  +Guest()
  +canManageProducts() : boolean
  +canShop() : boolean
  +getRole() : String
}
```

Extends: `User`. Implements: `(none)`.

## edu.course.shop.model.Money

Source: `src/main/java/edu/course/shop/model/Money.java:7`. Kind: class. Modifiers: public final.

```plantuml
class "Money" as edu_course_shop_model_Money {
  -Money()
  {static} +price(value : BigDecimal) : BigDecimal
  {static} +parse(text : String) : BigDecimal
  {static} +cents(amount : BigDecimal) : long
  {static} +amount(cents : long) : BigDecimal
  {static} +format(amount : BigDecimal) : String
}
```

## edu.course.shop.model.Order

Source: `src/main/java/edu/course/shop/model/Order.java:4`. Kind: record. Modifiers: public.

```plantuml
class "Order" as edu_course_shop_model_Order <<record>> {
  -id : long
  -customerId : long
  -createdAt : String
  -address : String
  -paymentMethod : String
  -paymentStatus : String
  -total : BigDecimal
  -items : List<OrderItem>
  -fulfillmentStatus : FulfillmentStatus
  -carrier : String
  -trackingNumber : String
  -events : List<OrderEvent>
  +Order(id : long, customerId : long, createdAt : String, address : String, paymentMethod : String, paymentStatus : String, total : BigDecimal, items : List<OrderItem>, fulfillmentStatus : FulfillmentStatus, carrier : String, trackingNumber : String, events : List<OrderEvent>)
  +Order(id : long, customerId : long, createdAt : String, address : String, paymentMethod : String, paymentStatus : String, total : BigDecimal, items : List<OrderItem>)
}
```

## edu.course.shop.model.OrderEvent

Source: `src/main/java/edu/course/shop/model/OrderEvent.java:2`. Kind: record. Modifiers: public.

```plantuml
class "OrderEvent" as edu_course_shop_model_OrderEvent <<record>> {
  -status : FulfillmentStatus
  -createdAt : String
  -note : String
}
```

## edu.course.shop.model.OrderItem

Source: `src/main/java/edu/course/shop/model/OrderItem.java:4`. Kind: record. Modifiers: public.

```plantuml
class "OrderItem" as edu_course_shop_model_OrderItem <<record>> {
  -productId : long
  -productName : String
  -unitPrice : BigDecimal
  -quantity : int
  +subtotal() : BigDecimal
}
```

## edu.course.shop.model.PhotoFrame

Source: `src/main/java/edu/course/shop/model/PhotoFrame.java:4`. Kind: record. Modifiers: public.

```plantuml
class "PhotoFrame" as edu_course_shop_model_PhotoFrame <<record>> {
  -mode : Mode
  -x : double
  -y : double
  AUTO
  +PhotoFrame(mode : Mode, x : double, y : double)
}
```

## edu.course.shop.model.PhotoFrame.Mode

Source: `src/main/java/edu/course/shop/model/PhotoFrame.java:5`. Kind: enum. Modifiers: public.

```plantuml
enum "PhotoFrame.Mode" as edu_course_shop_model_PhotoFrame_Mode {
  AUTO
  CONTAIN
  COVER
}
```

## edu.course.shop.model.Product

Source: `src/main/java/edu/course/shop/model/Product.java:6`. Kind: class. Modifiers: public final.

```plantuml
class "Product" as edu_course_shop_model_Product {
  -id : long
  -name : String
  -category : Category
  -price : BigDecimal
  -stock : int
  -active : boolean
  -description : String
  -photo : byte[]
  -additionalPhotos : java.util.List<byte[]>
  -specifications : String
  -photoFrame : PhotoFrame
  +getPhotoFrame() : PhotoFrame
  +Product(id : long, name : String, category : Category, price : BigDecimal, stock : int, active : boolean)
  +Product(id : long, name : String, category : Category, price : BigDecimal, stock : int, active : boolean, description : String, photo : byte[])
  +Product(id : long, name : String, category : Category, price : BigDecimal, stock : int, active : boolean, description : String, photo : byte[], additionalPhotos : java.util.List<byte[]>, specifications : String)
  +Product(id : long, name : String, category : Category, price : BigDecimal, stock : int, active : boolean, description : String, photo : byte[], additionalPhotos : java.util.List<byte[]>, specifications : String, frame : PhotoFrame)
  +getDescription() : String
  +getPhoto() : byte[]
  +getAdditionalPhotos() : java.util.List<byte[]>
  +getSpecifications() : String
  +getId() : long
  +getName() : String
  +getCategory() : Category
  +getPrice() : BigDecimal
  +getStock() : int
  +isActive() : boolean
}
```

## edu.course.shop.model.ProductReview

Source: `src/main/java/edu/course/shop/model/ProductReview.java:4`. Kind: record. Modifiers: public.

```plantuml
class "ProductReview" as edu_course_shop_model_ProductReview <<record>> {
  -id : long
  -productId : long
  -author : String
  -rating : int
  -text : String
  -createdAt : String
}
```

## edu.course.shop.model.ProductSpecifications

Source: `src/main/java/edu/course/shop/model/ProductSpecifications.java:8`. Kind: class. Modifiers: public final.

```plantuml
class "ProductSpecifications" as edu_course_shop_model_ProductSpecifications {
  {static} -ALIASES : Map<String, String>
  -ProductSpecifications()
  {static} +fields(category : Category) : List<String>
  {static} +canonical(key : String) : String
  {static} +values(text : String) : Map<String, String>
  {static} +value(p : Product, key : String) : String
  {static} +matches(p : Product, filters : Map<String, String>) : boolean
  {static} +validated(text : String) : String
  {static} +listingText(text : String) : String
  {static} +formatWarnings(text : String) : List<FormatWarning>
  {static} -normalize(text : String, strict : boolean) : String
  {static} -quantity(key : String, value : String, regex : String, help : String, length : boolean) : String
}
```

## edu.course.shop.model.ProductSpecifications.FormatWarning

Source: `src/main/java/edu/course/shop/model/ProductSpecifications.java:44`. Kind: record. Modifiers: public.

```plantuml
class "ProductSpecifications.FormatWarning" as edu_course_shop_model_ProductSpecifications_FormatWarning <<record>> {
  -key : String
  -message : String
}
```

## edu.course.shop.model.User

Source: `src/main/java/edu/course/shop/model/User.java:3`. Kind: class. Modifiers: public abstract.

```plantuml
abstract class "User" as edu_course_shop_model_User {
  -id : long
  -username : String
  #User(id : long, username : String)
  +getId() : long
  +getUsername() : String
  {abstract} +canManageProducts() : boolean
  {abstract} +canShop() : boolean
  {abstract} +getRole() : String
}
```

## edu.course.shop.payment.CashOnDeliveryPayment

Source: `src/main/java/edu/course/shop/payment/CashOnDeliveryPayment.java:3`. Kind: class. Modifiers: public final.

```plantuml
class "CashOnDeliveryPayment" as edu_course_shop_payment_CashOnDeliveryPayment {
  +getName() : String
  +pay(amount : BigDecimal) : PaymentResult
  +toString() : String
}
```

Extends: `(none)`. Implements: `PaymentMethod`.

## edu.course.shop.payment.PaymentMethod

Source: `src/main/java/edu/course/shop/payment/PaymentMethod.java:4`. Kind: interface. Modifiers: public.

```plantuml
interface "PaymentMethod" as edu_course_shop_payment_PaymentMethod {
  +getName() : String
  +pay(amount : BigDecimal) : PaymentResult
}
```

## edu.course.shop.payment.PaymentResult

Source: `src/main/java/edu/course/shop/payment/PaymentResult.java:2`. Kind: record. Modifiers: public.

```plantuml
class "PaymentResult" as edu_course_shop_payment_PaymentResult <<record>> {
  -accepted : boolean
  -status : String
}
```

## edu.course.shop.payment.SimulatedWalletPayment

Source: `src/main/java/edu/course/shop/payment/SimulatedWalletPayment.java:3`. Kind: class. Modifiers: public final.

```plantuml
class "SimulatedWalletPayment" as edu_course_shop_payment_SimulatedWalletPayment {
  -approved : boolean
  +SimulatedWalletPayment(approved : boolean)
  +getName() : String
  +pay(amount : BigDecimal) : PaymentResult
  +toString() : String
}
```

Extends: `(none)`. Implements: `PaymentMethod`.

## edu.course.shop.persistence.AdminRepository

Source: `src/main/java/edu/course/shop/persistence/AdminRepository.java:11`. Kind: class. Modifiers: public final.

```plantuml
class "AdminRepository" as edu_course_shop_persistence_AdminRepository {
  +summary(c : Connection) : Summary
  +lowStock(c : Connection) : List<LowStockRow>
  +orders(c : Connection) : List<OrderRow>
  +customers(c : Connection) : List<CustomerRow>
}
```

## edu.course.shop.persistence.AdminRepository.CustomerRow

Source: `src/main/java/edu/course/shop/persistence/AdminRepository.java:17`. Kind: record. Modifiers: public.

```plantuml
class "AdminRepository.CustomerRow" as edu_course_shop_persistence_AdminRepository_CustomerRow <<record>> {
  -id : long
  -username : String
  -displayName : String
  -orderCount : int
  -orderCents : long
}
```

## edu.course.shop.persistence.AdminRepository.LowStockRow

Source: `src/main/java/edu/course/shop/persistence/AdminRepository.java:18`. Kind: record. Modifiers: public.

```plantuml
class "AdminRepository.LowStockRow" as edu_course_shop_persistence_AdminRepository_LowStockRow <<record>> {
  -id : long
  -name : String
  -category : String
  -stock : int
}
```

## edu.course.shop.persistence.AdminRepository.OrderRow

Source: `src/main/java/edu/course/shop/persistence/AdminRepository.java:13`. Kind: record. Modifiers: public.

```plantuml
class "AdminRepository.OrderRow" as edu_course_shop_persistence_AdminRepository_OrderRow <<record>> {
  -id : long
  -customer : String
  -createdAt : String
  -paymentMethod : String
  -paymentStatus : String
  -itemCount : int
  -totalCents : long
  -fulfillmentStatus : String
  +OrderRow(id : long, customer : String, createdAt : String, method : String, payment : String, itemCount : int, total : long)
}
```

## edu.course.shop.persistence.AdminRepository.Summary

Source: `src/main/java/edu/course/shop/persistence/AdminRepository.java:12`. Kind: record. Modifiers: public.

```plantuml
class "AdminRepository.Summary" as edu_course_shop_persistence_AdminRepository_Summary <<record>> {
  -activeProducts : int
  -lowStockProducts : int
  -orders : int
  -customers : int
  -recordedOrderCents : long
}
```

## edu.course.shop.persistence.DatabaseManager

Source: `src/main/java/edu/course/shop/persistence/DatabaseManager.java:8`. Kind: class. Modifiers: public final.

```plantuml
class "DatabaseManager" as edu_course_shop_persistence_DatabaseManager {
  -url : String
  +DatabaseManager(file : Path)
  +open() : Connection
  +initialize() : void
  -seedUser(c : Connection, username : String, password : String, role : String) : void
}
```

## edu.course.shop.persistence.OrderRepository

Source: `src/main/java/edu/course/shop/persistence/OrderRepository.java:6`. Kind: class. Modifiers: public final.

```plantuml
class "OrderRepository" as edu_course_shop_persistence_OrderRepository {
  +save(c : Connection, order : Order) : Order
  +forCustomer(c : Connection, customerId : long) : List<Order>
  +find(c : Connection, id : long) : Order
  -read(c : Connection, r : ResultSet) : Order
  +event(c : Connection, id : long, status : FulfillmentStatus, time : String, note : String, actor : long) : void
  -items(c : Connection, id : long) : List<OrderItem>
}
```

## edu.course.shop.persistence.ProductRepository

Source: `src/main/java/edu/course/shop/persistence/ProductRepository.java:7`. Kind: class. Modifiers: public final.

```plantuml
class "ProductRepository" as edu_course_shop_persistence_ProductRepository {
  +list(c : Connection, includeInactive : boolean) : List<Product>
  +find(c : Connection, id : long) : Product
  +save(c : Connection, product : Product) : void
  +deductStock(c : Connection, product : Product, quantity : int) : void
  -read(c : Connection, r : ResultSet) : Product
}
```

## edu.course.shop.persistence.ProductReviewRepository

Source: `src/main/java/edu/course/shop/persistence/ProductReviewRepository.java:12`. Kind: class. Modifiers: public final.

```plantuml
class "ProductReviewRepository" as edu_course_shop_persistence_ProductReviewRepository {
  +hasPurchased(c : Connection, customerId : long, productId : long) : boolean
  +list(c : Connection, productId : long) : List<ProductReview>
  +save(c : Connection, customerId : long, productId : long, rating : int, text : String) : void
}
```

## edu.course.shop.persistence.UserRepository

Source: `src/main/java/edu/course/shop/persistence/UserRepository.java:6`. Kind: class. Modifiers: public final.

```plantuml
class "UserRepository" as edu_course_shop_persistence_UserRepository {
  +find(c : Connection, username : String) : Optional<Credentials>
}
```

## edu.course.shop.persistence.UserRepository.Credentials

Source: `src/main/java/edu/course/shop/persistence/UserRepository.java:7`. Kind: record. Modifiers: public.

```plantuml
class "UserRepository.Credentials" as edu_course_shop_persistence_UserRepository_Credentials <<record>> {
  -user : User
  -passwordHash : String
}
```

## edu.course.shop.service.AccountService

Source: `src/main/java/edu/course/shop/service/AccountService.java:10`. Kind: class. Modifiers: public final.

```plantuml
class "AccountService" as edu_course_shop_service_AccountService {
  -database : DatabaseManager
  +AccountService(database : DatabaseManager)
  -customer(c : Connection, user : User) : void
  +saved(user : User) : Set<Long>
  +saveItem(user : User, productId : long, saved : boolean) : void
  +displayName(user : User) : String
  +saveName(user : User, name : String) : void
  +addresses(user : User) : List<SavedAddress>
  +saveAddress(user : User, id : long, address : DeliveryAddress, preferred : boolean) : void
  +removeAddress(user : User, id : long) : void
}
```

## edu.course.shop.service.AccountService.SavedAddress

Source: `src/main/java/edu/course/shop/service/AccountService.java:11`. Kind: record. Modifiers: public.

```plantuml
class "AccountService.SavedAddress" as edu_course_shop_service_AccountService_SavedAddress <<record>> {
  -id : long
  -address : DeliveryAddress
  -preferred : boolean
  +toString() : String
}
```

## edu.course.shop.service.AdminService

Source: `src/main/java/edu/course/shop/service/AdminService.java:13`. Kind: class. Modifiers: public final.

```plantuml
class "AdminService" as edu_course_shop_service_AdminService {
  -database : DatabaseManager
  -reports : AdminRepository
  +AdminService(database : DatabaseManager, reports : AdminRepository)
  -requireAdmin(actor : User) : void
  +orderProcessing() : OrderService
  +summary(actor : User) : AdminRepository.Summary
  +lowStock(actor : User) : List<AdminRepository.LowStockRow>
  +orders(actor : User) : List<AdminRepository.OrderRow>
  +customers(actor : User) : List<AdminRepository.CustomerRow>
}
```

## edu.course.shop.service.AiCache

Source: `src/main/java/edu/course/shop/service/AiCache.java:7`. Kind: class. Modifiers: final.

```plantuml
class "AiCache" as edu_course_shop_service_AiCache {
  -entries : LinkedHashMap<String, Entry<T>>
  -maxEntries : int
  -maxBytes : int
  -ttl : long
  -clock : LongSupplier
  -bytes : int
  ~AiCache(count : int, bytes : int, ttl : long, clock : LongSupplier)
  ~get(key : String) : T
  ~put(key : String, value : T, size : int) : void
  ~clear() : void
  {static} ~digest(text : String) : String
  {static} ~digest(bytes : byte[]) : String
}
```

## edu.course.shop.service.AiCache.Entry

Source: `src/main/java/edu/course/shop/service/AiCache.java:8`. Kind: record. Modifiers: private.

```plantuml
class "AiCache.Entry" as edu_course_shop_service_AiCache_Entry <<record>> {
  -value : T
  -expires : long
  -bytes : int
}
```

## edu.course.shop.service.AiDiagnostics

Source: `src/main/java/edu/course/shop/service/AiDiagnostics.java:6`. Kind: class. Modifiers: public final.

```plantuml
class "AiDiagnostics" as edu_course_shop_service_AiDiagnostics {
  {static} -recent : Deque<Event>
  {static} -file : Path
  -AiDiagnostics()
  {static} -quotaFields(quota : AiQuota.Info) : String
  {static} ~record(e : Event) : void
  {static} +recent() : List<Event>
  {static} -safe(s : String) : String
}
```

## edu.course.shop.service.AiDiagnostics.Event

Source: `src/main/java/edu/course/shop/service/AiDiagnostics.java:7`. Kind: record. Modifiers: public.

```plantuml
class "AiDiagnostics.Event" as edu_course_shop_service_AiDiagnostics_Event <<record>> {
  -time : Instant
  -feature : String
  -model : String
  -outcome : String
  -attempt : int
  -httpStatus : int
  -durationMs : long
  -requestBytes : long
  -quota : AiQuota.Info
  +Event(time : Instant, feature : String, model : String, outcome : String, attempt : int, httpStatus : int, durationMs : long, requestBytes : long)
  +line() : String
}
```

## edu.course.shop.service.AiFailure

Source: `src/main/java/edu/course/shop/service/AiFailure.java:3`. Kind: class. Modifiers: public final.

```plantuml
class "AiFailure" as edu_course_shop_service_AiFailure {
  -kind : Kind
  -status : int
  -retryAfterSeconds : long
  -quota : AiQuota.Info
  ~AiFailure(kind : Kind, status : int, delay : long, message : String)
  ~AiFailure(kind : Kind, status : int, delay : long, message : String, quota : AiQuota.Info)
  +kind() : Kind
  +status() : int
  +retryAfterSeconds() : long
  +quota() : AiQuota.Info
}
```

Extends: `IllegalStateException`. Implements: `(none)`.

## edu.course.shop.service.AiFailure.Kind

Source: `src/main/java/edu/course/shop/service/AiFailure.java:4`. Kind: enum. Modifiers: public.

```plantuml
enum "AiFailure.Kind" as edu_course_shop_service_AiFailure_Kind {
  TIMEOUT
  CONNECTION
  SERVICE
  RATE_LIMIT
  DAILY_QUOTA
  ACCESS
  MODEL
  REQUEST
  RESPONSE
  COOLDOWN
}
```

## edu.course.shop.service.AiHttp

Source: `src/main/java/edu/course/shop/service/AiHttp.java:14`. Kind: class. Modifiers: final.

```plantuml
class "AiHttp" as edu_course_shop_service_AiHttp {
  -sender : GeminiAssistant.Sender
  -key : String
  -model : String
  -sleeper : Sleeper
  -clock : LongSupplier
  -events : Consumer<AiDiagnostics.Event>
  -nextAllowed : long
  -lastQuota : QuotaReport
  ~AiHttp(sender : GeminiAssistant.Sender, key : String, model : String, sleeper : Sleeper, clock : LongSupplier, events : Consumer<AiDiagnostics.Event>)
  ~send(request : HttpRequest, feature : String) : GeminiAssistant.Reply
  ~invalidResponse(feature : String) : void
  ~cacheHit(feature : String) : void
  -event(feature : String, outcome : String, attempt : int, status : int, began : long, bytes : long) : void
  -event(feature : String, outcome : String, attempt : int, status : int, began : long, bytes : long, quota : AiQuota.Info) : void
  ~limitsSummary() : String
  {static} -timeout() : AiFailure
  {static} -ioFailure(e : IOException) : AiFailure
  {static} ~error(reply : GeminiAssistant.Reply, key : String) : AiFailure
}
```

## edu.course.shop.service.AiHttp.QuotaReport

Source: `src/main/java/edu/course/shop/service/AiHttp.java:18`. Kind: record. Modifiers: private.

```plantuml
class "AiHttp.QuotaReport" as edu_course_shop_service_AiHttp_QuotaReport <<record>> {
  -at : Instant
  -info : AiQuota.Info
}
```

## edu.course.shop.service.AiHttp.Sleeper

Source: `src/main/java/edu/course/shop/service/AiHttp.java:15`. Kind: interface. Modifiers: package access.

```plantuml
interface "AiHttp.Sleeper" as edu_course_shop_service_AiHttp_Sleeper {
  +sleep(millis : long) : void
}
```

## edu.course.shop.service.AiManagerService

Source: `src/main/java/edu/course/shop/service/AiManagerService.java:12`. Kind: class. Modifiers: public final.

```plantuml
class "AiManagerService" as edu_course_shop_service_AiManagerService {
  -services : Services
  -ai : ManagerAi
  -clock : Clock
  +AiManagerService(services : Services, gemini : GeminiAssistant)
  ~AiManagerService(services : Services, ai : ManagerAi, clock : Clock)
  +configured() : boolean
  {static} -requireAdmin(actor : User) : void
  +metrics(actor : User) : Metrics
  +prepare(actor : User, request : String, research : boolean) : Result
  +prepare(actor : User, request : String, research : boolean, progress : java.util.function.Consumer<String>) : Result
  -result(c : ManagerCommand, p : List<Product>, changes : List<ProductService.ManagerChange>, drafts : List<ManagerAi.Draft>, sales : Sales, offline : boolean, message : String, count : int, aiIssue : String) : Result
  -sales(actor : User, command : ManagerCommand) : Sales
  +apply(actor : User, result : Result) : void
}
```

## edu.course.shop.service.AiManagerService.Metrics

Source: `src/main/java/edu/course/shop/service/AiManagerService.java:13`. Kind: record. Modifiers: public.

```plantuml
class "AiManagerService.Metrics" as edu_course_shop_service_AiManagerService_Metrics <<record>> {
  -products : int
  -needContent : int
  -lowStock : int
  -inactiveWithStock : int
}
```

## edu.course.shop.service.AiManagerService.Result

Source: `src/main/java/edu/course/shop/service/AiManagerService.java:15`. Kind: record. Modifiers: public.

```plantuml
class "AiManagerService.Result" as edu_course_shop_service_AiManagerService_Result <<record>> {
  -batchId : String
  -command : ManagerCommand
  -products : List<Product>
  -changes : List<ProductService.ManagerChange>
  -drafts : List<ManagerAi.Draft>
  -sales : Sales
  -offline : boolean
  -message : String
  -totalMatches : int
  -aiIssue : String
  +Result(batchId : String, command : ManagerCommand, products : List<Product>, changes : List<ProductService.ManagerChange>, drafts : List<ManagerAi.Draft>, sales : Sales, offline : boolean, message : String, totalMatches : int, aiIssue : String)
  +Result(batchId : String, command : ManagerCommand, products : List<Product>, changes : List<ProductService.ManagerChange>, drafts : List<ManagerAi.Draft>, sales : Sales, offline : boolean, message : String, totalMatches : int)
}
```

## edu.course.shop.service.AiManagerService.Sales

Source: `src/main/java/edu/course/shop/service/AiManagerService.java:14`. Kind: record. Modifiers: public.

```plantuml
class "AiManagerService.Sales" as edu_course_shop_service_AiManagerService_Sales <<record>> {
  -period : String
  -orders : int
  -orderValue : BigDecimal
  -paidValue : BigDecimal
  -units : int
}
```

## edu.course.shop.service.AiQuota

Source: `src/main/java/edu/course/shop/service/AiQuota.java:10`. Kind: class. Modifiers: public final.

```plantuml
class "AiQuota" as edu_course_shop_service_AiQuota {
  -AiQuota()
  {static} +guide() : String
  {static} ~read(reply : GeminiAssistant.Reply, key : String) : Info
  {static} -details(body : String) : JsonArray
  {static} -type(object : JsonObject, expected : String) : boolean
  {static} -text(object : JsonObject, field : String) : String
  {static} -safeField(object : JsonObject, field : String, key : String) : String
  {static} -identifier(value : String) : String
  {static} -amount(value : String) : String
  {static} -window(identity : String) : Window
  {static} -retryDelay(reply : GeminiAssistant.Reply, details : JsonArray) : long
  {static} -bounded(value : long) : long
}
```

## edu.course.shop.service.AiQuota.Info

Source: `src/main/java/edu/course/shop/service/AiQuota.java:24`. Kind: record. Modifiers: public.

```plantuml
class "AiQuota.Info" as edu_course_shop_service_AiQuota_Info <<record>> {
  -violations : List<Violation>
  -retryAfterSeconds : long
  EMPTY
  +Info(violations : List<Violation>, retryAfterSeconds : long)
  +daily() : boolean
  +zeroAllowance() : boolean
  +retryable() : boolean
  +message() : String
}
```

## edu.course.shop.service.AiQuota.Resource

Source: `src/main/java/edu/course/shop/service/AiQuota.java:12`. Kind: enum. Modifiers: public.

```plantuml
enum "AiQuota.Resource" as edu_course_shop_service_AiQuota_Resource {
  REQUESTS
  INPUT_TOKENS
  OUTPUT_TOKENS
  TOKENS
  SEARCHES
  IMAGES
  UNKNOWN
}
```

## edu.course.shop.service.AiQuota.Violation

Source: `src/main/java/edu/course/shop/service/AiQuota.java:13`. Kind: record. Modifiers: public.

```plantuml
class "AiQuota.Violation" as edu_course_shop_service_AiQuota_Violation <<record>> {
  -metric : String
  -id : String
  -limit : String
  -model : String
  -window : Window
  -resource : Resource
  +Violation(metric : String, id : String, limit : String, model : String, window : Window, resource : Resource)
  +label() : String
}
```

## edu.course.shop.service.AiQuota.Window

Source: `src/main/java/edu/course/shop/service/AiQuota.java:11`. Kind: enum. Modifiers: public.

```plantuml
enum "AiQuota.Window" as edu_course_shop_service_AiQuota_Window {
  MINUTE
  DAY
  UNKNOWN
}
```

## edu.course.shop.service.AssistantCart

Source: `src/main/java/edu/course/shop/service/AssistantCart.java:11`. Kind: class. Modifiers: public final.

```plantuml
class "AssistantCart" as edu_course_shop_service_AssistantCart {
  -AssistantCart()
  {static} +prepareEdit(question : String, items : List<CartItem>) : Optional<Edit>
  {static} -find(phrase : String, items : List<CartItem>) : Optional<CartItem>
  {static} +applyEdit(user : User, cart : Cart, edit : Edit, products : ProductService) : void
  {static} +validate(approved : List<Product>, products : ProductService) : List<Product>
  {static} +apply(user : User, cart : Cart, validated : List<Product>) : void
}
```

## edu.course.shop.service.AssistantCart.Edit

Source: `src/main/java/edu/course/shop/service/AssistantCart.java:12`. Kind: record. Modifiers: public.

```plantuml
class "AssistantCart.Edit" as edu_course_shop_service_AssistantCart_Edit <<record>> {
  -productId : long
  -productName : String
  -quantity : Integer
  +removes() : boolean
}
```

## edu.course.shop.service.AssistantContext

Source: `src/main/java/edu/course/shop/service/AssistantContext.java:9`. Kind: class. Modifiers: public final.

```plantuml
class "AssistantContext" as edu_course_shop_service_AssistantContext {
  -AssistantContext()
  {static} +product(p : Product) : JsonObject
  {static} +cart(items : List<CartItem>, products : ProductService) : JsonObject
  {static} +order(user : User, order : Order) : JsonObject
}
```

## edu.course.shop.service.AssistantPreferences

Source: `src/main/java/edu/course/shop/service/AssistantPreferences.java:17`. Kind: class. Modifiers: public final.

```plantuml
class "AssistantPreferences" as edu_course_shop_service_AssistantPreferences {
  -file : Path
  +AssistantPreferences()
  ~AssistantPreferences(file : Path)
  +load(userId : long) : Profile
  +hasGeminiConsent(userId : long) : boolean
  +rememberGeminiConsent(userId : long) : void
  +forgetGeminiConsent(userId : long) : void
  +hasGeminiImageConsent(userId : long) : boolean
  +rememberGeminiImageConsent(userId : long) : void
  +forgetGeminiImageConsent(userId : long) : void
  +hasReviewSummaryConsent(userId : long) : boolean
  +rememberReviewSummaryConsent(userId : long) : void
  +parseAndSave(userId : long, request : String) : Profile
  +clear(userId : long) : boolean
  {static} +apply(question : String, profile : Profile) : String
  {static} -category(q : String) : Category
  {static} -mentionsCategory(q : String) : boolean
  {static} -hasBudget(q : String) : boolean
  {static} -parseBudget(q : String) : BigDecimal
  -read() : Properties
  -write(values : Properties) : void
}
```

## edu.course.shop.service.AssistantPreferences.Profile

Source: `src/main/java/edu/course/shop/service/AssistantPreferences.java:18`. Kind: record. Modifiers: public.

```plantuml
class "AssistantPreferences.Profile" as edu_course_shop_service_AssistantPreferences_Profile <<record>> {
  -category : Category
  -budget : BigDecimal
  +empty() : boolean
}
```

## edu.course.shop.service.AuthService

Source: `src/main/java/edu/course/shop/service/AuthService.java:7`. Kind: class. Modifiers: public final.

```plantuml
class "AuthService" as edu_course_shop_service_AuthService {
  -database : DatabaseManager
  -users : UserRepository
  +AuthService(database : DatabaseManager, users : UserRepository)
  +accounts() : AccountService
  +login(username : String, password : String) : User
}
```

## edu.course.shop.service.CartService

Source: `src/main/java/edu/course/shop/service/CartService.java:5`. Kind: class. Modifiers: public final.

```plantuml
class "CartService" as edu_course_shop_service_CartService {
  -products : ProductService
  +CartService(products : ProductService)
  +add(user : User, cart : Cart, productId : long, quantity : int) : void
  +update(user : User, cart : Cart, productId : long, quantity : int) : void
  +transferGuest(guest : Cart, customer : User) : Cart
  +review(cart : Cart) : String
  {static} +checkOwner(user : User, cart : Cart) : void
  +prepareRepeat(user : User, cart : Cart, order : Order) : RepeatPlan
  +applyRepeat(user : User, cart : Cart, plan : RepeatPlan) : void
}
```

## edu.course.shop.service.CartService.RepeatLine

Source: `src/main/java/edu/course/shop/service/CartService.java:35`. Kind: record. Modifiers: public.

```plantuml
class "CartService.RepeatLine" as edu_course_shop_service_CartService_RepeatLine <<record>> {
  -product : Product
  -quantity : int
}
```

## edu.course.shop.service.CartService.RepeatPlan

Source: `src/main/java/edu/course/shop/service/CartService.java:36`. Kind: record. Modifiers: public.

```plantuml
class "CartService.RepeatPlan" as edu_course_shop_service_CartService_RepeatPlan <<record>> {
  -lines : java.util.List<RepeatLine>
  -unavailable : java.util.List<String>
  +RepeatPlan(lines : java.util.List<RepeatLine>, unavailable : java.util.List<String>)
}
```

## edu.course.shop.service.CatalogAssistant

Source: `src/main/java/edu/course/shop/service/CatalogAssistant.java:11`. Kind: class. Modifiers: public final.

```plantuml
class "CatalogAssistant" as edu_course_shop_service_CatalogAssistant {
  -services : Services
  {static} -CATEGORY_WORDS : String
  {static} -REJECTED_CATEGORY : Pattern
  +CatalogAssistant(services : Services)
  {static} -categoryIntent(query : String) : CategoryIntent
  {static} ~requestedCategory(query : String) : Category
  {static} +asksOrders(question : String) : boolean
  {static} -words(s : String) : String
  +prepare(user : User, cart : List<CartItem>, page : Product, memory : Memory, question : String, allowOrders : boolean) : Plan
  {static} -conversationalReply(q : String) : String
  {static} +remember(old : Memory, plan : Plan, question : String, answer : String) : Memory
  {static} ~budget(q : String) : BigDecimal
  {static} -category(q : String) : Category
  {static} -terms(q : String) : Set<String>
}
```

## edu.course.shop.service.CatalogAssistant.CategoryIntent

Source: `src/main/java/edu/course/shop/service/CatalogAssistant.java:29`. Kind: record. Modifiers: private.

```plantuml
class "CatalogAssistant.CategoryIntent" as edu_course_shop_service_CatalogAssistant_CategoryIntent <<record>> {
  -query : String
  -excluded : Set<Category>
  -correction : boolean
}
```

## edu.course.shop.service.CatalogAssistant.Memory

Source: `src/main/java/edu/course/shop/service/CatalogAssistant.java:13`. Kind: record. Modifiers: public.

```plantuml
class "CatalogAssistant.Memory" as edu_course_shop_service_CatalogAssistant_Memory <<record>> {
  -ids : List<Long>
  -category : Category
  -ceiling : BigDecimal
  -turns : List<Turn>
  +Memory(ids : List<Long>, category : Category, ceiling : BigDecimal, turns : List<Turn>)
  {static} +empty() : Memory
}
```

## edu.course.shop.service.CatalogAssistant.Plan

Source: `src/main/java/edu/course/shop/service/CatalogAssistant.java:17`. Kind: record. Modifiers: public.

```plantuml
class "CatalogAssistant.Plan" as edu_course_shop_service_CatalogAssistant_Plan <<record>> {
  -products : List<Product>
  -facts : JsonObject
  -localAnswer : String
  -context : String
  -category : Category
  -ceiling : BigDecimal
  -orderContext : boolean
  +Plan(products : List<Product>, facts : JsonObject, localAnswer : String, context : String, category : Category, ceiling : BigDecimal, orderContext : boolean)
}
```

## edu.course.shop.service.CatalogAssistant.Turn

Source: `src/main/java/edu/course/shop/service/CatalogAssistant.java:12`. Kind: record. Modifiers: public.

```plantuml
class "CatalogAssistant.Turn" as edu_course_shop_service_CatalogAssistant_Turn <<record>> {
  -question : String
  -answer : String
}
```

## edu.course.shop.service.CheckoutService

Source: `src/main/java/edu/course/shop/service/CheckoutService.java:11`. Kind: class. Modifiers: public final.

```plantuml
class "CheckoutService" as edu_course_shop_service_CheckoutService {
  -database : DatabaseManager
  -products : ProductRepository
  -orders : OrderRepository
  +CheckoutService(database : DatabaseManager, products : ProductRepository, orders : OrderRepository)
  +checkout(customer : User, cart : Cart, address : String, payment : PaymentMethod) : Order
  -checkoutLocked(customer : User, cart : Cart, address : String, payment : PaymentMethod) : Order
}
```

## edu.course.shop.service.CompatibilityAdvisor

Source: `src/main/java/edu/course/shop/service/CompatibilityAdvisor.java:7`. Kind: class. Modifiers: public final.

```plantuml
class "CompatibilityAdvisor" as edu_course_shop_service_CompatibilityAdvisor {
  -CompatibilityAdvisor()
  {static} +analyze(products : List<Product>) : List<Check>
  {static} +report(products : List<Product>) : String
  {static} -compare(label : String, a : String, b : String, nameA : String, nameB : String) : Check
  {static} -lengthCheck(gpuLength : String, clearance : String, gpu : String, pcCase : String) : Check
  {static} -wattageCheck(recommended : String, capacity : String, gpu : String, psu : String) : Check
  {static} -hasRole(p : Product, words : String[]) : boolean
  {static} -spec(product : Product, keys : String[]) : String
  {static} -normalize(value : String) : String
  {static} -normalizeComparable(label : String, value : String) : String
  {static} -millimeters(value : String) : Double
  {static} -watts(value : String) : Double
}
```

## edu.course.shop.service.CompatibilityAdvisor.Check

Source: `src/main/java/edu/course/shop/service/CompatibilityAdvisor.java:9`. Kind: record. Modifiers: public.

```plantuml
class "CompatibilityAdvisor.Check" as edu_course_shop_service_CompatibilityAdvisor_Check <<record>> {
  -label : String
  -status : Status
  -evidence : String
}
```

## edu.course.shop.service.CompatibilityAdvisor.Status

Source: `src/main/java/edu/course/shop/service/CompatibilityAdvisor.java:8`. Kind: enum. Modifiers: public.

```plantuml
enum "CompatibilityAdvisor.Status" as edu_course_shop_service_CompatibilityAdvisor_Status {
  MATCH
  CONFLICT
  UNVERIFIED
}
```

## edu.course.shop.service.GeminiAssistant

Source: `src/main/java/edu/course/shop/service/GeminiAssistant.java:24`. Kind: class. Modifiers: public final.

```plantuml
class "GeminiAssistant" as edu_course_shop_service_GeminiAssistant {
  {static} +DEFAULT_MODEL : String
  {static} -JSON : Gson
  {static} -RULES : String
  -config : Config
  -http : AiHttp
  -clock : java.util.function.LongSupplier
  -answers : AiCache<String>
  -imageResults : AiCache<ImageSearchResult>
  -catalogPhotos : AiCache<JsonObject>
  -cacheEpoch : java.util.concurrent.atomic.AtomicLong
  -busy : AtomicBoolean
  -nextAllowed : long
  +GeminiAssistant()
  ~GeminiAssistant(config : Config, sender : Sender)
  ~GeminiAssistant(config : Config, sender : Sender, sleeper : AiHttp.Sleeper, clock : java.util.function.LongSupplier, events : java.util.function.Consumer<AiDiagnostics.Event>)
  -checkSpacing() : void
  -requestBody(body : JsonObject) : String
  +clearCaches() : void
  {static} -productionSender() : Sender
  +configurationProblem() : String
  +model() : String
  +limitsSummary() : String
  ~managerRequest(body : JsonObject) : JsonObject
  +answer(question : String, context : JsonObject) : String
  +summarizeReviews(productName : String, reviews : List<ProductReview>) : String
  +searchByImage(selectedImage : byte[], products : List<Product>) : ImageSearchResult
  {static} -textPart(text : String) : JsonObject
  {static} -imagePart(source : byte[], maxSide : int) : JsonObject
  {static} -parseImageSearch(reply : Reply, candidates : List<Product>) : ImageSearchResult
  {static} -responseFailure(message : String) : AiFailure
  {static} ~parse(reply : Reply) : String
}
```

## edu.course.shop.service.GeminiAssistant.Config

Source: `src/main/java/edu/course/shop/service/GeminiAssistant.java:52`. Kind: class. Modifiers: public static final.

```plantuml
class "GeminiAssistant.Config" as edu_course_shop_service_GeminiAssistant_Config {
  -key : String
  -model : String
  -freeTierConfirmed : boolean
  +Config(key : String, model : String, freeTierConfirmed : boolean)
  {static} +environment() : Config
  +problem() : String
  +model() : String
  +toString() : String
}
```

## edu.course.shop.service.GeminiAssistant.HttpClientHolder

Source: `src/main/java/edu/course/shop/service/GeminiAssistant.java:120`. Kind: class. Modifiers: private static final.

```plantuml
class "GeminiAssistant.HttpClientHolder" as edu_course_shop_service_GeminiAssistant_HttpClientHolder {
  {static} -CLIENT : HttpClient
}
```

## edu.course.shop.service.GeminiAssistant.ImageMatch

Source: `src/main/java/edu/course/shop/service/GeminiAssistant.java:74`. Kind: record. Modifiers: public.

```plantuml
class "GeminiAssistant.ImageMatch" as edu_course_shop_service_GeminiAssistant_ImageMatch <<record>> {
  -productId : long
  -confidence : int
  -reason : String
}
```

## edu.course.shop.service.GeminiAssistant.ImageSearchResult

Source: `src/main/java/edu/course/shop/service/GeminiAssistant.java:75`. Kind: record. Modifiers: public.

```plantuml
class "GeminiAssistant.ImageSearchResult" as edu_course_shop_service_GeminiAssistant_ImageSearchResult <<record>> {
  -identifiedItem : String
  -matches : List<ImageMatch>
  +ImageSearchResult(identifiedItem : String, matches : List<ImageMatch>)
}
```

## edu.course.shop.service.GeminiAssistant.Reply

Source: `src/main/java/edu/course/shop/service/GeminiAssistant.java:78`. Kind: record. Modifiers: package access.

```plantuml
class "GeminiAssistant.Reply" as edu_course_shop_service_GeminiAssistant_Reply <<record>> {
  -status : int
  -body : String
  -headers : Map<String, String>
  ~Reply(status : int, body : String)
  ~Reply(status : int, body : String, headers : Map<String, String>)
}
```

## edu.course.shop.service.GeminiAssistant.Sender

Source: `src/main/java/edu/course/shop/service/GeminiAssistant.java:82`. Kind: interface. Modifiers: package access.

```plantuml
interface "GeminiAssistant.Sender" as edu_course_shop_service_GeminiAssistant_Sender {
  +send(request : HttpRequest) : Reply
}
```

## edu.course.shop.service.ManagerAi

Source: `src/main/java/edu/course/shop/service/ManagerAi.java:10`. Kind: class. Modifiers: public final.

```plantuml
class "ManagerAi" as edu_course_shop_service_ManagerAi {
  -gemini : GeminiAssistant
  +ManagerAi(gemini : GeminiAssistant)
  +configured() : boolean
  +configurationProblem() : String
  ~classify(command : String) : ManagerCommand
  ~draft(products : List<Product>, research : boolean) : List<Draft>
  {static} ~validSource(text : String) : boolean
  {static} -parts(response : JsonObject) : JsonArray
  {static} -schema(type : String, description : String) : JsonObject
  {static} -enumSchema(description : String, values : String[]) : JsonObject
  {static} -request(rules : String, input : String, tokens : int) : JsonObject
}
```

## edu.course.shop.service.ManagerAi.Draft

Source: `src/main/java/edu/course/shop/service/ManagerAi.java:11`. Kind: record. Modifiers: public.

```plantuml
class "ManagerAi.Draft" as edu_course_shop_service_ManagerAi_Draft <<record>> {
  -id : long
  -description : String
  -specifications : String
  -sources : List<String>
  -note : String
  +Draft(id : long, description : String, specifications : String, sources : List<String>, note : String)
}
```

## edu.course.shop.service.ManagerCommand

Source: `src/main/java/edu/course/shop/service/ManagerCommand.java:11`. Kind: record. Modifiers: public.

```plantuml
class "ManagerCommand" as edu_course_shop_service_ManagerCommand <<record>> {
  -intent : Intent
  -query : String
  -category : Category
  -belowPrice : BigDecimal
  -stockAtMost : Integer
  -stockAtLeast : Integer
  -status : String
  -targetActive : Boolean
  -missingContent : boolean
  -missingField : String
  -ids : List<Long>
  -period : String
  -days : int
  -includeRestock : boolean
  -question : String
  +ManagerCommand(intent : Intent, query : String, category : Category, belowPrice : BigDecimal, stockAtMost : Integer, stockAtLeast : Integer, status : String, targetActive : Boolean, missingContent : boolean, missingField : String, ids : List<Long>, period : String, days : int, includeRestock : boolean, question : String)
  +matches(p : Product) : boolean
  {static} +needsContent(p : Product) : boolean
  {static} ~parse(tool : String, args : JsonObject) : ManagerCommand
  {static} ~arguments(intent : Intent) : Set<String>
  {static} -has(o : JsonObject, k : String) : boolean
  {static} -string(o : JsonObject, k : String, fallback : String) : String
  {static} -bool(o : JsonObject, k : String) : boolean
  {static} ~offline(request : String) : ManagerCommand
  {static} -clarify(text : String) : ManagerCommand
  {static} -similarity(tokens : Set<String>, concepts : String) : double
}
```

## edu.course.shop.service.ManagerCommand.Intent

Source: `src/main/java/edu/course/shop/service/ManagerCommand.java:14`. Kind: enum. Modifiers: public.

```plantuml
enum "ManagerCommand.Intent" as edu_course_shop_service_ManagerCommand_Intent {
  FIX_CONTENT
  FIND_LOW_STOCK
  QUERY_PRODUCTS
  SALES_SUMMARY
  UPDATE_STATUS
  CLARIFY
}
```

## edu.course.shop.service.OrderService

Source: `src/main/java/edu/course/shop/service/OrderService.java:10`. Kind: class. Modifiers: public final.

```plantuml
class "OrderService" as edu_course_shop_service_OrderService {
  -database : DatabaseManager
  -orders : OrderRepository
  +OrderService(database : DatabaseManager, orders : OrderRepository)
  -authorize(c : Connection, user : User, order : Order, adminOnly : boolean) : void
  +history(customer : User) : List<Order>
  +get(actor : User, id : long) : Order
  +recordDeliveryPayment(admin : User, id : long) : Order
  +update(admin : User, id : long, expected : FulfillmentStatus, next : FulfillmentStatus, carrier : String, tracking : String) : Order
  +cancel(customer : User, id : long, expected : FulfillmentStatus) : Order
  -change(actor : User, id : long, expected : FulfillmentStatus, next : FulfillmentStatus, carrier : String, tracking : String, customerCancel : boolean) : Order
}
```

## edu.course.shop.service.Passwords

Source: `src/main/java/edu/course/shop/service/Passwords.java:9`. Kind: class. Modifiers: public final.

```plantuml
class "Passwords" as edu_course_shop_service_Passwords {
  -Passwords()
  {static} +hash(password : String) : String
  {static} +matches(password : String, stored : String) : boolean
  {static} -derive(password : String, salt : byte[]) : byte[]
}
```

## edu.course.shop.service.PersistentCartService

Source: `src/main/java/edu/course/shop/service/PersistentCartService.java:9`. Kind: class. Modifiers: public final.

```plantuml
class "PersistentCartService" as edu_course_shop_service_PersistentCartService {
  -database : DatabaseManager
  -products : ProductRepository
  +PersistentCartService(database : DatabaseManager, products : ProductRepository)
  -customer(c : Connection, user : User) : void
  +restore(user : User, guest : Cart) : Restored
  -restoreLine(c : Connection, cart : Cart, id : long, quantity : int, oldPrice : long, notices : List<String>) : void
  -save(user : User, rows : List<CartItem>) : void
}
```

## edu.course.shop.service.PersistentCartService.Restored

Source: `src/main/java/edu/course/shop/service/PersistentCartService.java:10`. Kind: record. Modifiers: public.

```plantuml
class "PersistentCartService.Restored" as edu_course_shop_service_PersistentCartService_Restored <<record>> {
  -cart : Cart
  -notices : List<String>
  +Restored(cart : Cart, notices : List<String>)
}
```

## edu.course.shop.service.ProductPhotos

Source: `src/main/java/edu/course/shop/service/ProductPhotos.java:8`. Kind: class. Modifiers: public final.

```plantuml
class "ProductPhotos" as edu_course_shop_service_ProductPhotos {
  {static} +MAX_BYTES : int
  -ProductPhotos()
  {static} +read(file : Path) : byte[]
  {static} +validate(bytes : byte[]) : void
}
```

## edu.course.shop.service.ProductReviewService

Source: `src/main/java/edu/course/shop/service/ProductReviewService.java:14`. Kind: class. Modifiers: public final.

```plantuml
class "ProductReviewService" as edu_course_shop_service_ProductReviewService {
  -database : DatabaseManager
  -reviews : ProductReviewRepository
  +ProductReviewService(database : DatabaseManager, reviews : ProductReviewRepository)
  +list(productId : long) : List<ProductReview>
  +canReview(actor : User, productId : long) : boolean
  +submit(actor : User, productId : long, rating : int, text : String) : void
}
```

## edu.course.shop.service.ProductService

Source: `src/main/java/edu/course/shop/service/ProductService.java:9`. Kind: class. Modifiers: public final.

```plantuml
class "ProductService" as edu_course_shop_service_ProductService {
  -database : DatabaseManager
  -products : ProductRepository
  +ProductService(database : DatabaseManager, products : ProductRepository)
  +delete(actor : User, expected : Product) : void
  +savedCarts() : PersistentCartService
  +search(user : User, query : String, category : Category) : List<Product>
  +search(user : User, query : String, category : Category, inStockOnly : boolean, maximumPrice : BigDecimal) : List<Product>
  +get(id : long) : Product
  +applyManagerChanges(actor : User, batchId : String, intent : String, changes : List<ManagerChange>) : void
  +save(actor : User, id : long, name : String, category : Category, price : BigDecimal, stock : int, active : boolean) : void
  +save(actor : User, id : long, name : String, category : Category, price : BigDecimal, stock : int, active : boolean, description : String, photo : byte[]) : void
  +updateInventoryNumbers(actor : User, expected : Product, price : BigDecimal, stock : int) : void
  +save(actor : User, id : long, name : String, category : Category, price : BigDecimal, stock : int, active : boolean, description : String, photo : byte[], additionalPhotos : List<byte[]>, specifications : String) : void
  +save(actor : User, id : long, name : String, category : Category, price : BigDecimal, stock : int, active : boolean, description : String, photo : byte[], additionalPhotos : List<byte[]>, specifications : String, frame : PhotoFrame) : void
}
```

## edu.course.shop.service.ProductService.ManagerChange

Source: `src/main/java/edu/course/shop/service/ProductService.java:49`. Kind: record. Modifiers: public.

```plantuml
class "ProductService.ManagerChange" as edu_course_shop_service_ProductService_ManagerChange <<record>> {
  -expected : Product
  -description : String
  -specifications : String
  -active : Boolean
  -sources : String
}
```

## edu.course.shop.service.RevenueAnalytics

Source: `src/main/java/edu/course/shop/service/RevenueAnalytics.java:11`. Kind: class. Modifiers: public final.

```plantuml
class "RevenueAnalytics" as edu_course_shop_service_RevenueAnalytics {
  -RevenueAnalytics()
  {static} +calculate(orders : List<OrderRow>, start : LocalDate, end : LocalDate, clock : Clock) : Report
  {static} -period(start : LocalDate, end : LocalDate, grouped : Map<LocalDate, List<OrderRow>>) : Period
  {static} -paid(order : OrderRow) : boolean
}
```

## edu.course.shop.service.RevenueAnalytics.Day

Source: `src/main/java/edu/course/shop/service/RevenueAnalytics.java:19`. Kind: record. Modifiers: public.

```plantuml
class "RevenueAnalytics.Day" as edu_course_shop_service_RevenueAnalytics_Day <<record>> {
  -date : LocalDate
  -orders : List<OrderRow>
  +Day(date : LocalDate, orders : List<OrderRow>)
  +matching(metric : Metric) : List<OrderRow>
  +cents(metric : Metric) : long
  +count(metric : Metric) : int
}
```

## edu.course.shop.service.RevenueAnalytics.Metric

Source: `src/main/java/edu/course/shop/service/RevenueAnalytics.java:13`. Kind: enum. Modifiers: public.

```plantuml
enum "RevenueAnalytics.Metric" as edu_course_shop_service_RevenueAnalytics_Metric {
  PAID_REVENUE
  ORDER_VALUE
  -label : String
  ~Metric(label : String)
  +label() : String
}
```

## edu.course.shop.service.RevenueAnalytics.Period

Source: `src/main/java/edu/course/shop/service/RevenueAnalytics.java:25`. Kind: record. Modifiers: public.

```plantuml
class "RevenueAnalytics.Period" as edu_course_shop_service_RevenueAnalytics_Period <<record>> {
  -start : LocalDate
  -end : LocalDate
  -days : List<Day>
  +Period(start : LocalDate, end : LocalDate, days : List<Day>)
  +cents(metric : Metric) : long
  +count(metric : Metric) : int
  +average(metric : Metric) : BigDecimal
}
```

## edu.course.shop.service.RevenueAnalytics.Report

Source: `src/main/java/edu/course/shop/service/RevenueAnalytics.java:31`. Kind: record. Modifiers: public.

```plantuml
class "RevenueAnalytics.Report" as edu_course_shop_service_RevenueAnalytics_Report <<record>> {
  -current : Period
  -previous : Period
  -zone : ZoneId
  -today : LocalDate
  -invalidRecords : int
  -futureRecords : int
  +changePercent(metric : Metric) : Optional<BigDecimal>
}
```

## edu.course.shop.ui.AccountView

Source: `src/main/java/edu/course/shop/ui/AccountView.java:9`. Kind: class. Modifiers: public final.

```plantuml
class "AccountView" as edu_course_shop_ui_AccountView {
  -editing : long
  +AccountView(user : User, accounts : AccountService, savedItems : Runnable, orders : Runnable)
  -reload() : void
}
```

Extends: `VBox`. Implements: `(none)`.

## edu.course.shop.ui.AdminMotion

Source: `src/main/java/edu/course/shop/ui/AdminMotion.java:15`. Kind: class. Modifiers: final.

```plantuml
class "AdminMotion" as edu_course_shop_ui_AdminMotion {
  -AdminMotion()
  {static} ~showing(node : Node) : boolean
  {static} ~animated(node : Node) : boolean
  {static} ~observe(node : Node, sync : Runnable) : void
  {static} ~disclose(node : Node, open : boolean) : void
}
```

## edu.course.shop.ui.AdminMotion.Dots

Source: `src/main/java/edu/course/shop/ui/AdminMotion.java:38`. Kind: class. Modifiers: static final.

```plantuml
class "AdminMotion.Dots" as edu_course_shop_ui_AdminMotion_Dots {
  -pulse : Timeline
  -dots : List<Label>
  -running : boolean
  ~Dots()
  ~setRunning(value : boolean) : void
  ~sync() : void
  ~dispose() : void
}
```

Extends: `HBox`. Implements: `(none)`.

## edu.course.shop.ui.AdminMotion.Watch

Source: `src/main/java/edu/course/shop/ui/AdminMotion.java:20`. Kind: class. Modifiers: private static final.

```plantuml
class "AdminMotion.Watch" as edu_course_shop_ui_AdminMotion_Watch {
  ~node : Node
  ~sync : Runnable
  ~ancestors : List<Node>
  ~window : Window
  ~update : InvalidationListener
  ~windows : ChangeListener<Window>
  ~Watch(node : Node, sync : Runnable)
  ~sync() : void
  ~parents() : void
  ~window(next : Window) : void
}
```

## edu.course.shop.ui.AdminReportsView

Source: `src/main/java/edu/course/shop/ui/AdminReportsView.java:25`. Kind: class. Modifiers: public final.

```plantuml
class "AdminReportsView" as edu_course_shop_ui_AdminReportsView {
  {static} -DATE : DateTimeFormatter
  -admin : User
  -reports : AdminService
  -viewOrders : Runnable
  -viewInventory : Runnable
  -viewAi : Runnable
  -addProduct : Runnable
  -feedback : Label
  +AdminReportsView(admin : User, reports : AdminService, section : Section, viewOrders : Runnable)
  +AdminReportsView(admin : User, reports : AdminService, section : Section, viewOrders : Runnable, viewInventory : Runnable)
  +AdminReportsView(admin : User, reports : AdminService, section : Section, viewOrders : Runnable, viewInventory : Runnable, viewAi : Runnable, addProduct : Runnable)
  -buildOverview() : void
  -lowStockTable() : TableView<AdminRepository.LowStockRow>
  -metric(label : String, value : String, note : String, icon : String) : VBox
  -resizeStats(stats : FlowPane) : void
  -buildOrders() : void
  -orderTable(id : String) : TableView<AdminRepository.OrderRow>
  -buildCustomers() : void
  {static} -date(value : String) : String
  {static} -payment(value : String) : String
  {static} -paymentStatus(value : String) : String
  {static} -fitTable(table : TableView<?>, rows : int) : void
}
```

Extends: `VBox`. Implements: `(none)`.

## edu.course.shop.ui.AdminReportsView.OverviewData

Source: `src/main/java/edu/course/shop/ui/AdminReportsView.java:236`. Kind: record. Modifiers: private.

```plantuml
class "AdminReportsView.OverviewData" as edu_course_shop_ui_AdminReportsView_OverviewData <<record>> {
  -summary : AdminRepository.Summary
  -orders : List<AdminRepository.OrderRow>
  -lowStock : List<AdminRepository.LowStockRow>
}
```

## edu.course.shop.ui.AdminReportsView.Section

Source: `src/main/java/edu/course/shop/ui/AdminReportsView.java:26`. Kind: enum. Modifiers: public.

```plantuml
enum "AdminReportsView.Section" as edu_course_shop_ui_AdminReportsView_Section {
  OVERVIEW
  ORDERS
  CUSTOMERS
}
```

## edu.course.shop.ui.AdminView

Source: `src/main/java/edu/course/shop/ui/AdminView.java:11`. Kind: class. Modifiers: public final.

```plantuml
class "AdminView" as edu_course_shop_ui_AdminView {
  -editingId : long
  -originalSpecifications : String
  -activeCount : Label
  -lowCount : Label
  -photoCount : Label
  -all : List<Product>
  -table : TableView<Product>
  -search : TextField
  -status : ComboBox<String>
  -filterCategory : ComboBox<String>
  -count : Label
  -selected : java.util.Set<Long>
  -bulk : HBox
  -selectionCount : Label
  -selectAll : CheckBox
  -reload : Runnable
  -actor : User
  -productService : ProductService
  -editFeedback : java.util.Map<String, EditFeedback>
  -drafts : java.util.Map<String, String>
  +AdminView(admin : User, products : ProductService)
  +AdminView(admin : User, products : ProductService, aiCommand : java.util.function.Consumer<String>)
  -filter() : void
  -updateSelection() : void
  -bulkCommand(command : java.util.function.Consumer<String>, prefix : String) : void
  -replaceProduct(updated : Product) : void
  -undoInventory(workspace : AdminWorkspace, before : Product, after : Product) : void
  -editable(column : TableColumn<Product, String>, price : boolean) : void
  -exportCsv() : void
  {static} -csv(text : String) : String
}
```

Extends: `VBox`. Implements: `(none)`.

## edu.course.shop.ui.AdminView.EditFeedback

Source: `src/main/java/edu/course/shop/ui/AdminView.java:27`. Kind: record. Modifiers: private.

```plantuml
class "AdminView.EditFeedback" as edu_course_shop_ui_AdminView_EditFeedback <<record>> {
  -message : String
  -pending : boolean
  -error : boolean
}
```

## edu.course.shop.ui.AdminWorkspace

Source: `src/main/java/edu/course/shop/ui/AdminWorkspace.java:17`. Kind: class. Modifiers: public final.

```plantuml
class "AdminWorkspace" as edu_course_shop_ui_AdminWorkspace {
  -pageHost : BorderPane
  -section : Label
  -description : Label
  -navigation : Map<String, Button>
  -counts : Map<String, Label>
  -services : Services
  -actor : User
  -addProduct : Runnable
  -theme : Button
  -modal : StackPane
  -modalClosed : Runnable
  -dark : boolean
  -navIndicator : Region
  -navMotion : Timeline
  -selectedNavigation : String
  -indicatorPlaced : boolean
  -modalCard : Node
  -restoreFocus : Node
  -modalDrawer : boolean
  -closing : boolean
  -modalExit : Animation
  -notice : HBox
  -noticeHold : PauseTransition
  +AdminWorkspace(actor : User, services : Services, overview : Runnable, products : Runnable, orders : Runnable, customers : Runnable, ai : Runnable, addProduct : Runnable, signOut : Runnable)
  +pageHost() : BorderPane
  -moveIndicator(animate : boolean) : void
  -nav(title : String, icon : String, id : String, action : Runnable, count : boolean) : Button
  +select(id : String) : void
  -toggleTheme() : void
  -commandMenu(overview : Runnable, products : Runnable, orders : Runnable, customers : Runnable, ai : Runnable) : void
  -showModal(card : Node, drawer : boolean) : void
  -cycleFocus(backwards : boolean) : void
  {static} -focusable(node : Node, items : List<Node>) : void
  -closeModal() : void
  -closeModalNow(restore : boolean) : void
  {static} +placeNotice(owner : Node) : void
  -placeNotice() : void
  -dismissNotice() : void
  {static} ~find(owner : Node) : AdminWorkspace
  {static} +notice(owner : Node, message : String, undo : Runnable) : void
  {static} +failure(owner : Node, message : String) : void
  -showNotice(message : String, undo : Runnable, error : boolean) : void
  {static} +inventoryChanged(owner : Node) : void
  {static} +editProduct(owner : Node, form : VBox) : void
  {static} +inspect(owner : Node, type : String, title : String, fields : List<Node>, edit : Runnable) : void
  {static} +detail(label : String, value : String) : VBox
  {static} +badges(column : TableColumn<T, String>, variant : java.util.function.Function<String, String>) : void
}
```

Extends: `StackPane`. Implements: `(none)`.

## edu.course.shop.ui.AiManagerView

Source: `src/main/java/edu/course/shop/ui/AiManagerView.java:20`. Kind: class. Modifiers: public final.

```plantuml
class "AiManagerView" as edu_course_shop_ui_AiManagerView {
  -actor : User
  -services : Services
  -manager : AiManagerService
  -gemini : GeminiAssistant
  -input : TextField
  -robot : RobotAvatar
  -badge : Label
  -feedback : Label
  -results : VBox
  -chips : FlowPane
  -metrics : FlowPane
  -research : CheckBox
  -run : Button
  -cancel : Button
  -reviews : List<Review>
  -activity : VBox
  -terminalDots : AdminMotion.Dots
  -activityStatus : Label
  -pending : Task<?>
  -metricsTask : Task<?>
  -shimmer : Animation
  -generation : int
  -metricsGeneration : int
  -disposed : boolean
  -busy : boolean
  -applying : boolean
  +AiManagerView(actor : User, services : Services, gemini : GeminiAssistant)
  -setMode(offline : boolean) : void
  -loadMetrics() : void
  -metric(label : String, count : int) : VBox
  -resizeMetrics() : void
  -chip(title : String, command : String, id : String) : Button
  -empty() : void
  -run() : void
  +submit(command : String) : void
  -log(text : String) : void
  -setBusy(value : boolean) : void
  -loading() : void
  -finish() : void
  -render(result : AiManagerService.Result) : void
  -revealResults() : void
  -editor(before : String, value : String, title : String, parent : VBox) : TextArea
  -filter(row : FlowPane, value : String) : void
  -salesMetric(label : String, value : String) : VBox
  -productTable(products : List<Product>, total : int) : VBox
  -apply(result : AiManagerService.Result) : void
  -failure(error : Throwable) : void
  -cancel() : void
  +dispose() : void
  -showLimits() : void
  -showDiagnostics() : void
  -showPrivacy() : void
  -openSource(source : String) : void
  {static} -start(task : Task<?>) : void
}
```

Extends: `BorderPane`. Implements: `(none)`.

## edu.course.shop.ui.AiManagerView.Review

Source: `src/main/java/edu/course/shop/ui/AiManagerView.java:34`. Kind: record. Modifiers: private.

```plantuml
class "AiManagerView.Review" as edu_course_shop_ui_AiManagerView_Review <<record>> {
  -original : ProductService.ManagerChange
  -included : CheckBox
  -description : TextArea
  -specs : TextArea
}
```

## edu.course.shop.ui.AssistantPanel

Source: `src/main/java/edu/course/shop/ui/AssistantPanel.java:18`. Kind: class. Modifiers: public final.

```plantuml
class "AssistantPanel" as edu_course_shop_ui_AssistantPanel {
  -services : Services
  -user : User
  -cart : Supplier<Cart>
  -page : Supplier<Product>
  -gemini : GeminiAssistant
  -retrieval : CatalogAssistant
  -savedItems : SavedItems
  -preferenceStore : AssistantPreferences
  -savedPreferences : AssistantPreferences.Profile
  -viewProduct : Consumer<Product>
  -cartChanged : Runnable
  -messages : VBox
  -scroll : ScrollPane
  -input : TextField
  -send : Button
  -context : Label
  -memory : CatalogAssistant.Memory
  -pending : Task<?>
  -thinking : Node
  -robot : RobotAvatar
  -dots : Timeline
  -generation : int
  -consent : boolean
  -busy : boolean
  -lastFailed : String
  +AssistantPanel(services : Services, user : User, cart : Supplier<Cart>, page : Supplier<Product>, gemini : GeminiAssistant, viewProduct : Consumer<Product>, cartChanged : Runnable, savedItems : SavedItems, close : Runnable)
  +greet() : void
  +refreshContext() : void
  -preferenceSuffix() : String
  {static} -preferenceLabels(profile : AssistantPreferences.Profile) : List<String>
  -personalize(question : String) : String
  -newChat() : void
  -showLimits() : void
  -showPrivacy() : void
  -submit() : void
  -submit(question : String, orderApproved : boolean, retry : boolean) : void
  -confirmCartEdit(edit : AssistantCart.Edit) : void
  -showAiConsent(plan : CatalogAssistant.Plan, question : String, previous : CatalogAssistant.Memory) : void
  -startGemini(plan : CatalogAssistant.Plan, question : String, previous : CatalogAssistant.Memory) : void
  -renderAnswer(plan : CatalogAssistant.Plan, previous : CatalogAssistant.Memory, question : String, text : String) : void
  -appendProductCards(bubble : VBox, plan : CatalogAssistant.Plan) : void
  -addLoadMore(bubble : VBox, plan : CatalogAssistant.Plan, offset : int) : void
  -productCard(p : Product, bestMatch : boolean, plan : CatalogAssistant.Plan) : Node
  {static} -productNote(p : Product, plan : CatalogAssistant.Plan) : String
  {static} -productEvidence(p : Product) : Node
  -confirmAdd(products : List<Product>) : void
  +stop() : void
  -finish() : void
  -setBusy(value : boolean) : void
  -showThinking(message : String) : void
  -bot(text : String) : VBox
  -userBubble(text : String) : void
  -trim() : void
  {static} -wrap(text : String, style : String) : Label
  -scrollDown() : void
  {static} -start(task : Task<?>) : void
  {static} ~friendly(error : Throwable) : String
  {static} -isSetupFailure(error : Throwable) : boolean
}
```

Extends: `BorderPane`. Implements: `(none)`.

## edu.course.shop.ui.CartDrawer

Source: `src/main/java/edu/course/shop/ui/CartDrawer.java:9`. Kind: class. Modifiers: public final.

```plantuml
class "CartDrawer" as edu_course_shop_ui_CartDrawer {
  -user : User
  -cart : Cart
  -carts : CartService
  -changed : Runnable
  -close : Runnable
  -review : Runnable
  -checkout : Runnable
  -removed : CartItem
  -undoTimeout : javafx.animation.PauseTransition
  -undo : Button
  -lines : VBox
  -bottom : VBox
  -feedback : Label
  -count : Label
  +CartDrawer(user : User, cart : Cart, carts : CartService, changed : Runnable, close : Runnable, review : Runnable, checkout : Runnable)
  -render() : void
  -update(product : Product, quantity : int) : void
  -undoRemoval() : void
}
```

Extends: `VBox`. Implements: `(none)`.

## edu.course.shop.ui.CartView

Source: `src/main/java/edu/course/shop/ui/CartView.java:18`. Kind: class. Modifiers: public final.

```plantuml
class "CartView" as edu_course_shop_ui_CartView {
  -user : User
  -cart : Cart
  -carts : CartService
  -changed : Runnable
  -checkout : Runnable
  -browse : Runnable
  -feedback : Label
  -removed : edu.course.shop.model.CartItem
  -undoTimeout : javafx.animation.PauseTransition
  -undo : Button
  -undoBar : HBox
  -body : VBox
  +CartView(user : User, cart : Cart, carts : CartService, changed : Runnable, checkout : Runnable, browse : Runnable)
  -refresh() : void
  -line(item : CartItem) : VBox
  -update(p : Product, count : int) : void
  -undoRemoval() : void
}
```

Extends: `VBox`. Implements: `(none)`.

## edu.course.shop.ui.CatalogState

Source: `src/main/java/edu/course/shop/ui/CatalogState.java:3`. Kind: class. Modifiers: public final.

```plantuml
class "CatalogState" as edu_course_shop_ui_CatalogState {
  -specificationFilters : java.util.Map<String, String>
  -minimumPrice : String
  -maximumPrice : String
  -categoryIndex : int
  -sortIndex : int
  -budgetIndex : int
  -inStockOnly : boolean
  -filtersExpanded : boolean
  -scrollPosition : double
  +getSpecificationFilters() : java.util.Map<String, String>
  +setSpecificationFilters(value : java.util.Map<String, String>) : void
  +getMinimumPrice() : String
  +getMaximumPrice() : String
  +setPriceRange(minimum : String, maximum : String) : void
  +getCategoryIndex() : int
  +setCategoryIndex(value : int) : void
  +getSortIndex() : int
  +setSortIndex(value : int) : void
  +getBudgetIndex() : int
  +setBudgetIndex(value : int) : void
  +isInStockOnly() : boolean
  +setInStockOnly(value : boolean) : void
  +isFiltersExpanded() : boolean
  +setFiltersExpanded(value : boolean) : void
  +getScrollPosition() : double
  +setScrollPosition(value : double) : void
}
```

## edu.course.shop.ui.CatalogView

Source: `src/main/java/edu/course/shop/ui/CatalogView.java:36`. Kind: class. Modifiers: public final.

```plantuml
class "CatalogView" as edu_course_shop_ui_CatalogView {
  -clearing : boolean
  -restoring : boolean
  -rangeApplied : javafx.beans.property.BooleanProperty
  -toggle : Button
  -imageSearch : Button
  -imageSearchTask : Task<?>
  -gemini : GeminiAssistant
  -preferences : AssistantPreferences
  -filters : FlowPane
  -grid : TilePane
  -search : TextField
  -minimum : TextField
  -maximum : TextField
  -saved : SavedItems
  -state : CatalogState
  -details : Consumer<Product>
  -category : ComboBox<String>
  -sort : ComboBox<String>
  -budget : ComboBox<String>
  -available : CheckBox
  -feedback : Label
  -result : Label
  -title : Label
  -chips : FlowPane
  -results : VBox
  -collectionSection : VBox
  -scroll : ScrollPane
  -hero : EditorialHeader
  -searchBar : HBox
  -suggestions : SearchSuggestions
  -navigation : HBox
  -campaign : javafx.scene.layout.StackPane
  -user : User
  -cart : Cart
  -products : ProductService
  -carts : CartService
  -changed : Runnable
  -toast : Consumer<String>
  -navSync : Runnable
  +enableSavedItems(saved : SavedItems) : void
  +stopSearchSuggestions() : void
  +useHeaderNavigation(host : HBox) : void
  +searchBar() : HBox
  +CatalogView(user : User, cart : Cart, products : ProductService, carts : CartService, changed : Runnable, openCart : Runnable, details : Consumer<Product>, search : TextField, state : CatalogState)
  +CatalogView(user : User, cart : Cart, products : ProductService, carts : CartService, changed : Runnable, openCart : Runnable, details : Consumer<Product>, search : TextField, state : CatalogState, toast : Consumer<String>, navSync : Runnable)
  +CatalogView(user : User, cart : Cart, products : ProductService, carts : CartService, changed : Runnable, openCart : Runnable, details : Consumer<Product>, search : TextField, state : CatalogState, toast : Consumer<String>, navSync : Runnable, gemini : GeminiAssistant, preferences : AssistantPreferences)
  -categoryNavigation() : HBox
  {static} -labeled(title : String, input : javafx.scene.control.Control) : VBox
  -syncToggle() : void
  -applyFilters() : void
  -openFilters() : void
  -validRange() : boolean
  -scrollViewportHeight() : double
  -updateHeroViewport() : void
  -scrollTo(value : double) : void
  -collections() : VBox
  -closing() : VBox
  -filterChanged() : void
  -browseCategory(index : int) : void
  -clear() : void
  -refresh() : void
  -chooseImage() : void
  {static} -imageSearchFailure(error : Throwable) : String
  -imageSearchProgress() : VBox
  -scrollToCollection() : void
  -confirmImageSharing() : boolean
  -chip(text : String, remove : Runnable) : void
  -add(product : Product) : void
  {static} -parseRange(value : String) : BigDecimal
}
```

Extends: `VBox`. Implements: `(none)`.

## edu.course.shop.ui.CatalogView.ImageSearchData

Source: `src/main/java/edu/course/shop/ui/CatalogView.java:37`. Kind: record. Modifiers: private.

```plantuml
class "CatalogView.ImageSearchData" as edu_course_shop_ui_CatalogView_ImageSearchData <<record>> {
  -catalog : List<Product>
  -response : GeminiAssistant.ImageSearchResult
}
```

## edu.course.shop.ui.CategoryBanner

Source: `src/main/java/edu/course/shop/ui/CategoryBanner.java:27`. Kind: class. Modifiers: public final.

```plantuml
class "CategoryBanner" as edu_course_shop_ui_CategoryBanner {
  {static} -ART : EnumMap<Category, Artwork>
  -canvas : Canvas
  -eyebrow : Label
  -headline : Label
  -support : Label
  -pending : Label
  -copy : VBox
  -product : ImageView
  -floating : StackPane
  -contactShadow : Ellipse
  -drift : TranslateTransition
  -lifecycle : InvalidationListener
  -windowListener : ChangeListener<Window>
  -watchedWindow : Window
  -category : Category
  -campaign : Campaign
  +CategoryBanner()
  +showCategory(value : Category) : void
  {static} -load(file : String) : Artwork
  -watchWindow(next : Window) : void
  -syncMotion() : void
  #layoutChildren() : void
}
```

Extends: `Pane`. Implements: `(none)`.

## edu.course.shop.ui.CategoryBanner.Artwork

Source: `src/main/java/edu/course/shop/ui/CategoryBanner.java:29`. Kind: record. Modifiers: private.

```plantuml
class "CategoryBanner.Artwork" as edu_course_shop_ui_CategoryBanner_Artwork <<record>> {
  -image : Image
  -viewport : Rectangle2D
}
```

## edu.course.shop.ui.CategoryBanner.Campaign

Source: `src/main/java/edu/course/shop/ui/CategoryBanner.java:28`. Kind: record. Modifiers: private.

```plantuml
class "CategoryBanner.Campaign" as edu_course_shop_ui_CategoryBanner_Campaign <<record>> {
  -headline : String
  -support : String
  -image : String
}
```

## edu.course.shop.ui.CheckoutDraft

Source: `src/main/java/edu/course/shop/ui/CheckoutDraft.java:3`. Kind: class. Modifiers: public final.

```plantuml
class "CheckoutDraft" as edu_course_shop_ui_CheckoutDraft {
  -address : String
  -paymentIndex : int
  -recipient : String
  -city : String
  -postalCode : String
  +getRecipient() : String
  +setRecipient(value : String) : void
  +getCity() : String
  +setCity(value : String) : void
  +getPostalCode() : String
  +setPostalCode(value : String) : void
  +getAddress() : String
  +setAddress(value : String) : void
  +getPaymentIndex() : int
  +setPaymentIndex(value : int) : void
  +clear() : void
}
```

## edu.course.shop.ui.CheckoutView

Source: `src/main/java/edu/course/shop/ui/CheckoutView.java:11`. Kind: class. Modifiers: public final.

```plantuml
class "CheckoutView" as edu_course_shop_ui_CheckoutView {
  +CheckoutView(user : User, cart : Cart, checkout : CheckoutService, draft : CheckoutDraft, placed : Consumer<Order>, back : Runnable)
  +CheckoutView(user : User, cart : Cart, checkout : CheckoutService, accounts : AccountService, draft : CheckoutDraft, placed : Consumer<Order>, back : Runnable)
  {static} -steps() : HBox
}
```

Extends: `VBox`. Implements: `(none)`.

## edu.course.shop.ui.CollectionArtwork

Source: `src/main/java/edu/course/shop/ui/CollectionArtwork.java:10`. Kind: class. Modifiers: public final.

```plantuml
class "CollectionArtwork" as edu_course_shop_ui_CollectionArtwork {
  -canvas : Canvas
  -subject : String
  +CollectionArtwork(subject : String)
  {static} +supports(name : String) : boolean
  #layoutChildren() : void
  {static} -gradient(light : String, dark : String) : LinearGradient
  -headphones(g : GraphicsContext) : void
  -keyboard(g : GraphicsContext) : void
  -screen(g : GraphicsContext, x : double, y : double, width : double, height : double) : void
  -pc(g : GraphicsContext) : void
  -parts(g : GraphicsContext) : void
  -laptop(g : GraphicsContext) : void
  -mobile(g : GraphicsContext) : void
  -speaker(g : GraphicsContext) : void
  -tee(g : GraphicsContext) : void
  -tote(g : GraphicsContext) : void
  -mug(g : GraphicsContext) : void
  -lamp(g : GraphicsContext) : void
}
```

Extends: `Region`. Implements: `(none)`.

## edu.course.shop.ui.ConfirmationView

Source: `src/main/java/edu/course/shop/ui/ConfirmationView.java:8`. Kind: class. Modifiers: public final.

```plantuml
class "ConfirmationView" as edu_course_shop_ui_ConfirmationView {
  +ConfirmationView(order : Order, orders : Runnable, browse : Runnable)
}
```

Extends: `VBox`. Implements: `(none)`.

## edu.course.shop.ui.EditorialHeader

Source: `src/main/java/edu/course/shop/ui/EditorialHeader.java:34`. Kind: class. Modifiers: public final.

```plantuml
class "EditorialHeader" as edu_course_shop_ui_EditorialHeader {
  -floating : StackPane
  -parallax : StackPane
  -productReveal : StackPane
  -glow : Region
  -drift : TranslateTransition
  -pulse : FadeTransition
  -pointer : TranslateTransition
  -entrance : ParallelTransition
  -lifecycle : InvalidationListener
  -windowListener : ChangeListener<Window>
  -copyNodes : Node[]
  -watchedWindow : Window
  -inViewport : boolean
  -entered : boolean
  +EditorialHeader(shop : Runnable, electronics : Runnable)
  +EditorialHeader(shop : Runnable, electronics : Runnable, headphones : Runnable)
  {static} +forLogin() : EditorialHeader
  -EditorialHeader(shop : Runnable, electronics : Runnable, headphones : Runnable, account : boolean)
  +setInViewport(value : boolean) : void
  -watchWindow(next : Window) : void
  -canAnimate() : boolean
  -syncMotion() : void
  -finishEntrance() : void
  -centerPointer() : void
}
```

Extends: `StackPane`. Implements: `(none)`.

## edu.course.shop.ui.EditorialHeader.CircuitAccents

Source: `src/main/java/edu/course/shop/ui/EditorialHeader.java:224`. Kind: class. Modifiers: private static final.

```plantuml
class "EditorialHeader.CircuitAccents" as edu_course_shop_ui_EditorialHeader_CircuitAccents {
  ~CircuitAccents()
  #layoutChildren() : void
  -line(x1 : double, y1 : double, x2 : double, y2 : double, color : Color) : void
}
```

Extends: `Pane`. Implements: `(none)`.

## edu.course.shop.ui.EditorialHeader.Rings

Source: `src/main/java/edu/course/shop/ui/EditorialHeader.java:246`. Kind: class. Modifiers: private static final.

```plantuml
class "EditorialHeader.Rings" as edu_course_shop_ui_EditorialHeader_Rings {
  -rings : Circle[]
  -streaks : Line[]
  -Rings()
  #layoutChildren() : void
}
```

Extends: `Pane`. Implements: `(none)`.

## edu.course.shop.ui.FilterDrawer

Source: `src/main/java/edu/course/shop/ui/FilterDrawer.java:8`. Kind: class. Modifiers: final.

```plantuml
class "FilterDrawer" as edu_course_shop_ui_FilterDrawer {
  {static} ~show(owner : CatalogView, state : CatalogState, ceiling : double, matches : java.util.List<edu.course.shop.model.Product>, apply : Consumer<Selection>) : void
  {static} -money(value : double) : String
}
```

## edu.course.shop.ui.FilterDrawer.Selection

Source: `src/main/java/edu/course/shop/ui/FilterDrawer.java:9`. Kind: record. Modifiers: package access.

```plantuml
class "FilterDrawer.Selection" as edu_course_shop_ui_FilterDrawer_Selection <<record>> {
  -category : int
  -inStock : boolean
  -minimum : String
  -maximum : String
  -specifications : java.util.Map<String, String>
}
```

## edu.course.shop.ui.Interactions

Source: `src/main/java/edu/course/shop/ui/Interactions.java:19`. Kind: class. Modifiers: public final.

```plantuml
class "Interactions" as edu_course_shop_ui_Interactions {
  {static} -CSS : String
  -Interactions()
  {static} +install(root : Parent) : void
  {static} -enhance(node : Node) : void
  {static} -rotateArrow(owner : Node, open : boolean) : void
  {static} -popup(content : Parent, owner : Node) : void
  {static} ~section(title : String, content : Node) : VBox
  {static} ~categoryIndicator(row : HBox, selection : ObservableValue<?>) : void
}
```

## edu.course.shop.ui.LoginGlassPanel

Source: `src/main/java/edu/course/shop/ui/LoginGlassPanel.java:18`. Kind: class. Modifiers: final.

```plantuml
class "LoginGlassPanel" as edu_course_shop_ui_LoginGlassPanel {
  -reflection : Pane
  -lights : Pane
  -sheen : Polygon
  -mask : Rectangle
  -trails : Rectangle[]
  -phase : DoubleProperty
  -flow : Timeline
  -lifecycle : InvalidationListener
  -windowListener : ChangeListener<Window>
  -window : Window
  ~LoginGlassPanel(form : VBox)
  -geometry() : void
  -positionLights() : void
  -watch(next : Window) : void
  -syncMotion() : void
  ~glowRunning() : boolean
  ~glowPhase() : double
}
```

Extends: `StackPane`. Implements: `(none)`.

## edu.course.shop.ui.LoginView

Source: `src/main/java/edu/course/shop/ui/LoginView.java:12`. Kind: class. Modifiers: public final.

```plantuml
class "LoginView" as edu_course_shop_ui_LoginView {
  +LoginView(auth : AuthService, signedIn : Consumer<User>)
}
```

Extends: `StackPane`. Implements: `(none)`.

## edu.course.shop.ui.Motion

Source: `src/main/java/edu/course/shop/ui/Motion.java:14`. Kind: class. Modifiers: public final.

```plantuml
class "Motion" as edu_course_shop_ui_Motion {
  {static} -reduced : BooleanProperty
  -Motion()
  {static} +reducedProperty() : BooleanProperty
  {static} +isReduced() : boolean
  {static} +lift(target : Node) : void
  {static} +zoom(trigger : Node, image : Node) : void
  {static} +reveal(target : Node) : void
  {static} +dropIn(target : Node) : void
  {static} +press(target : Node) : void
  {static} +popup(target : Node) : void
  {static} +panel(target : Node) : void
  {static} -transientEntrance(target : Node, offset : double, duration : int) : void
  {static} +loading(target : Node) : void
}
```

## edu.course.shop.ui.OrderProcessingView

Source: `src/main/java/edu/course/shop/ui/OrderProcessingView.java:8`. Kind: class. Modifiers: public final.

```plantuml
class "OrderProcessingView" as edu_course_shop_ui_OrderProcessingView {
  +OrderProcessingView(admin : User, service : OrderService, order : Order, refreshed : Runnable)
}
```

Extends: `VBox`. Implements: `(none)`.

## edu.course.shop.ui.OrderSummary

Source: `src/main/java/edu/course/shop/ui/OrderSummary.java:9`. Kind: class. Modifiers: public final.

```plantuml
class "OrderSummary" as edu_course_shop_ui_OrderSummary {
  +OrderSummary(cart : Cart, action : Node)
  -row(name : String, value : String, strong : boolean) : HBox
}
```

Extends: `VBox`. Implements: `(none)`.

## edu.course.shop.ui.OrderTracking

Source: `src/main/java/edu/course/shop/ui/OrderTracking.java:9`. Kind: class. Modifiers: public final.

```plantuml
class "OrderTracking" as edu_course_shop_ui_OrderTracking {
  -OrderTracking()
  {static} +view(order : Order) : VBox
  {static} +payment(order : Order) : String
}
```

## edu.course.shop.ui.OrdersView

Source: `src/main/java/edu/course/shop/ui/OrdersView.java:12`. Kind: class. Modifiers: public final.

```plantuml
class "OrdersView" as edu_course_shop_ui_OrdersView {
  +OrdersView(user : User, orders : OrderService, products : ProductService, browse : Runnable, repeat : Consumer<Order>)
  {static} -showReceipt(receipt : VBox, order : Order, orders : OrderService, user : User, refresh : Runnable) : void
}
```

Extends: `VBox`. Implements: `(none)`.

## edu.course.shop.ui.OrdersView.History

Source: `src/main/java/edu/course/shop/ui/OrdersView.java:47`. Kind: record. Modifiers: private.

```plantuml
class "OrdersView.History" as edu_course_shop_ui_OrdersView_History <<record>> {
  -orders : List<Order>
  -images : Map<Long, Product>
}
```

## edu.course.shop.ui.PriceRange

Source: `src/main/java/edu/course/shop/ui/PriceRange.java:7`. Kind: class. Modifiers: public final.

```plantuml
class "PriceRange" as edu_course_shop_ui_PriceRange {
  -ceiling : double
  -low : DoubleProperty
  -high : DoubleProperty
  -track : Region
  -selection : Region
  -lower : Button
  -upper : Button
  +PriceRange(ceiling : double, from : double, to : double)
  -configure(thumb : Button, minimum : boolean) : void
  -move(minimum : boolean, value : double) : void
  +setRange(from : double, to : double) : void
  +lowProperty() : DoubleProperty
  +highProperty() : DoubleProperty
  +getLow() : double
  +getHigh() : double
  +getCeiling() : double
  -update() : void
  #layoutChildren() : void
}
```

Extends: `Pane`. Implements: `(none)`.

## edu.course.shop.ui.ProductArtwork

Source: `src/main/java/edu/course/shop/ui/ProductArtwork.java:10`. Kind: class. Modifiers: public final.

```plantuml
class "ProductArtwork" as edu_course_shop_ui_ProductArtwork {
  +ProductArtwork(product : Product, height : double)
  +ProductArtwork(product : Product, height : double, square : boolean)
  +ProductArtwork(photo : byte[], name : String, height : double)
  +ProductArtwork(photo : byte[], name : String, height : double, square : boolean, frame : PhotoFrame)
  +ProductArtwork(photo : byte[], name : String, height : double, square : boolean, frame : PhotoFrame, sourceResolution : boolean)
  {static} -alphaBounds(image : Image) : javafx.geometry.Rectangle2D
}
```

Extends: `StackPane`. Implements: `(none)`.

## edu.course.shop.ui.ProductCard

Source: `src/main/java/edu/course/shop/ui/ProductCard.java:16`. Kind: class. Modifiers: public final.

```plantuml
class "ProductCard" as edu_course_shop_ui_ProductCard {
  -quick : Button
  -photoFrame : StackPane
  -productForSaving : Product
  -visualLayer : StackPane
  -confirmation : javafx.animation.PauseTransition
  +confirmAdded() : void
  +ProductCard(product : Product, add : Runnable, details : Runnable)
  +enableSavedItems(saved : SavedItems) : void
  +enableSavedItems(saved : SavedItems, showRemoveAction : boolean) : void
}
```

Extends: `VBox`. Implements: `(none)`.

## edu.course.shop.ui.ProductDetailView

Source: `src/main/java/edu/course/shop/ui/ProductDetailView.java:11`. Kind: class. Modifiers: public final.

```plantuml
class "ProductDetailView" as edu_course_shop_ui_ProductDetailView {
  +ProductDetailView(product : Product, user : User, cart : Cart, carts : CartService, products : ProductService, changed : Runnable, back : Runnable, openCart : Runnable, openProduct : Consumer<Product>)
  +ProductDetailView(product : Product, user : User, cart : Cart, carts : CartService, products : ProductService, changed : Runnable, back : Runnable, openCart : Runnable, openProduct : Consumer<Product>, saved : SavedItems, toast : Consumer<String>)
  +ProductDetailView(product : Product, user : User, cart : Cart, carts : CartService, products : ProductService, changed : Runnable, back : Runnable, openCart : Runnable, openProduct : Consumer<Product>, saved : SavedItems, toast : Consumer<String>, reviews : ProductReviewService, gemini : GeminiAssistant)
  -specifications(product : Product) : VBox
  -section(title : String, text : String) : TitledPane
}
```

Extends: `VBox`. Implements: `(none)`.

## edu.course.shop.ui.ProductGallery

Source: `src/main/java/edu/course/shop/ui/ProductGallery.java:11`. Kind: class. Modifiers: public final.

```plantuml
class "ProductGallery" as edu_course_shop_ui_ProductGallery {
  -product : Product
  -addToCart : Runnable
  -photos : List<byte[]>
  -thumbnails : List<Button>
  -main : Button
  -caption : Label
  -selected : int
  -compact : javafx.beans.property.BooleanProperty
  -windowHeight : javafx.beans.property.DoubleProperty
  +setCompact(value : boolean) : void
  +ProductGallery(product : Product)
  +ProductGallery(product : Product, addToCart : Runnable)
  -select(index : int) : void
  -enlarge() : void
}
```

Extends: `VBox`. Implements: `(none)`.

## edu.course.shop.ui.ProductPhotoEditor

Source: `src/main/java/edu/course/shop/ui/ProductPhotoEditor.java:10`. Kind: class. Modifiers: public final.

```plantuml
class "ProductPhotoEditor" as edu_course_shop_ui_ProductPhotoEditor {
  -photo : byte[]
  -mode : ComboBox<PhotoFrame.Mode>
  -focalX : Slider
  -focalY : Slider
  -dimensions : Label
  -updating : boolean
  -additional : java.util.List<byte[]>
  -gallery : FlowPane
  -preview : VBox
  -feedback : Label
  +getFraming() : PhotoFrame
  +setFraming(framing : PhotoFrame) : void
  +ProductPhotoEditor()
  +importPhoto(file : Path) : void
  +setPhoto(bytes : byte[]) : void
  +setPhotos(main : byte[], extras : java.util.List<byte[]>) : void
  +importAdditionalPhoto(file : Path) : void
  -renderGallery() : void
  +getAdditionalPhotos() : java.util.List<byte[]>
  +getPhoto() : byte[]
  -renderPreview() : void
  -compare() : void
}
```

Extends: `VBox`. Implements: `(none)`.

## edu.course.shop.ui.ProductReviewsView

Source: `src/main/java/edu/course/shop/ui/ProductReviewsView.java:26`. Kind: class. Modifiers: public final.

```plantuml
class "ProductReviewsView" as edu_course_shop_ui_ProductReviewsView {
  {static} -DATE : DateTimeFormatter
  -productId : long
  -productName : String
  -user : User
  -reviews : ProductReviewService
  -gemini : GeminiAssistant
  -preferences : AssistantPreferences
  -average : Label
  -entries : VBox
  -feedback : Label
  -summary : Label
  -summarize : Button
  -rating : ComboBox<Integer>
  -text : TextArea
  +ProductReviewsView(productId : long, productName : String, user : User, reviews : ProductReviewService, gemini : GeminiAssistant)
  -buildForm() : void
  -load() : void
  -reviewCard(review : ProductReview) : VBox
  -submit() : void
  -summarize() : void
  -confirmSharing() : boolean
  -saveConsent() : boolean
  {static} -date(value : String) : String
}
```

Extends: `VBox`. Implements: `(none)`.

## edu.course.shop.ui.ProductReviewsView.RegionSpacer

Source: `src/main/java/edu/course/shop/ui/ProductReviewsView.java:126`. Kind: class. Modifiers: private static final.

```plantuml
class "ProductReviewsView.RegionSpacer" as edu_course_shop_ui_ProductReviewsView_RegionSpacer {
}
```

Extends: `javafx.scene.layout.Region`. Implements: `(none)`.

## edu.course.shop.ui.ProductReviewsView.ReviewData

Source: `src/main/java/edu/course/shop/ui/ProductReviewsView.java:125`. Kind: record. Modifiers: private.

```plantuml
class "ProductReviewsView.ReviewData" as edu_course_shop_ui_ProductReviewsView_ReviewData <<record>> {
  -rows : List<ProductReview>
  -canReview : boolean
}
```

## edu.course.shop.ui.QuantityControl

Source: `src/main/java/edu/course/shop/ui/QuantityControl.java:7`. Kind: class. Modifiers: public final.

```plantuml
class "QuantityControl" as edu_course_shop_ui_QuantityControl {
  -value : IntegerProperty
  -valid : ReadOnlyBooleanWrapper
  +validProperty() : ReadOnlyBooleanProperty
  +QuantityControl(maximum : int)
  +getValue() : int
  +valueProperty() : IntegerProperty
}
```

Extends: `HBox`. Implements: `(none)`.

## edu.course.shop.ui.RevenueDraw

Source: `src/main/java/edu/course/shop/ui/RevenueDraw.java:16`. Kind: class. Modifiers: final.

```plantuml
class "RevenueDraw" as edu_course_shop_ui_RevenueDraw {
  -chart : AreaChart<Number, Number>
  -sweep : Timeline
  -progress : DoubleProperty
  -scrolls : List<ScrollPane>
  -viewport : InvalidationListener
  -content : Node
  -previousClip : Node
  -clip : Rectangle
  -bounds : Bounds
  -requested : boolean
  -queued : boolean
  ~RevenueDraw(chart : AreaChart<Number, Number>)
  ~complete() : void
  ~request() : void
  -queue() : void
  -followScrolls() : void
  -viewable() : boolean
  -begin() : void
  -finish() : void
}
```

## edu.course.shop.ui.RevenuePerformanceCard

Source: `src/main/java/edu/course/shop/ui/RevenuePerformanceCard.java:28`. Kind: class. Modifiers: public final.

```plantuml
class "RevenuePerformanceCard" as edu_course_shop_ui_RevenuePerformanceCard {
  {static} -SHORT : DateTimeFormatter
  {static} -FULL : DateTimeFormatter
  -clock : Clock
  -loader : Supplier<List<OrderRow>>
  -rows : List<OrderRow>
  -report : RevenueAnalytics.Report
  -start : LocalDate
  -end : LocalDate
  -metric : Metric
  -hovered : int
  -pinned : int
  -dates : NumberAxis
  -amounts : NumberAxis
  -chart : AreaChart<Number, Number>
  -drawing : RevenueDraw
  -total : Label
  -change : Label
  -orderCount : Label
  -average : Label
  -periodText : Label
  -note : Label
  -updated : Label
  -error : Label
  -totalLabel : Label
  -countLabel : Label
  -compare : CheckBox
  -from : DatePicker
  -to : DatePicker
  -presetGroup : ToggleGroup
  -refresh : Button
  -forward : Button
  -details : VBox
  -overlay : Pane
  -guide : Line
  -dot : Circle
  -hoverDate : Label
  -hoverAmount : Label
  -hoverOrders : Label
  -hoverPrevious : Label
  -hoverCard : VBox
  -pending : Task<List<OrderRow>>
  +RevenuePerformanceCard(loader : Supplier<List<OrderRow>>)
  +RevenuePerformanceCard(loader : Supplier<List<OrderRow>>, clock : Clock)
  {static} -spacer() : Region
  -configureChart() : void
  +setOrders(orders : List<OrderRow>) : void
  -syncDates() : void
  -applyDates() : void
  -shift(direction : int) : void
  -render() : void
  -insidePlot(sceneX : double, sceneY : double) : boolean
  -inspect(index : int) : void
  -hideHover() : void
  -clearSelection() : void
  -showDetails() : void
  -reload() : void
  {static} -money(cents : long) : String
  {static} -currency(amount : BigDecimal) : String
}
```

Extends: `VBox`. Implements: `(none)`.

## edu.course.shop.ui.RobotAvatar

Source: `src/main/java/edu/course/shop/ui/RobotAvatar.java:21`. Kind: class. Modifiers: public final.

```plantuml
class "RobotAvatar" as edu_course_shop_ui_RobotAvatar {
  {static} -FRAME_W : int
  {static} -FRAME_H : int
  {static} -COLUMNS : int
  {static} -FRAMES : int
  {static} -BOX : int
  {static} -PAD_X : int
  {static} -PAD_Y : int
  {static} -ATLASES : Map<State, Image>
  {static} -STILLS : Map<State, Image>
  -picture : ImageView
  -previous : ImageView
  -state : ObjectProperty<State>
  -animated : BooleanProperty
  -playback : Timeline
  -blend : Timeline
  -reaction : PauseTransition
  -typing : PauseTransition
  -rest : PauseTransition
  -size : double
  -frame : int
  -attentive : boolean
  -sleepAfter : Duration
  -ancestors : List<Node>
  -watchedImages : Set<State>
  -lifecycle : InvalidationListener
  -visibility : InvalidationListener
  -windowListener : ChangeListener<Window>
  -imageReady : ChangeListener<Number>
  -window : Window
  +RobotAvatar(size : double)
  +getState() : State
  +setState(value : State) : void
  +stateProperty() : ReadOnlyObjectProperty<State>
  +setAnimated(value : boolean) : void
  +react(value : State) : void
  +replyPresented() : void
  -reactFor(value : State, duration : Duration) : void
  +returnToRest() : void
  +wake() : void
  +setAttentive(value : boolean) : void
  +setSleepAfter(delay : Duration) : void
  +watchInput(input : TextInputControl, busy : BooleanSupplier) : void
  -quiet() : boolean
  -changeExpression(next : State) : void
  -clearBlend() : void
  {static} -still(state : State) : Image
  -atlas(value : State) : Image
  -configure() : void
  -paint() : void
  #layoutChildren() : void
  -present() : boolean
  -canAnimate() : boolean
  -armRest() : void
  -syncPlayback() : void
  -observeVisibility() : void
  -watchWindow(next : Window) : void
  ~isAnimating() : boolean
  ~frameIndex() : int
  ~displayedImage() : Image
  {static} ~cachedAtlasBytes() : long
}
```

Extends: `Region`. Implements: `(none)`.

## edu.course.shop.ui.RobotAvatar.State

Source: `src/main/java/edu/course/shop/ui/RobotAvatar.java:22`. Kind: enum. Modifiers: public.

```plantuml
enum "RobotAvatar.State" as edu_course_shop_ui_RobotAvatar_State {
  IDLE
  THINKING
  SEARCHING
  LISTENING
  SPEAKING
  WORKING
  HAPPY
  ERROR
  SLEEPING
}
```

## edu.course.shop.ui.RobotLauncher

Source: `src/main/java/edu/course/shop/ui/RobotLauncher.java:14`. Kind: class. Modifiers: public final.

```plantuml
class "RobotLauncher" as edu_course_shop_ui_RobotLauncher {
  -robot : RobotAvatar
  -gesture : Timeline
  -click : Timeline
  -focusAttention : PauseTransition
  -open : Runnable
  -pointerInside : boolean
  -opening : boolean
  -window : Window
  -lifecycle : InvalidationListener
  -motion : InvalidationListener
  -windowChanged : ChangeListener<Window>
  +RobotLauncher(open : Runnable)
  -attend() : void
  -activate() : void
  -move(scale : double, x : double, y : double, angle : double, millis : int) : void
  {static} -clamp(value : double) : double
  -available() : boolean
  -neutral() : void
  -reset() : void
  -watchWindow(next : Window) : void
}
```

Extends: `Button`. Implements: `(none)`.

## edu.course.shop.ui.SavedItems

Source: `src/main/java/edu/course/shop/ui/SavedItems.java:10`. Kind: class. Modifiers: public final.

```plantuml
class "SavedItems" as edu_course_shop_ui_SavedItems {
  -ids : ObservableSet<Long>
  -accounts : AccountService
  -pending : Map<Long, Task<Void>>
  -user : User
  +SavedItems(accounts : AccountService)
  +snapshot() : Set<Long>
  +reset() : void
  +signedIn(customer : User, stored : Set<Long>) : void
  +observe(owner : javafx.scene.Node, action : Runnable) : void
  +heart(product : Product) : Button
}
```

## edu.course.shop.ui.SavedItemsView

Source: `src/main/java/edu/course/shop/ui/SavedItemsView.java:8`. Kind: class. Modifiers: public final.

```plantuml
class "SavedItemsView" as edu_course_shop_ui_SavedItemsView {
  +SavedItemsView(user : User, cart : Cart, products : ProductService, saved : SavedItems, changed : Runnable, browse : Runnable, details : Consumer<Product>, toast : Consumer<String>)
}
```

Extends: `VBox`. Implements: `(none)`.

## edu.course.shop.ui.SearchSuggestions

Source: `src/main/java/edu/course/shop/ui/SearchSuggestions.java:26`. Kind: class. Modifiers: public final.

```plantuml
class "SearchSuggestions" as edu_course_shop_ui_SearchSuggestions {
  -headerSearch : TextField
  -user : User
  -products : ProductService
  -details : Consumer<Product>
  -results : Runnable
  -debounce : PauseTransition
  -pending : Task<List<Product>>
  -generation : long
  -closeOverlay : Runnable
  -cleanup : Runnable
  -activeIndex : int
  +SearchSuggestions(search : TextField, user : User, products : ProductService, details : Consumer<Product>, results : Runnable)
  +dismiss() : void
  +close() : void
  -open(query : String) : void
  -query(value : String, rows : VBox, resultScroll : ScrollPane) : void
  -render(rows : VBox, resultScroll : ScrollPane, matches : List<Product>) : void
  -moveActive(delta : int, rows : VBox) : void
  -showAll() : void
}
```

## edu.course.shop.ui.ShopOverlay

Source: `src/main/java/edu/course/shop/ui/ShopOverlay.java:9`. Kind: class. Modifiers: final.

```plantuml
class "ShopOverlay" as edu_course_shop_ui_ShopOverlay {
  {static} ~show(owner : Node, panel : Region, glass : boolean, drawer : boolean, dismissed : Runnable) : Runnable
  {static} -collect(n : Node, nodes : List<Node>) : void
}
```

## edu.course.shop.ui.ShopWindow

Source: `src/main/java/edu/course/shop/ui/ShopWindow.java:43`. Kind: class. Modifiers: public final.

```plantuml
class "ShopWindow" as edu_course_shop_ui_ShopWindow {
  -stage : Stage
  -services : Services
  -saved : SavedItems
  -catalogState : CatalogState
  -search : TextField
  -user : User
  -cart : Cart
  -pendingGuestCart : Cart
  -draft : CheckoutDraft
  -shell : BorderPane
  -window : StackPane
  -overlay : StackPane
  -cartButton : Button
  -adminWorkspace : AdminWorkspace
  -headerSearch : HBox
  -headerNavigation : HBox
  -previousFocus : Node
  -navButtons : List<Button>
  -lastCartCount : int
  -toast : Label
  -toastBar : HBox
  -toastAnimation : SequentialTransition
  -cartPulse : ScaleTransition
  -drawerAnimation : ParallelTransition
  -activeDrawer : CartDrawer
  -drawerScrim : Region
  -drawerClosing : boolean
  -assistant : edu.course.shop.service.GeminiAssistant
  -assistantPanel : AssistantPanel
  -assistantLauncher : Button
  -assistantProduct : Product
  -darkStore : boolean
  +ShopWindow(stage : Stage, services : Services)
  +showStorefront() : void
  +showLogin() : void
  -signedIn(signedIn : User) : void
  -completeSignIn(signedIn : User) : void
  -signOut() : void
  -link(text : String, action : Runnable) : Button
  -buildShell() : void
  -toggleAssistant() : void
  -showInfo() : void
  -category(index : int) : void
  -show(page : Node) : void
  -updateNav() : void
  -updateCart() : void
  -showToast(message : String) : void
  -stopFeedbackAnimations() : void
  +showCatalog() : void
  -showDetails(product : Product) : void
  +showCartDrawer() : void
  {static} -drawerFocusNodes(node : Node, result : List<Node>) : void
  -animateDrawer(x : double, opacity : double, finished : Runnable) : void
  -dismissDrawer() : void
  -closeDrawer() : void
  {static} -canFocus(node : Node) : boolean
  +showCart() : void
  +showCheckout() : void
  +showOrders() : void
  +showAdmin() : void
  -addAdminProduct() : void
  -runAdminCommand(command : String) : void
  -showAdminOverview() : void
  -showAdminOrders() : void
  -showAdminCustomers() : void
  -showAiManager() : void
  -markAdminTab(selected : String) : void
  -showSaved() : void
  -showAccount() : void
  -repeatOrder(order : edu.course.shop.model.Order) : void
}
```

## edu.course.shop.ui.ShopWindow.CustomerSession

Source: `src/main/java/edu/course/shop/ui/ShopWindow.java:88`. Kind: record. Modifiers: private.

```plantuml
class "ShopWindow.CustomerSession" as edu_course_shop_ui_ShopWindow_CustomerSession <<record>> {
  -saved : java.util.Set<Long>
  -restored : edu.course.shop.service.PersistentCartService.Restored
}
```

## edu.course.shop.ui.SpecificationsEditor

Source: `src/main/java/edu/course/shop/ui/SpecificationsEditor.java:9`. Kind: class. Modifiers: public final.

```plantuml
class "SpecificationsEditor" as edu_course_shop_ui_SpecificationsEditor {
  -raw : TextArea
  -rows : VBox
  -schema : VBox
  -fields : Map<String, TextField>
  -units : Map<String, ComboBox<String>>
  -errors : Map<String, Label>
  -category : Category
  -syncing : boolean
  +SpecificationsEditor()
  +setCategory(value : Category) : void
  +getText() : String
  +setText(value : String) : void
  +clear() : void
  +validatedText() : String
  +clearErrors() : void
  -showWarnings() : void
  {static} -unitsFor(key : String) : List<String>
  -render() : void
  {static} -hint(key : String) : String
  -addRow(key : String, value : String) : void
  -sync() : void
}
```

Extends: `VBox`. Implements: `(none)`.

## edu.course.shop.ui.StoreIcon

Source: `src/main/java/edu/course/shop/ui/StoreIcon.java:8`. Kind: class. Modifiers: public final.

```plantuml
class "StoreIcon" as edu_course_shop_ui_StoreIcon {
  -StoreIcon()
  {static} +of(name : String, size : double) : Group
  {static} +of(name : String) : Group
}
```

## edu.course.shop.ui.Ui

Source: `src/main/java/edu/course/shop/ui/Ui.java:28`. Kind: class. Modifiers: public final.

```plantuml
class "Ui" as edu_course_shop_ui_Ui {
  -Ui()
  {static} +main(args : String[]) : void
  {static} +label(text : String, style : String) : Label
  {static} +category(category : Category) : String
  {static} +button(text : String, action : Runnable) : Button
  {static} +primary(text : String, action : Runnable) : Button
  {static} +link(text : String, action : Runnable) : Button
  {static} +round(node : Node, arc : double) : void
  {static} +focus(target : Node) : void
  {static} +flag(control : Control, invalid : boolean) : void
  {static} +row(left : String, right : String, leftStyle : String, rightStyle : String) : HBox
  {static} +page(title : String, subtitle : String, content : Node[]) : VBox
  {static} +feedback(id : String) : Label
  {static} +field(title : String, input : Control, error : Label) : VBox
  {static} +scroll(content : Node) : ScrollPane
  {static} +responsiveColumns(first : javafx.scene.layout.Region, second : javafx.scene.layout.Region, breakpoint : double, gap : double) : VBox
  {static} +skeleton() : VBox
  {static} +skeletonProductGrid(count : int) : TilePane
  {static} +skeletonRows(count : int) : VBox
  {static} +skeletonProductDetail() : VBox
  {static} -skeletonProductCard() : VBox
  {static} -skeletonBlock(width : double, height : double) : javafx.scene.layout.Region
  {static} +purchaseColumns(form : javafx.scene.layout.Region, summary : javafx.scene.layout.Region) : VBox
  {static} -stickySummary(content : VBox, form : javafx.scene.layout.Region, summary : javafx.scene.layout.Region, stacked : boolean[]) : void
  {static} +empty(title : String, detail : String, action : String, callback : Runnable) : VBox
  {static} +column(name : String, value : Function<T, String>) : TableColumn<T, String>
  {static} +table() : TableView<T>
  {static} +integer(text : String) : int
  {static} +selected(table : TableView<T>) : T
  {static} +attempt(action : Runnable) : void
  {static} +message(error : Throwable) : String
  {static} +failure(label : Label, error : Throwable) : void
  {static} +success(label : Label, text : String) : void
  {static} +error(e : Throwable) : void
  {static} +info(message : String) : void
  {static} +async(owner : Node, work : Callable<T>, success : Consumer<T>) : void
  {static} +readOnly(owner : Node, work : Callable<T>, success : Consumer<T>, failure : Consumer<Throwable>) : void
  {static} +async(owner : Node, busyText : String, work : Callable<T>, success : Consumer<T>, failure : Consumer<Throwable>) : void
}
```
