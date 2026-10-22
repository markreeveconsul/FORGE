package edu.course.shop;
import edu.course.shop.exception.*;
import edu.course.shop.model.*;
import edu.course.shop.payment.*;
import edu.course.shop.persistence.*;
import edu.course.shop.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

class ShopTest {
    @TempDir Path temp;
    DatabaseManager database;
    ProductService products;
    CheckoutService checkout;
    OrderService orders;
    CartService carts;
    AuthService auth;
    User customer, admin;
    Cart cart;
    Product product;

    @BeforeEach void setup() {
        database = new DatabaseManager(temp.resolve("test.db")); database.initialize();
        ProductRepository productRepo = new ProductRepository(); OrderRepository orderRepo = new OrderRepository();
        products = new ProductService(database, productRepo); checkout = new CheckoutService(database, productRepo, orderRepo);
        orders = new OrderService(database, orderRepo); carts = new CartService(products);
        auth = new AuthService(database, new UserRepository());
        customer = auth.login("customer", "customer123"); admin = auth.login("admin", "admin123");
        TechTestCatalog.prepare(products,admin);
        cart = new Cart(customer.getId()); product = products.search(customer, "", null).getFirst();
    }
    @Test void loginChecksCredentialsAndResolvesRoles() {
        assertTrue(admin.canManageProducts()); assertFalse(customer.canManageProducts());
        assertInstanceOf(Admin.class, admin); assertInstanceOf(Customer.class, customer);
        assertThrows(ValidationException.class, () -> auth.login("customer", "wrong"));
        assertThrows(ValidationException.class, () -> auth.login("", ""));
        assertThrows(ValidationException.class, () -> auth.login("' OR 1=1 --", "x"));
    }
    @Test void invalidProductsCannotBeConstructed() {
        assertThrows(ValidationException.class, () -> new Product(0, "", Category.PC_PARTS, BigDecimal.ONE, 1, true));
        assertThrows(ValidationException.class, () -> Money.parse("abc"));
        assertThrows(ValidationException.class, () -> Money.parse("-2"));
        assertThrows(ValidationException.class, () -> Money.parse("1.234"));
        assertThrows(ValidationException.class, () -> new Product(0, "Test", Category.PC_PARTS, BigDecimal.ONE, -1, true));
    }
    @Test void customersCannotEditProductsEvenOutsideGui() {
        assertThrows(ValidationException.class, () -> products.save(customer, 0, "Test", Category.PC_PARTS, BigDecimal.ONE, 1, true));
    }
    @Test void inlineInventoryEditsRequireAdminAndPreserveContent(){
        assertThrows(ValidationException.class,()->products.updateInventoryNumbers(customer,product,BigDecimal.TEN,2));
        assertThrows(ValidationException.class,()->products.updateInventoryNumbers(null,product,BigDecimal.TEN,2));
        assertThrows(ValidationException.class,()->products.updateInventoryNumbers(admin,product,new BigDecimal("0"),2));
        assertThrows(ValidationException.class,()->products.updateInventoryNumbers(admin,product,product.getPrice(),-1));
        products.updateInventoryNumbers(admin,product,new BigDecimal("25.50"),9);
        Product updated=products.get(product.getId());assertEquals(new BigDecimal("25.50"),updated.getPrice());assertEquals(9,updated.getStock());
        assertEquals(product.getDescription(),updated.getDescription());assertEquals(product.getSpecifications(),updated.getSpecifications());assertArrayEquals(product.getPhoto(),updated.getPhoto());assertEquals(product.isActive(),updated.isActive());
    }
    @Test void staleInlineInventoryEditsDoNotOverwriteNewerValues(){
        products.updateInventoryNumbers(admin,product,product.getPrice(),17);
        assertThrows(ValidationException.class,()->products.updateInventoryNumbers(admin,product,BigDecimal.ONE,2));
        assertEquals(17,products.get(product.getId()).getStock());assertEquals(product.getPrice(),products.get(product.getId()).getPrice());
    }
    @Test void adminCreatesEditsAndDeactivatesProducts() {
        products.save(admin, 0, "Test item", Category.PC_PARTS, new BigDecimal("1.25"), 3, true);
        Product p = products.search(customer, "TEST ITEM", Category.PC_PARTS).getFirst();
        products.save(admin, p.getId(), "Updated item", Category.PERIPHERALS, new BigDecimal("2.50"), 5, false);
        assertTrue(products.search(customer, "Updated item", null).isEmpty());
        assertEquals(5, products.search(admin, "Updated item", Category.PERIPHERALS).getFirst().getStock());
    }
    @Test void searchCombinesNameAndCategory() {
        assertEquals(1, products.search(customer, "KEYBOARD", Category.PERIPHERALS).size());
        assertTrue(products.search(customer, "keyboard", Category.PC_PARTS).isEmpty());
        assertEquals(6, products.search(customer, "", null).size());
    }
    @Test void cartCombinesItemsAndProtectsItsCollection() {
        carts.add(customer, cart, product.getId(), 1); carts.add(customer, cart, product.getId(), 2);
        assertEquals(1, cart.getItems().size()); assertEquals(3, cart.getItems().getFirst().getQuantity());
        assertEquals(product.getPrice().multiply(BigDecimal.valueOf(3)), cart.getTotal());
        assertThrows(UnsupportedOperationException.class, () -> cart.getItems().clear());
        carts.update(customer, cart, product.getId(), 2); assertEquals(2, cart.getItems().getFirst().getQuantity());
        cart.remove(product.getId()); assertTrue(cart.getItems().isEmpty());
    }
    @Test void cartRejectsInvalidQuantitiesAndWrongOwner() {
        assertThrows(ValidationException.class, () -> carts.add(customer, cart, product.getId(), 0));
        assertThrows(OutOfStockException.class, () -> carts.add(customer, cart, product.getId(), product.getStock() + 1));
        assertThrows(ValidationException.class, () -> carts.add(admin, cart, product.getId(), 1));
        assertThrows(ValidationException.class, () -> carts.add(new Customer(999, "other"), cart, product.getId(), 1));
    }
    @Test void cashOnDeliveryCommitsOrderAndInventory() {
        carts.add(customer, cart, product.getId(), 2);
        Order order = checkout.checkout(customer, cart, "123 Sample Street", new CashOnDeliveryPayment());
        assertEquals("PENDING", order.paymentStatus()); assertTrue(order.id() > 0);
        assertEquals(product.getStock() - 2, products.get(product.getId()).getStock());
        assertTrue(cart.getItems().isEmpty()); assertEquals(1, orders.history(customer).size());
        assertEquals(product.getPrice().multiply(BigDecimal.valueOf(2)), order.total());
    }
    @Test void approvedWalletUsesDifferentPaymentBehavior() {
        carts.add(customer, cart, product.getId(), 1);
        assertEquals("PAID", checkout.checkout(customer, cart, "123 Sample Street", new SimulatedWalletPayment(true)).paymentStatus());
    }
    @Test void declinedPaymentRollsBackStockAndKeepsCart() {
        carts.add(customer, cart, product.getId(), 1);
        assertThrows(ValidationException.class, () -> checkout.checkout(customer, cart, "123 Sample Street", new SimulatedWalletPayment(false)));
        assertEquals(product.getStock(), products.get(product.getId()).getStock());
        assertEquals(1, cart.getItems().size()); assertTrue(orders.history(customer).isEmpty());
    }
    @Test void insufficientSecondItemRollsBackFirstDeduction() {
        Product second = products.search(customer, "", null).get(1);
        carts.add(customer, cart, product.getId(), 1); carts.add(customer, cart, second.getId(), 2);
        products.save(admin, second.getId(), second.getName(), second.getCategory(), second.getPrice(), 1, true);
        assertThrows(OutOfStockException.class, () -> checkout.checkout(customer, cart, "123 Sample Street", new CashOnDeliveryPayment()));
        assertEquals(product.getStock(), products.get(product.getId()).getStock()); assertTrue(orders.history(customer).isEmpty());
        assertEquals(2, cart.getItems().size());
    }
    @Test void databaseWriteFailureRollsBackEntireCheckout() throws SQLException {
        carts.add(customer, cart, product.getId(), 1);
        try (Connection c = database.open(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TRIGGER reject_order BEFORE INSERT ON orders BEGIN SELECT RAISE(ABORT, 'test failure'); END");
        }
        assertThrows(StorageException.class, () -> checkout.checkout(customer, cart, "123 Sample Street", new CashOnDeliveryPayment()));
        assertEquals(product.getStock(), products.get(product.getId()).getStock());
        assertEquals(1, cart.getItems().size()); assertTrue(orders.history(customer).isEmpty());
    }
    @Test void checkoutRejectsEmptyCartAddressAndMissingPayment() {
        assertThrows(ValidationException.class, () -> checkout.checkout(customer, cart, "123 Sample Street", new CashOnDeliveryPayment()));
        carts.add(customer, cart, product.getId(), 1);
        assertThrows(ValidationException.class, () -> checkout.checkout(customer, cart, "", new CashOnDeliveryPayment()));
        assertThrows(ValidationException.class, () -> checkout.checkout(customer, cart, "123 Sample Street", null));
        assertTrue(orders.history(customer).isEmpty());
    }
    @Test void stalePriceRequiresCustomerReview() {
        carts.add(customer, cart, product.getId(), 1);
        products.save(admin, product.getId(), product.getName(), product.getCategory(), new BigDecimal("99.99"), product.getStock(), true);
        assertThrows(ValidationException.class, () -> checkout.checkout(customer, cart, "123 Sample Street", new CashOnDeliveryPayment()));
        assertTrue(orders.history(customer).isEmpty());
        carts.update(customer, cart, product.getId(), 1);
        assertEquals(new BigDecimal("99.99"), cart.getTotal());
    }
    @Test void deactivatedCartItemCannotBePurchased() {
        carts.add(customer, cart, product.getId(), 1);
        products.save(admin, product.getId(), product.getName(), product.getCategory(), product.getPrice(), product.getStock(), false);
        assertThrows(ValidationException.class, () -> checkout.checkout(customer, cart, "123 Sample Street", new CashOnDeliveryPayment()));
        assertEquals(product.getStock(), products.get(product.getId()).getStock());
    }
    @Test void orderSnapshotsSurviveProductEditsAndReopeningDatabase() {
        carts.add(customer, cart, product.getId(), 1);
        checkout.checkout(customer, cart, "123 Sample Street", new CashOnDeliveryPayment());
        products.save(admin, product.getId(), "Changed name", product.getCategory(), new BigDecimal("999.00"), 50, false);
        DatabaseManager reopened = new DatabaseManager(temp.resolve("test.db")); reopened.initialize();
        Order saved = new OrderService(reopened, new OrderRepository()).history(customer).getFirst();
        assertEquals(product.getName(), saved.items().getFirst().productName());
        assertEquals(product.getPrice(), saved.items().getFirst().unitPrice());
        assertEquals(50, new ProductService(reopened, new ProductRepository()).get(product.getId()).getStock());
    }
    @Test void historyDoesNotExposeOtherCustomersOrders() {
        carts.add(customer, cart, product.getId(), 1);
        checkout.checkout(customer, cart, "123 Sample Street", new CashOnDeliveryPayment());
        assertTrue(orders.history(new Customer(999, "other")).isEmpty());
        assertThrows(ValidationException.class, () -> orders.history(admin));
    }

    @Test void reviewsRequireACompletedPurchaseAndAreVisibleInTheCatalog() {
        ProductReviewService reviewService = new ProductReviewService(database, new ProductReviewRepository());
        assertFalse(reviewService.canReview(customer, product.getId()));
        assertThrows(ValidationException.class, () -> reviewService.submit(customer, product.getId(), 5, "Great product"));
        carts.add(customer, cart, product.getId(), 1);
        checkout.checkout(customer, cart, "123 Sample Street", new CashOnDeliveryPayment());
        assertTrue(reviewService.canReview(customer, product.getId()));
        reviewService.submit(customer, product.getId(), 4, "Works well and feels sturdy.");
        assertEquals(1, reviewService.list(product.getId()).size());
        assertEquals(4, reviewService.list(product.getId()).getFirst().rating());
        assertEquals("Works well and feels sturdy.", reviewService.list(product.getId()).getFirst().text());
        assertThrows(ValidationException.class, () -> reviewService.submit(admin, product.getId(), 5, "Admin should not review"));
        assertThrows(ValidationException.class, () -> reviewService.submit(customer, product.getId(), 6, "Invalid rating"));
    }

    @Test void adminLowStockReportListsProductsAndRequiresAdminRole() {
        products.save(admin, product.getId(), product.getName(), product.getCategory(), product.getPrice(), 2, true);
        AdminService reports = new AdminService(database, new AdminRepository());
        assertTrue(reports.lowStock(admin).stream().anyMatch(row -> row.id() == product.getId() && row.stock() == 2));
        assertThrows(ValidationException.class, () -> reports.lowStock(customer));
    }

    private byte[] testPhoto(String format, int width, int height) throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_RGB),format,output);
        return output.toByteArray();
    }
    @Test void photoAndDescriptionSurviveReopenReplacementAndRemoval() throws Exception {
        byte[] first=testPhoto("png",32,24), second=testPhoto("jpeg",20,30);
        Path source=temp.resolve("photo.png");java.nio.file.Files.write(source,first);
        products.save(admin,product.getId(),product.getName(),product.getCategory(),product.getPrice(),product.getStock(),true,
            "An admin-written description",ProductPhotos.read(source));
        java.nio.file.Files.delete(source);
        var reopened=new ProductService(new DatabaseManager(temp.resolve("test.db")),new ProductRepository());
        assertArrayEquals(first,reopened.get(product.getId()).getPhoto());
        assertEquals("An admin-written description",reopened.get(product.getId()).getDescription());
        products.save(admin,product.getId(),product.getName(),product.getCategory(),product.getPrice(),product.getStock(),true,"Updated",second);
        assertArrayEquals(second,reopened.get(product.getId()).getPhoto());
        products.save(admin,product.getId(),product.getName(),product.getCategory(),product.getPrice(),product.getStock(),true,"Updated",null);
        assertNull(reopened.get(product.getId()).getPhoto());
    }
    @Test void invalidPhotosCannotReplaceSavedData() throws Exception {
        byte[] good=testPhoto("png",10,10);
        products.save(admin,product.getId(),product.getName(),product.getCategory(),product.getPrice(),product.getStock(),true,"Valid",good);
        assertThrows(ValidationException.class,()->products.save(admin,product.getId(),"Bad edit",product.getCategory(),product.getPrice(),1,true,"Changed",new byte[]{1,2,3}));
        assertEquals(product.getName(),products.get(product.getId()).getName());
        assertArrayEquals(good,products.get(product.getId()).getPhoto());
        assertThrows(ValidationException.class,()->ProductPhotos.validate(new byte[0]));
        assertThrows(ValidationException.class,()->ProductPhotos.validate(new byte[ProductPhotos.MAX_BYTES+1]));
        assertThrows(ValidationException.class,()->ProductPhotos.validate(testPhoto("png",4097,1)));
        assertThrows(ValidationException.class,()->ProductPhotos.validate(testPhoto("gif",10,10)));
        Path fake=temp.resolve("fake.jpg");java.nio.file.Files.writeString(fake,"not an image");
        assertThrows(ValidationException.class,()->ProductPhotos.read(fake));
        assertThrows(ValidationException.class,()->ProductPhotos.read(temp.resolve("missing.png")));
    }
    @Test void photoBytesAreEncapsulatedAndBasicEditPreservesPhoto() throws Exception {
        byte[] bytes=testPhoto("png",10,10), expected=bytes.clone();
        Product p=new Product(0,"Photo",Category.PC_PARTS,BigDecimal.ONE,2,true,"Description",bytes);
        bytes[0]=0;p.getPhoto()[1]=0;
        assertArrayEquals(expected,p.getPhoto());
        products.save(admin,product.getId(),product.getName(),product.getCategory(),product.getPrice(),3,true,"Description",expected);
        products.save(admin,product.getId(),product.getName(),product.getCategory(),product.getPrice(),4,true);
        assertArrayEquals(expected,products.get(product.getId()).getPhoto());
        assertEquals("Description",products.get(product.getId()).getDescription());
    }
    @Test void photosAndDescriptionsRequireAdminAccessAndValidDescription() throws Exception {
        byte[] photo=testPhoto("png",2,2);
        assertThrows(ValidationException.class,()->products.save(customer,product.getId(),"Edit",Category.PC_PARTS,BigDecimal.ONE,1,true,"",photo));
        assertThrows(ValidationException.class,()->products.save(new Guest(),product.getId(),"Edit",Category.PC_PARTS,BigDecimal.ONE,1,true,"",photo));
        assertThrows(ValidationException.class,()->products.save(admin,product.getId(),"Edit",Category.PC_PARTS,BigDecimal.ONE,1,true,"x".repeat(1001),photo));
    }
    @Test void existingSchemaMigratesWithoutLosingProducts() throws Exception {
        DatabaseManager legacy=new DatabaseManager(temp.resolve("legacy.db"));
        try(Connection c=legacy.open();Statement s=c.createStatement()){
            s.executeUpdate("CREATE TABLE products(id INTEGER PRIMARY KEY,name TEXT NOT NULL,category TEXT NOT NULL,price_cents INTEGER NOT NULL,stock INTEGER NOT NULL,active INTEGER NOT NULL)");
            s.executeUpdate("INSERT INTO products VALUES(99,'Existing product','HOME',1234,7,1)");
        }
        legacy.initialize();legacy.initialize();
        Product saved=new ProductService(legacy,new ProductRepository()).get(99);
        assertEquals("Existing product",saved.getName());assertEquals(7,saved.getStock());assertEquals("",saved.getDescription());assertNull(saved.getPhoto());assertEquals("",saved.getSpecifications());assertTrue(saved.getAdditionalPhotos().isEmpty());
    }
    @Test void guestCanBrowseAndBuildCartButCannotCheckoutOrReadOrders() {
        User guest=new Guest();Cart guestCart=new Cart(0);
        assertEquals(6,products.search(guest,"",null).size());
        carts.add(guest,guestCart,product.getId(),2);
        assertThrows(ValidationException.class,()->checkout.checkout(guest,guestCart,"123 Main Street",new CashOnDeliveryPayment()));
        assertThrows(ValidationException.class,()->orders.history(guest));
        Cart transferred=carts.transferGuest(guestCart,customer);
        assertEquals(customer.getId(),transferred.getCustomerId());assertEquals(2,transferred.getItems().getFirst().getQuantity());
        assertThrows(ValidationException.class,()->carts.transferGuest(guestCart,admin));
        assertThrows(ValidationException.class,()->carts.add(guest,transferred,product.getId(),1));
    }
    @Test void guestTransferNeverSilentlyChangesPriceAndCheckoutRevalidates() {
        Cart guest=new Cart(0);carts.add(new Guest(),guest,product.getId(),2);
        products.save(admin,product.getId(),product.getName(),product.getCategory(),product.getPrice().add(BigDecimal.ONE),product.getStock(),true);
        Cart transferred=carts.transferGuest(guest,customer);
        assertEquals(product.getPrice(),transferred.getItems().getFirst().getProduct().getPrice());
        assertTrue(carts.review(transferred).contains("changed"));
        assertThrows(ValidationException.class,()->checkout.checkout(customer,transferred,"123 Main Street",new CashOnDeliveryPayment()));
        carts.update(customer,transferred,product.getId(),2);
        assertDoesNotThrow(()->checkout.checkout(customer,transferred,"123 Main Street",new CashOnDeliveryPayment()));
    }


    @Test void deliveryDetailsAreValidatedOutsideTheGui() {
        DeliveryAddress address=new DeliveryAddress(" Demo Customer ","123 Main Street","Demo City","12345");
        assertEquals("Demo Customer\n123 Main Street\nDemo City 12345",address.formatted());
        assertThrows(ValidationException.class,()->new DeliveryAddress("","123 Main Street","City","12345"));
        assertThrows(ValidationException.class,()->new DeliveryAddress("Name","abc","City","12345"));
        assertThrows(ValidationException.class,()->new DeliveryAddress("Name","123 Main Street","","12345"));
        assertThrows(ValidationException.class,()->new DeliveryAddress("Name","123 Main Street","City","!"));
        assertThrows(ValidationException.class,()->new DeliveryAddress("N".repeat(80),"S".repeat(200),"City","12345"));
    }


    @Test void catalogFiltersCombineAvailabilityCategoryAndBudget() {
        Product lamp=products.get(6);
        products.save(admin,6,lamp.getName(),lamp.getCategory(),lamp.getPrice(),0,true);
        assertEquals(4,products.search(new Guest(),"",null,false,new BigDecimal("50")).size());
        assertEquals(3,products.search(new Guest(),"",null,true,new BigDecimal("50")).size());
        assertEquals(1,products.search(new Guest(),"",Category.PC_PARTS,true,new BigDecimal("50")).size());
        assertTrue(products.search(new Guest(),"keyboard",null,true,new BigDecimal("50")).isEmpty());
        assertThrows(ValidationException.class,()->products.search(customer,"",null,true,new BigDecimal("-1")));
    }


    @Test void gallerySpecificationsPersistAndBasicEditsPreserveThem() throws Exception {
        byte[] main=testPhoto("png",10,10),angle=testPhoto("png",12,8);
        products.save(admin,0,"Gallery product",Category.PC_PARTS,BigDecimal.TEN,3,true,"Description",main,java.util.List.of(angle),"Material: Cotton");
        Product saved=products.search(admin,"Gallery product",null).getFirst();
        assertEquals(1,saved.getAdditionalPhotos().size());assertArrayEquals(angle,saved.getAdditionalPhotos().getFirst());
        products.save(admin,saved.getId(),saved.getName(),saved.getCategory(),saved.getPrice(),4,true);
        database.initialize();Product reopened=products.get(saved.getId());
        assertEquals("Material: Cotton",reopened.getSpecifications());assertArrayEquals(main,reopened.getPhoto());assertArrayEquals(angle,reopened.getAdditionalPhotos().getFirst());
        products.save(admin,saved.getId(),saved.getName(),saved.getCategory(),saved.getPrice(),4,true,"Description",main,java.util.List.of(),"");
        assertTrue(products.get(saved.getId()).getAdditionalPhotos().isEmpty());assertEquals("",products.get(saved.getId()).getSpecifications());
    }
    @Test void galleryPhotoBytesAreDefensiveCopies() throws Exception {
        byte[] main=testPhoto("png",10,10),angle=testPhoto("png",8,12),expected=angle.clone();
        Product p=new Product(0,"Gallery",Category.PC_PARTS,BigDecimal.ONE,1,true,"",main,java.util.List.of(angle),"");
        angle[0]=0;p.getAdditionalPhotos().getFirst()[1]=0;assertArrayEquals(expected,p.getAdditionalPhotos().getFirst());
    }
    @Test void invalidGalleryContentCannotOverwriteSavedProduct() throws Exception {
        byte[] main=testPhoto("png",10,10);
        assertThrows(ValidationException.class,()->products.save(admin,product.getId(),"Invalid",Category.PC_PARTS,BigDecimal.ONE,1,true,"",main,java.util.List.of(new byte[]{1,2,3}),""));
        assertEquals(product.getName(),products.get(product.getId()).getName());
        assertThrows(ValidationException.class,()->products.save(admin,product.getId(),"Invalid",Category.PC_PARTS,BigDecimal.ONE,1,true,"",main,java.util.Collections.nCopies(5,main),""));
        assertThrows(ValidationException.class,()->products.save(admin,product.getId(),"Invalid",Category.PC_PARTS,BigDecimal.ONE,1,true,"",null,java.util.List.of(main),""));
        assertThrows(ValidationException.class,()->products.save(admin,product.getId(),"Invalid",Category.PC_PARTS,BigDecimal.ONE,1,true,"",main,java.util.List.of(),"x".repeat(1001)));
    }
    @Test void galleryChangesRequireAdministratorAccess() throws Exception {
        byte[] main=testPhoto("png",10,10);
        for(User actor:java.util.List.of(customer,new Guest()))assertThrows(ValidationException.class,()->
            products.save(actor,product.getId(),"Unauthorized",Category.PC_PARTS,BigDecimal.ONE,1,true,"",main,java.util.List.of(main),"Material: Cotton"));
        assertEquals(product.getName(),products.get(product.getId()).getName());
    }
    @Test void failedGallerySaveRollsBackProductAndPreviousAngles() throws Exception {
        byte[] main=testPhoto("png",10,10),angle=testPhoto("png",8,12);
        products.save(admin,product.getId(),product.getName(),product.getCategory(),product.getPrice(),product.getStock(),true,"Original",main,java.util.List.of(angle),"Original spec");
        try(Connection c=database.open();Statement statement=c.createStatement()) {
            statement.executeUpdate("CREATE TRIGGER reject_gallery BEFORE INSERT ON product_photos BEGIN SELECT RAISE(ABORT,'test failure'); END");
        }
        assertThrows(StorageException.class,()->products.save(admin,product.getId(),"Changed",Category.PC_PARTS,BigDecimal.TEN,1,true,"Changed",angle,java.util.List.of(main),"Changed spec"));
        Product restored=products.get(product.getId());assertEquals(product.getName(),restored.getName());assertEquals("Original spec",restored.getSpecifications());
        assertArrayEquals(main,restored.getPhoto());assertArrayEquals(angle,restored.getAdditionalPhotos().getFirst());
    }
}
