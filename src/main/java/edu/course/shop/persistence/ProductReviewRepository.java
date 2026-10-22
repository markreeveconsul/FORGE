package edu.course.shop.persistence;

import edu.course.shop.model.ProductReview;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ProductReviewRepository {
    public boolean hasPurchased(Connection c, long customerId, long productId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM orders o JOIN order_items i ON i.order_id=o.id WHERE o.customer_id=? AND i.product_id=? AND o.fulfillment_status<>'CANCELLED' LIMIT 1")) {
            p.setLong(1, customerId); p.setLong(2, productId);
            try (ResultSet r = p.executeQuery()) { return r.next(); }
        }
    }

    public List<ProductReview> list(Connection c, long productId) throws SQLException {
        List<ProductReview> reviews = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("SELECT r.id,r.product_id,r.customer_id,COALESCE(NULLIF(cp.display_name,''),u.username) AS author,r.rating,r.review,r.created_at FROM product_reviews r JOIN users u ON u.id=r.customer_id LEFT JOIN customer_profiles cp ON cp.customer_id=u.id WHERE r.product_id=? ORDER BY r.created_at DESC,r.id DESC")) {
            p.setLong(1, productId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) reviews.add(new ProductReview(r.getLong("id"), r.getLong("product_id"), r.getString("author"), r.getInt("rating"), r.getString("review"), r.getString("created_at")));
            }
        }
        return List.copyOf(reviews);
    }

    public void save(Connection c, long customerId, long productId, int rating, String text) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO product_reviews(product_id,customer_id,rating,review,created_at) VALUES(?,?,?,?,?) ON CONFLICT(product_id,customer_id) DO UPDATE SET rating=excluded.rating,review=excluded.review,created_at=excluded.created_at")) {
            p.setLong(1, productId); p.setLong(2, customerId); p.setInt(3, rating); p.setString(4, text); p.setString(5, Instant.now().toString()); p.executeUpdate();
        }
    }
}
