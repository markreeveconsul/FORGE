package edu.course.shop.service;

import com.google.gson.*;
import edu.course.shop.model.*;
import java.math.BigDecimal;
import java.util.List;

/** Explicit projections: never serialize User, Order, or Product directly to a cloud service. */
public final class AssistantContext {
    private AssistantContext() { }
    public static JsonObject product(Product p) {
        JsonObject data = new JsonObject();
        data.addProperty("productId", p.getId()); data.addProperty("name", p.getName());
        data.addProperty("category", p.getCategory().label()); data.addProperty("priceUSD", p.getPrice());
        data.addProperty("stock", p.getStock()); data.addProperty("active", p.isActive());
        data.addProperty("description", p.getDescription()); data.addProperty("specifications", p.getSpecifications());
        return data;
    }
    public static JsonObject cart(List<CartItem> items, ProductService products) {
        if (items.isEmpty()) throw new IllegalArgumentException("Add a product to your cart first.");
        if (items.size() > 20) throw new IllegalArgumentException("Review up to 20 distinct cart items at a time.");
        JsonObject result = new JsonObject(); JsonArray lines = new JsonArray(), warnings = new JsonArray();
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : items) {
            Product current = products.get(item.getProduct().getId());
            JsonObject line = new JsonObject();
            line.addProperty("name", item.getProduct().getName()); line.addProperty("quantity", item.getQuantity());
            line.addProperty("cartUnitPriceUSD", item.getProduct().getPrice());
            total = total.add(item.getSubtotal());
            if (current == null || !current.isActive() || current.getCategory().isRetired()) {
                warnings.add(item.getProduct().getName() + " is no longer available.");
            } else {
                line.add("currentCatalogRecord", product(current));
                if (current.getStock() < item.getQuantity()) warnings.add(current.getName() + ": quantity exceeds current stock.");
                if (current.getPrice().compareTo(item.getProduct().getPrice()) != 0) warnings.add(current.getName() + ": price changed; review at checkout.");
            }
            lines.add(line);
        }
        result.add("items", lines); result.add("verifiedWarnings", warnings);
        result.addProperty("cartSnapshotTotalUSD", total);
        result.addProperty("compatibility", "Not certified. Missing technical specifications must be checked.");
        return result;
    }
    public static JsonObject order(User user, Order order) {
        if (!(user instanceof Customer) || order.customerId() != user.getId())
            throw new IllegalArgumentException("Only your own customer orders can be explained.");
        JsonObject data = new JsonObject();
        data.addProperty("orderId", order.id()); data.addProperty("createdAt", order.createdAt());
        data.addProperty("paymentStatus", order.paymentStatus()); data.addProperty("totalUSD", order.total());
        data.addProperty("itemQuantity", order.items().stream().mapToInt(OrderItem::quantity).sum());
        data.addProperty("delivery", "Course demo. No real delivery, shipment status or tracking is available.");
        data.addProperty("payment", "Simulated payment only; no real money charged.");
        return data;
    }
}
