package edu.course.shop.service;

import edu.course.shop.model.Category;
import edu.course.shop.persistence.DatabaseManager;
import edu.course.shop.persistence.ProductRepository;
import edu.course.shop.persistence.UserRepository;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class VisualProductSearchTest {
    @TempDir Path temp;

    @Test void ranksProductsByPhotoColorAndSkipsProductsWithoutPhotos() throws Exception {
        DatabaseManager db = new DatabaseManager(temp.resolve("visual.db")); db.initialize();
        ProductRepository repository = new ProductRepository(); ProductService catalog = new ProductService(db, repository);
        var auth = new AuthService(db, new UserRepository()); var admin = auth.login("admin", "admin123"); var customer = auth.login("customer", "customer123");
        byte[] red = image(Color.RED), blue = image(Color.BLUE);
        catalog.save(admin, 0, "Red case", Category.PC_PARTS, BigDecimal.TEN, 3, true, "", red);
        catalog.save(admin, 0, "Blue case", Category.PC_PARTS, BigDecimal.TEN, 3, true, "", blue);
        catalog.save(admin, 0, "No image item", Category.PC_PARTS, BigDecimal.TEN, 3, true, "", null);

        List<edu.course.shop.model.Product> matches = new VisualProductSearch().rank(red, catalog.search(customer, "", null));

        assertEquals("Red case", matches.getFirst().getName());
        assertEquals(2, matches.size());
        assertTrue(matches.stream().noneMatch(p -> p.getName().equals("No image item")));
    }

    private static byte[] image(Color color) throws Exception {
        BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) image.setRGB(x, y, color.getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output); return output.toByteArray();
    }
}
