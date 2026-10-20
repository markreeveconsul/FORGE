package edu.course.shop;

import edu.course.shop.exception.ValidationException;
import edu.course.shop.model.*;
import edu.course.shop.payment.CashOnDeliveryPayment;
import edu.course.shop.persistence.*;
import edu.course.shop.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TechCategoriesTest {
    @TempDir Path temp;
    private byte[] photo() throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        return bytes.toByteArray();
    }
    private Services services(DatabaseManager db) {
        ProductRepository products=new ProductRepository();OrderRepository orders=new OrderRepository();
        return new Services(new AuthService(db,new UserRepository()),new ProductService(db,products),new CheckoutService(db,products,orders),new OrderService(db,orders),new edu.course.shop.service.AdminService(db,new edu.course.shop.persistence.AdminRepository()),new edu.course.shop.service.ProductReviewService(db,new edu.course.shop.persistence.ProductReviewRepository()));
    }
    @Test void migrationRetainsLegacyContentSavedLinksAndHistoricalReceiptsUntilReplacement() throws Exception {
        DatabaseManager db=new DatabaseManager(temp.resolve("legacy.db"));db.initialize();Services s=services(db);
        User admin=s.auth().login("admin","admin123"),customer=s.auth().login("customer","customer123");
        byte[] bytes=photo();BigDecimal price=new BigDecimal("77.25");PhotoFrame frame=new PhotoFrame(PhotoFrame.Mode.COVER,.2,.7);
        s.products().save(admin,3,"Original purchase name",Category.PC_PARTS,price,7,true,"Original description",bytes,List.of(bytes),"Original: Spec",frame);
        s.accounts().saveItem(customer,3,true);Cart cart=new Cart(customer.getId());new CartService(s.products()).add(customer,cart,3,1);
        Order receipt=s.checkout().checkout(customer,cart,"123 Test Street",new CashOnDeliveryPayment());
        // Emulate a pre-upgrade database row without modifying photos, saved items or historical lines.
        try(Connection c=db.open();Statement statement=c.createStatement()){statement.executeUpdate("UPDATE products SET category='CLOTHING',active=1 WHERE id=3");}
        db.initialize();db.initialize();
        Product kept=s.products().get(3);assertFalse(kept.isActive());assertEquals(Category.CLOTHING,kept.getCategory());
        assertEquals("Original purchase name",kept.getName());assertEquals(price,kept.getPrice());assertEquals(6,kept.getStock());assertEquals("Original description",kept.getDescription());
        assertArrayEquals(bytes,kept.getPhoto());assertArrayEquals(bytes,kept.getAdditionalPhotos().getFirst());assertEquals("Original: Spec",kept.getSpecifications());assertEquals(frame,kept.getPhotoFrame());
        assertTrue(s.accounts().saved(customer).contains(3L));assertTrue(s.products().search(customer,"Original purchase name",null).isEmpty());
        assertEquals(1,s.products().search(admin,"Original purchase name",Category.CLOTHING).size());
        Order history=s.orders().history(customer).getFirst();assertEquals(receipt.id(),history.id());assertEquals("Original purchase name",history.items().getFirst().productName());assertEquals(price,history.items().getFirst().unitPrice());
        s.products().save(admin,3,"Replacement laptop",Category.LAPTOPS,new BigDecimal("899.00"),4,true,"Updated product",bytes,List.of(),"");
        db.initialize();assertTrue(s.products().get(3).isActive());assertEquals(1,s.products().search(customer,"Replacement laptop",Category.LAPTOPS).size());
        assertEquals("Original purchase name",s.orders().history(customer).getFirst().items().getFirst().productName());
    }
    @Test void allSixTechCategoriesRoundTripAndExistingInventoryIsNotDeleted() {
        DatabaseManager db=new DatabaseManager(temp.resolve("tech.db"));db.initialize();Services s=services(db);
        User admin=s.auth().login("admin","admin123"),customer=s.auth().login("customer","customer123");
        assertEquals(6,s.products().search(admin,"",null).size());assertEquals(2,s.products().search(customer,"",null).size());
        assertFalse(s.products().get(4).isActive());assertEquals("Canvas tote",s.products().get(4).getName());
        List<Category> categories=List.of(Category.PC_SETS,Category.PC_PARTS,Category.LAPTOPS,Category.PHONES_TABLETS,Category.PERIPHERALS,Category.ELECTRONICS);
        for(Category category:categories)s.products().save(admin,0,"Roundtrip "+category.name(),category,BigDecimal.TEN,3,true);
        db.initialize();
        for(Category category:categories){List<Product> matches=s.products().search(customer,"Roundtrip "+category.name(),category);assertEquals(1,matches.size());assertEquals(category,matches.getFirst().getCategory());}
        assertEquals(12,s.products().search(admin,"",null).size());
        assertTrue(s.products().search(customer,"",Category.HOME).isEmpty());assertTrue(s.products().search(new Guest(),"",Category.CLOTHING).isEmpty());
    }
    @Test void retiredItemsCannotBeRepublishedSavedOrPurchasedEvenOutsideUi() throws Exception {
        DatabaseManager db=new DatabaseManager(temp.resolve("gates.db"));db.initialize();Services s=services(db);
        User admin=s.auth().login("admin","admin123"),customer=s.auth().login("customer","customer123");
        Product retired=s.products().get(3);
        assertThrows(ValidationException.class,()->s.products().save(admin,3,retired.getName(),Category.CLOTHING,retired.getPrice(),retired.getStock(),true));
        assertThrows(ValidationException.class,()->s.products().save(admin,0,"New retired item",Category.HOME,BigDecimal.TEN,1,false));
        assertDoesNotThrow(()->s.products().save(admin,3,retired.getName(),Category.CLOTHING,retired.getPrice(),retired.getStock(),false,"Kept for replacement",null));
        Product headphone=s.products().get(1);Cart cart=new Cart(customer.getId());new CartService(s.products()).add(customer,cart,1,1);
        try(Connection c=db.open();Statement statement=c.createStatement()){statement.executeUpdate("UPDATE products SET category='HOME',active=1 WHERE id=1");}
        assertTrue(s.products().search(customer,"headphones",null).isEmpty());
        assertThrows(ValidationException.class,()->new CartService(s.products()).add(new Guest(),new Cart(0),1,1));
        assertThrows(ValidationException.class,()->s.accounts().saveItem(customer,1,true));
        assertThrows(ValidationException.class,()->s.checkout().checkout(customer,cart,"123 Test Street",new CashOnDeliveryPayment()));
        assertEquals(headphone.getStock(),s.products().get(1).getStock());assertEquals(1,cart.getItems().size());assertTrue(s.orders().history(customer).isEmpty());
    }
}
