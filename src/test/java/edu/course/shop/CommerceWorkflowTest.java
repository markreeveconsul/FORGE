package edu.course.shop;
import edu.course.shop.exception.*;
import edu.course.shop.model.*;
import edu.course.shop.payment.*;
import edu.course.shop.persistence.*;
import edu.course.shop.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CommerceWorkflowTest {
    @TempDir Path temp;DatabaseManager db;ProductService products;CheckoutService checkout;OrderService orders;AuthService auth;User admin,customer;Product laptop;
    @BeforeEach void setup(){
        db=new DatabaseManager(temp.resolve("commerce.db"));db.initialize();ProductRepository p=new ProductRepository();OrderRepository o=new OrderRepository();
        products=new ProductService(db,p);checkout=new CheckoutService(db,p,o);orders=new OrderService(db,o);auth=new AuthService(db,new UserRepository());admin=auth.login("admin","admin123");customer=auth.login("customer","customer123");
        products.save(admin,0,"Studio laptop 16",Category.LAPTOPS,new BigDecimal("900.00"),10,true,"Fixture",null,List.of(),"Brand: FORGE\nModel: Studio\nSKU: ST-16\nVariant: 16 GB\nRAM: 16GB\nStorage: 1TB");laptop=products.search(admin,"Studio laptop",null).getFirst();
    }
    Cart saved(){return products.savedCarts().restore(customer,null).cart();}
    Order order(boolean paid){Cart cart=saved();new CartService(products).add(customer,cart,laptop.getId(),2);return checkout.checkout(customer,cart,"123 Test Street",paid?new SimulatedWalletPayment(true):new CashOnDeliveryPayment());}
    void stock(int n){products.updateInventoryNumbers(admin,products.get(laptop.getId()),laptop.getPrice(),n);}
    long count(String table)throws Exception{try(var c=db.open();var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();return r.getLong(1);}}
    @Test void cartPersistsAddUpdateRemoveAcrossServiceInstances(){
        Cart cart=saved();new CartService(products).add(customer,cart,laptop.getId(),2);
        assertEquals(2,saved().getItems().getFirst().getQuantity());cart.setQuantity(laptop,3);assertEquals(3,saved().getItems().getFirst().getQuantity());cart.remove(laptop.getId());assertTrue(saved().getItems().isEmpty());assertEquals(10,products.get(laptop.getId()).getStock());
    }
    @Test void guestMergeCombinesQuantitiesAndReportsStockClamp(){
        saved().add(laptop,8);Cart guest=new Cart(0);guest.add(laptop,4);var result=products.savedCarts().restore(customer,guest);
        assertEquals(10,result.cart().getItems().getFirst().getQuantity());assertFalse(result.notices().isEmpty());assertEquals(4,guest.getItems().getFirst().getQuantity());
    }
    @Test void restoreReportsPriceAndStockChanges(){
        saved().add(laptop,5);products.updateInventoryNumbers(admin,laptop,new BigDecimal("950.00"),3);var result=products.savedCarts().restore(customer,null);
        assertEquals(3,result.cart().getItems().getFirst().getQuantity());assertEquals(new BigDecimal("2850.00"),result.cart().getTotal());assertEquals(2,result.notices().size());
    }
    @Test void unavailableItemsAreRemovedWithNotice(){
        saved().add(laptop,1);products.save(admin,laptop.getId(),laptop.getName(),laptop.getCategory(),laptop.getPrice(),10,false);var result=products.savedCarts().restore(customer,null);
        assertTrue(result.cart().getItems().isEmpty());assertFalse(result.notices().isEmpty());
    }
    @Test void savedCartsRejectGuestsAdminsAndUnknownCustomers(){
        assertThrows(ValidationException.class,()->products.savedCarts().restore(new Guest(),null));assertThrows(ValidationException.class,()->products.savedCarts().restore(admin,null));assertThrows(ValidationException.class,()->products.savedCarts().restore(new Customer(999,"missing"),null));
        assertThrows(ValidationException.class,()->products.savedCarts().restore(customer,new Cart(customer.getId())));
    }
    @Test void failedPersistenceDoesNotMutateVisibleCart(){
        Cart cart=new Cart(customer.getId());cart.add(laptop,1);cart.attachPersistence(rows->{throw new ValidationException("Storage failed");});
        assertThrows(ValidationException.class,()->cart.add(laptop,1));assertEquals(1,cart.getItems().getFirst().getQuantity());assertThrows(ValidationException.class,cart::clear);assertEquals(1,cart.getItems().size());
    }
    @Test void checkoutClearsSavedCartAndRecordsInitialTimeline()throws Exception{
        Order order=order(false);assertTrue(saved().getItems().isEmpty());assertEquals(8,products.get(laptop.getId()).getStock());assertEquals(1,order.events().size());assertEquals(FulfillmentStatus.CONFIRMED,order.fulfillmentStatus());assertEquals(1,count("orders"));
    }
    @Test void declinedCheckoutKeepsSavedCartAndStock()throws Exception{
        Cart cart=saved();cart.add(laptop,2);assertThrows(ValidationException.class,()->checkout.checkout(customer,cart,"123 Test Street",new SimulatedWalletPayment(false)));assertEquals(2,saved().getItems().getFirst().getQuantity());assertEquals(10,products.get(laptop.getId()).getStock());assertEquals(0,count("orders"));
    }
    @Test void checkoutRollbackAlsoPreservesSavedCart()throws Exception{
        Cart cart=saved();cart.add(laptop,2);try(var c=db.open();var s=c.createStatement()){s.executeUpdate("CREATE TRIGGER fail_order BEFORE INSERT ON order_events BEGIN SELECT RAISE(ABORT,'test rollback'); END");}
        assertThrows(StorageException.class,()->checkout.checkout(customer,cart,"123 Test Street",new CashOnDeliveryPayment()));assertEquals(2,saved().getItems().getFirst().getQuantity());assertEquals(10,products.get(laptop.getId()).getStock());assertEquals(0,count("orders"));
    }
    @Test void deliveryProgressRequiresTrackingAndRejectsSkippedOrStaleStates(){
        Order order=order(false);long id=order.id();assertThrows(ValidationException.class,()->orders.update(admin,id,FulfillmentStatus.CONFIRMED,FulfillmentStatus.SHIPPED,"Carrier","ABC"));
        orders.update(admin,id,FulfillmentStatus.CONFIRMED,FulfillmentStatus.PACKED,"","");
        assertThrows(ValidationException.class,()->orders.update(admin,id,FulfillmentStatus.CONFIRMED,FulfillmentStatus.PACKED,"",""));
        assertThrows(ValidationException.class,()->orders.update(admin,id,FulfillmentStatus.PACKED,FulfillmentStatus.SHIPPED,"",""));
        Order shipped=orders.update(admin,id,FulfillmentStatus.PACKED,FulfillmentStatus.SHIPPED,"Demo carrier","TRACK-01");assertEquals("TRACK-01",shipped.trackingNumber());assertEquals("PENDING",shipped.paymentStatus());
        Order delivered=orders.update(admin,id,FulfillmentStatus.SHIPPED,FulfillmentStatus.DELIVERED,"","");assertEquals("TRACK-01",delivered.trackingNumber());assertEquals(4,delivered.events().size());assertEquals(8,products.get(laptop.getId()).getStock());
        assertThrows(ValidationException.class,()->orders.update(admin,id,FulfillmentStatus.DELIVERED,FulfillmentStatus.CANCELLED,"",""));
    }
    @Test void deliveredCodIsMarkedPaidOnlyByExplicitAdminAction(){
        Order order=order(false);assertThrows(ValidationException.class,()->orders.recordDeliveryPayment(admin,order.id()));
        orders.update(admin,order.id(),FulfillmentStatus.CONFIRMED,FulfillmentStatus.PACKED,"","");orders.update(admin,order.id(),FulfillmentStatus.PACKED,FulfillmentStatus.SHIPPED,"Demo","ABC");orders.update(admin,order.id(),FulfillmentStatus.SHIPPED,FulfillmentStatus.DELIVERED,"","");
        assertEquals("PENDING",orders.get(customer,order.id()).paymentStatus());assertThrows(ValidationException.class,()->orders.recordDeliveryPayment(customer,order.id()));assertEquals("PAID",orders.recordDeliveryPayment(admin,order.id()).paymentStatus());assertThrows(ValidationException.class,()->orders.recordDeliveryPayment(admin,order.id()));
    }
    @Test void cancellationRestocksOnceAndDoesNotAlterReceipt(){
        Order order=order(false);Order cancelled=orders.cancel(customer,order.id(),FulfillmentStatus.CONFIRMED);assertEquals(10,products.get(laptop.getId()).getStock());assertEquals("CANCELLED",cancelled.paymentStatus());assertEquals(order.items(),cancelled.items());assertEquals(order.total(),cancelled.total());
        assertThrows(ValidationException.class,()->orders.cancel(customer,order.id(),FulfillmentStatus.CONFIRMED));assertEquals(10,products.get(laptop.getId()).getStock());
    }
    @Test void paidCancellationIsAnAdminActionAndRecordsSimulatedRefund(){
        Order order=order(true);assertThrows(ValidationException.class,()->orders.cancel(customer,order.id(),FulfillmentStatus.CONFIRMED));Order cancelled=orders.update(admin,order.id(),FulfillmentStatus.CONFIRMED,FulfillmentStatus.CANCELLED,"","");assertEquals("REFUNDED",cancelled.paymentStatus());assertEquals(10,products.get(laptop.getId()).getStock());
    }
    @Test void customerCannotCancelPackedOrdersOrViewOtherCustomersOrders(){
        Order order=order(false);orders.update(admin,order.id(),FulfillmentStatus.CONFIRMED,FulfillmentStatus.PACKED,"","");assertThrows(ValidationException.class,()->orders.cancel(customer,order.id(),FulfillmentStatus.PACKED));
        assertThrows(ValidationException.class,()->orders.get(new Customer(admin.getId(),"wrong-role"),order.id()));assertThrows(ValidationException.class,()->orders.update(customer,order.id(),FulfillmentStatus.PACKED,FulfillmentStatus.SHIPPED,"Demo","ABC"));
    }
    @Test void failedCancellationRollsBackStatusEventsAndStock()throws Exception{
        Order order=order(false);try(var c=db.open();var s=c.createStatement()){s.executeUpdate("CREATE TRIGGER reject_restock BEFORE UPDATE OF stock ON products BEGIN SELECT RAISE(ABORT,'test rollback'); END");}
        assertThrows(StorageException.class,()->orders.cancel(customer,order.id(),FulfillmentStatus.CONFIRMED));assertEquals(FulfillmentStatus.CONFIRMED,orders.get(customer,order.id()).fulfillmentStatus());assertEquals(8,products.get(laptop.getId()).getStock());assertEquals(1,orders.get(customer,order.id()).events().size());
    }
    @Test void deletionRemovesInventoryAndSavedLinksButPreservesOrderReceipts()throws Exception{
        Order order=order(false);saved().add(products.get(laptop.getId()),1);auth.accounts().saveItem(customer,laptop.getId(),true);products.delete(admin,products.get(laptop.getId()));
        assertTrue(products.search(admin,"Studio laptop",null).isEmpty());assertTrue(products.search(customer,"Studio laptop",null).isEmpty());assertTrue(saved().getItems().isEmpty());assertFalse(auth.accounts().saved(customer).contains(laptop.getId()));assertEquals(order.items(),orders.history(customer).getFirst().items());
        orders.cancel(customer,order.id(),FulfillmentStatus.CONFIRMED);assertEquals(10,products.get(laptop.getId()).getStock());assertFalse(products.get(laptop.getId()).isActive());
    }
    @Test void deletionRejectsCustomersStaleRowsAndRepeatedDeletion(){
        assertThrows(ValidationException.class,()->products.delete(customer,laptop));stock(9);assertThrows(ValidationException.class,()->products.delete(admin,laptop));Product current=products.get(laptop.getId());products.delete(admin,current);assertThrows(ValidationException.class,()->products.delete(admin,current));assertThrows(ValidationException.class,()->products.save(admin,current.getId(),current.getName(),current.getCategory(),current.getPrice(),9,true));
    }
    @Test void aiBatchCannotBringDeletedProductBack(){
        products.delete(admin,laptop);assertThrows(ValidationException.class,()->products.applyManagerChanges(admin,UUID.randomUUID().toString(),"update_status",List.of(new ProductService.ManagerChange(laptop,null,null,true,""))));assertTrue(products.search(admin,"Studio laptop",null).isEmpty());
    }
    @Test void specificationsNormalizeUnitsAndSupportExactAttributeFilters(){
        assertEquals("16 GB",ProductSpecifications.value(laptop,"RAM"));assertEquals("1 TB",ProductSpecifications.value(laptop,"Storage"));assertTrue(ProductSpecifications.matches(laptop,Map.of("Brand","forge","RAM","16 GB")));assertFalse(ProductSpecifications.matches(laptop,Map.of("RAM","32 GB")));
        assertEquals("Length: 30 cm\nWattage: 650 W",ProductSpecifications.validated("GPU length: 30cm\nWattage: 650w"));
    }
    @Test void customSpecificationFormatsAreAdvisoryAndPreservedWhenSaving(){
        for(String specs:List.of("RAM: lots","Storage: -1 TB","Wattage: 0 W","VRAM: Shared memory")){
            products.save(admin,laptop.getId(),laptop.getName(),laptop.getCategory(),laptop.getPrice(),10,true,"Fixture",null,List.of(),specs);
            assertEquals(specs,products.get(laptop.getId()).getSpecifications());assertFalse(ProductSpecifications.formatWarnings(specs).isEmpty());
            assertThrows(ValidationException.class,()->ProductSpecifications.validated(specs));
        }
        assertEquals("VRAM: 8 GB",ProductSpecifications.listingText("VRAM: 8gb"));assertTrue(ProductSpecifications.formatWarnings("VRAM: 8gb").isEmpty());
        String duplicate="RAM: 16 GB\nram capacity: 32 GB";
        assertThrows(ValidationException.class,()->products.save(admin,laptop.getId(),laptop.getName(),laptop.getCategory(),laptop.getPrice(),10,true,"Fixture",null,List.of(),duplicate));
        assertEquals("VRAM: Shared memory",products.get(laptop.getId()).getSpecifications());
        assertThrows(ValidationException.class,()->ProductSpecifications.listingText("Note: "+"x".repeat(201)));
    }
    @Test void configurationsHaveUniqueSkusAndIndependentStock(){
        assertThrows(ValidationException.class,()->products.save(admin,0,"Duplicate",Category.LAPTOPS,BigDecimal.TEN,5,true,"",null,List.of(),"SKU: st-16"));
        products.save(admin,0,"Studio laptop 32",Category.LAPTOPS,new BigDecimal("1100"),4,true,"",null,List.of(),"Brand: FORGE\nModel: Studio\nSKU: ST-32\nVariant: 32 GB\nRAM: 32 GB");Product variant=products.search(admin,"Studio laptop 32",null).getFirst();order(false);assertEquals(4,products.get(variant.getId()).getStock());assertEquals(8,products.get(laptop.getId()).getStock());
    }
    @Test void customFactsSurviveStructuredValidation(){assertEquals("Material: Alloy\nLegacy note\nColor: Black",ProductSpecifications.validated("Material: Alloy\nLegacy note\nColor: Black"));}
    @Test void migrationsAreIdempotentAndPreserveCartReceiptsAndEvents()throws Exception{
        Order order=order(false);saved().add(products.get(laptop.getId()),1);db.initialize();db.initialize();assertEquals(1,orders.history(customer).size());assertEquals(order.total(),orders.get(customer,order.id()).total());assertEquals(1,count("order_events"));assertEquals(1,saved().getItems().size());
    }
    @Test void anotherRealCustomerCannotReadOrCancelAnOrderAndHasAnIndependentCart()throws Exception{
        Order order=order(false);try(var c=db.open();var p=c.prepareStatement("INSERT INTO users(id,username,password_hash,role) VALUES(99,'second','test-only','CUSTOMER')")){p.executeUpdate();}
        User other=new Customer(99,"second");assertTrue(orders.history(other).isEmpty());assertThrows(ValidationException.class,()->orders.get(other,order.id()));assertThrows(ValidationException.class,()->orders.cancel(other,order.id(),FulfillmentStatus.CONFIRMED));assertTrue(products.savedCarts().restore(other,null).cart().getItems().isEmpty());
    }
    @Test void concurrentCheckoutOfOneCartProducesOnlyOneOrder()throws Exception{
        Cart cart=saved();cart.add(laptop,2);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);try{
            var first=pool.submit(()->{try{checkout.checkout(customer,cart,"123 Test Street",new CashOnDeliveryPayment());return true;}catch(ValidationException e){return false;}});
            var second=pool.submit(()->{try{checkout.checkout(customer,cart,"123 Test Street",new CashOnDeliveryPayment());return true;}catch(ValidationException e){return false;}});
            assertEquals(1,(first.get()?1:0)+(second.get()?1:0));assertEquals(1,count("orders"));assertEquals(8,products.get(laptop.getId()).getStock());assertTrue(saved().getItems().isEmpty());
        }finally{pool.shutdownNow();}
    }
    @Test void concurrentCancellationCannotRestockTwice()throws Exception{
        Order order=order(false);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);try{
            java.util.concurrent.Callable<Boolean> cancel=()->{try{orders.cancel(customer,order.id(),FulfillmentStatus.CONFIRMED);return true;}catch(ValidationException|StorageException e){return false;}};
            var first=pool.submit(cancel);var second=pool.submit(cancel);assertEquals(1,(first.get()?1:0)+(second.get()?1:0));assertEquals(10,products.get(laptop.getId()).getStock());assertEquals(2,orders.get(customer,order.id()).events().size());
        }finally{pool.shutdownNow();}
    }
    @Test void oldDatabaseGainsFieldsWithoutLosingHistoricalRows()throws Exception{
        DatabaseManager legacy=new DatabaseManager(temp.resolve("legacy.db"));try(var c=legacy.open();var s=c.createStatement()){
            s.executeUpdate("CREATE TABLE users(id INTEGER PRIMARY KEY,username TEXT,password_hash TEXT,role TEXT)");s.executeUpdate("INSERT INTO users VALUES(1,'legacy','hash','CUSTOMER')");
            s.executeUpdate("CREATE TABLE products(id INTEGER PRIMARY KEY,name TEXT,category TEXT,price_cents INTEGER,stock INTEGER,active INTEGER)");s.executeUpdate("INSERT INTO products VALUES(7,'Legacy GPU','PC_PARTS',50000,4,1)");
            s.executeUpdate("CREATE TABLE orders(id INTEGER PRIMARY KEY,customer_id INTEGER,created_at TEXT,address TEXT,payment_method TEXT,payment_status TEXT,total_cents INTEGER)");s.executeUpdate("INSERT INTO orders VALUES(9,1,'2025-01-01T00:00:00Z','Legacy address','Cash on delivery','PENDING',50000)");
            s.executeUpdate("CREATE TABLE order_items(order_id INTEGER,product_id INTEGER,product_name TEXT,unit_price_cents INTEGER,quantity INTEGER)");s.executeUpdate("INSERT INTO order_items VALUES(9,7,'Original GPU name',50000,1)");
        }
        legacy.initialize();legacy.initialize();Order migrated=new OrderService(legacy,new OrderRepository()).get(new Customer(1,"legacy"),9);
        assertEquals("Original GPU name",migrated.items().getFirst().productName());assertEquals(new BigDecimal("500.00"),migrated.total());assertEquals(FulfillmentStatus.CONFIRMED,migrated.fulfillmentStatus());assertEquals(1,migrated.events().size());assertEquals(4,new ProductService(legacy,new ProductRepository()).get(7).getStock());
    }
    @Test void cancelledOrdersDoNotUnlockVerifiedPurchaseReviews(){
        Order order=order(false);orders.cancel(customer,order.id(),FulfillmentStatus.CONFIRMED);
        ProductReviewService reviews=new ProductReviewService(db,new ProductReviewRepository());
        assertFalse(reviews.canReview(customer,laptop.getId()));
        assertThrows(ValidationException.class,()->reviews.submit(customer,laptop.getId(),5,"A cancelled order should not qualify."));
    }
}
