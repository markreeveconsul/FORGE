package edu.course.shop.service;

import com.google.gson.*;
import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class GeminiAssistantTest {
    @TempDir Path temp;
    private static final String GOOD = """
        {"candidates":[{"finishReason":"STOP","content":{"parts":[
        {"thought":true,"text":"internal reasoning"},{"text":"Check the recorded specifications."}]}}]}
        """;
    @Test void missingKeyAndUnconfirmedBillingNeverSend() {
        AtomicInteger calls = new AtomicInteger();
        for (var config : List.of(new GeminiAssistant.Config("", null, true), new GeminiAssistant.Config("secret", null, false))) {
            var client = new GeminiAssistant(config, r -> { calls.incrementAndGet(); return new GeminiAssistant.Reply(200, GOOD); });
            assertThrows(IllegalStateException.class, () -> client.answer("Help", new JsonObject()));
            assertFalse(config.toString().contains("secret"));
        }
        assertEquals(0, calls.get());
    }
    @Test void keyStaysOutOfUrlAndRepeatedRequestsAreLimited() {
        AtomicInteger calls = new AtomicInteger();
        var client = new GeminiAssistant(new GeminiAssistant.Config("secret", null, true), request -> {
            calls.incrementAndGet();
            assertEquals("generativelanguage.googleapis.com", request.uri().getHost());
            assertEquals("https", request.uri().getScheme());
            assertFalse(request.uri().toString().contains("secret"));
            assertEquals("secret", request.headers().firstValue("x-goog-api-key").orElseThrow());
            assertTrue(request.timeout().isPresent());
            return new GeminiAssistant.Reply(200, GOOD);
        });
        assertEquals("Check the recorded specifications.", client.answer("Help", new JsonObject()));
        assertThrows(IllegalStateException.class, () -> client.answer("Again", new JsonObject()));
        assertEquals(1, calls.get());
    }
    @Test void blockedTruncatedMalformedAndProviderErrorsAreNotAnswers() {
        for (String body : List.of("{}", "not json", "{\"candidates\":[]}",
            "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}",
            GOOD.replace("STOP", "MAX_TOKENS"))) {
            assertThrows(IllegalStateException.class, () -> GeminiAssistant.parse(new GeminiAssistant.Reply(200, body)));
        }
        for (int code : List.of(400, 401, 403, 404, 429, 500)) {
            var error = assertThrows(IllegalStateException.class, () -> GeminiAssistant.parse(new GeminiAssistant.Reply(code, "secret-provider-debug")));
            assertFalse(error.getMessage().contains("secret-provider-debug"));
        }
    }
    @Test void imageSearchSendsReferenceAndCatalogImagesAndAcceptsOnlyStrongKnownMatches() throws Exception {
        byte[] photo = image(Color.ORANGE);
        Product gpu = new Product(41,"GPU candidate",Category.PC_PARTS,BigDecimal.TEN,4,true,"",photo);
        Product unrelated = new Product(42,"Laptop candidate",Category.LAPTOPS,BigDecimal.TEN,4,true,"",photo);
        AtomicInteger calls = new AtomicInteger();
        JsonObject answer=new JsonObject();answer.addProperty("identifiedItem","graphics card");JsonArray matches=new JsonArray();
        for(Object[] row:List.of(new Object[]{41L,93,"Same GPU form"},new Object[]{42L,54,"Weak color match"},new Object[]{999L,99,"Unknown"})){
            JsonObject match=new JsonObject();match.addProperty("productId",(Long)row[0]);match.addProperty("confidence",(Integer)row[1]);match.addProperty("reason",(String)row[2]);matches.add(match);
        }
        answer.add("matches",matches);JsonObject part=new JsonObject();part.addProperty("text",answer.toString());
        JsonObject content=new JsonObject();content.add("parts",new JsonArray());content.getAsJsonArray("parts").add(part);
        JsonObject candidate=new JsonObject();candidate.add("content",content);JsonObject wrapper=new JsonObject();JsonArray candidates=new JsonArray();candidates.add(candidate);wrapper.add("candidates",candidates);
        var client = new GeminiAssistant(new GeminiAssistant.Config("secret",null,true), request -> {
            calls.incrementAndGet();
            assertEquals("https",request.uri().getScheme());
            assertEquals("secret",request.headers().firstValue("x-goog-api-key").orElseThrow());
            assertTrue(request.timeout().isPresent());
            return new GeminiAssistant.Reply(200,wrapper.toString());
        });
        var result = client.searchByImage(photo,List.of(gpu,unrelated));
        assertEquals("graphics card",result.identifiedItem());
        assertEquals(1,result.matches().size());
        assertEquals(41,result.matches().getFirst().productId());
        assertEquals(93,result.matches().getFirst().confidence());
        assertEquals(1,calls.get());
    }
    @Test void imageSearchReportsGeminiRequestRejectionReason() throws Exception {
        Product candidate=new Product(51,"GPU candidate",Category.PC_PARTS,BigDecimal.TEN,4,true,"",image(Color.ORANGE));
        var client=new GeminiAssistant(new GeminiAssistant.Config("secret",null,true),request->
            new GeminiAssistant.Reply(400,"{\"error\":{\"message\":\"invalid image part\"}}"));
        var error=assertThrows(IllegalStateException.class,()->client.searchByImage(image(Color.BLUE),List.of(candidate)));
        assertTrue(error.getMessage().contains("invalid image part"));
    }
    @Test void orderProjectionExcludesPrivateFieldsAndRejectsOtherCustomers() {
        DatabaseManager db = new DatabaseManager(temp.resolve("orders.db")); db.initialize();
        var auth = new AuthService(db, new UserRepository());
        User customer = auth.login("customer", "customer123");
        var order = new Order(17, customer.getId(), "2026-10-20", "PRIVATE ADDRESS", "PRIVATE METHOD", "APPROVED",
            new BigDecimal("10"), List.of(new OrderItem(1, "PRIVATE PURCHASE NAME", BigDecimal.TEN, 1)));
        String data = AssistantContext.order(customer, order).toString();
        assertFalse(data.contains("PRIVATE")); assertFalse(data.contains(customer.getUsername()));
        assertTrue(data.contains("Simulated")); assertTrue(data.contains("APPROVED"));
        assertThrows(IllegalArgumentException.class, () -> AssistantContext.order(new Guest(), order));
        assertThrows(IllegalArgumentException.class, () -> AssistantContext.order(auth.login("admin", "admin123"), order));
        var other = new Order(18, customer.getId() + 99, "date", "address", "method", "APPROVED", BigDecimal.TEN, List.of());
        assertThrows(IllegalArgumentException.class, () -> AssistantContext.order(customer, other));
    }
    private static byte[] image(Color color) throws Exception {
        BufferedImage image=new BufferedImage(16,12,BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();try{graphics.setColor(color);graphics.fillRect(0,0,16,12);}finally{graphics.dispose();}
        ByteArrayOutputStream output=new ByteArrayOutputStream();ImageIO.write(image,"png",output);return output.toByteArray();
    }
    @Test void cartReviewUsesCurrentStockAndPricesWithoutMutatingCart() {
        DatabaseManager db = new DatabaseManager(temp.resolve("cart.db")); db.initialize();
        var auth = new AuthService(db, new UserRepository()); User admin = auth.login("admin", "admin123");
        var products = new ProductService(db, new ProductRepository());
        products.save(admin, 0, "AI fixture", Category.PC_PARTS, BigDecimal.TEN, 5, true);
        Product original = products.search(new Guest(), "AI fixture", null).getFirst();
        Cart cart = new Cart(0); cart.add(original, 3);
        products.save(admin, original.getId(), original.getName(), original.getCategory(), new BigDecimal("12"), 1, true);
        JsonObject data = AssistantContext.cart(cart.getItems(), products);
        assertEquals(2, data.getAsJsonArray("verifiedWarnings").size());
        assertEquals(0, new BigDecimal("30").compareTo(data.get("cartSnapshotTotalUSD").getAsBigDecimal()));
        assertEquals(3, cart.getItems().getFirst().getQuantity());
        assertEquals(0, BigDecimal.TEN.compareTo(cart.getItems().getFirst().getProduct().getPrice()));
        assertThrows(IllegalArgumentException.class, () -> AssistantContext.cart(List.of(), products));
    }
}
