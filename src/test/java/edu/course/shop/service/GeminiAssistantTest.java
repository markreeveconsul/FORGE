package edu.course.shop.service;

import com.google.gson.*;
import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
