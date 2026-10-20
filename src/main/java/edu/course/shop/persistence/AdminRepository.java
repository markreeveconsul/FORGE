package edu.course.shop.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Read-only reporting queries for the administrator workspace. */
public final class AdminRepository {
    public record Summary(int activeProducts, int lowStockProducts, int orders, int customers, long recordedOrderCents) { }
    public record OrderRow(long id, String customer, String createdAt, String paymentMethod,
                           String paymentStatus, int itemCount, long totalCents) { }
    public record CustomerRow(long id, String username, String displayName, int orderCount, long orderCents) { }
    public record LowStockRow(long id, String name, String category, int stock) { }

    public Summary summary(Connection c) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("""
            SELECT
              (SELECT COUNT(*) FROM products WHERE active=1) AS active_products,
              (SELECT COUNT(*) FROM products WHERE active=1 AND stock<=5) AS low_stock,
              (SELECT COUNT(*) FROM orders) AS orders,
              (SELECT COUNT(*) FROM users WHERE role='CUSTOMER') AS customers,
              COALESCE((SELECT SUM(total_cents) FROM orders),0) AS order_cents
            """); ResultSet r = p.executeQuery()) {
            r.next();
            return new Summary(r.getInt("active_products"), r.getInt("low_stock"), r.getInt("orders"),
                r.getInt("customers"), r.getLong("order_cents"));
        }
    }

    public List<LowStockRow> lowStock(Connection c) throws SQLException {
        List<LowStockRow> rows = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("SELECT id,name,category,stock FROM products WHERE active=1 AND stock<=5 ORDER BY stock,name");
             ResultSet r = p.executeQuery()) {
            while (r.next()) rows.add(new LowStockRow(r.getLong("id"), r.getString("name"), r.getString("category"), r.getInt("stock")));
        }
        return List.copyOf(rows);
    }

    public List<OrderRow> orders(Connection c) throws SQLException {
        List<OrderRow> rows = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("""
            SELECT o.id, COALESCE(NULLIF(cp.display_name,''), u.username) AS customer,
                   o.created_at, o.payment_method, o.payment_status,
                   COALESCE(SUM(oi.quantity),0) AS item_count, o.total_cents
            FROM orders o
            JOIN users u ON u.id=o.customer_id
            LEFT JOIN customer_profiles cp ON cp.customer_id=u.id
            LEFT JOIN order_items oi ON oi.order_id=o.id
            GROUP BY o.id
            ORDER BY o.id DESC
            """); ResultSet r = p.executeQuery()) {
            while (r.next()) rows.add(new OrderRow(r.getLong("id"), r.getString("customer"),
                r.getString("created_at"), r.getString("payment_method"), r.getString("payment_status"),
                r.getInt("item_count"), r.getLong("total_cents")));
        }
        return List.copyOf(rows);
    }

    public List<CustomerRow> customers(Connection c) throws SQLException {
        List<CustomerRow> rows = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("""
            SELECT u.id, u.username, COALESCE(NULLIF(cp.display_name,''), u.username) AS display_name,
                   COUNT(o.id) AS order_count, COALESCE(SUM(o.total_cents),0) AS order_cents
            FROM users u
            LEFT JOIN customer_profiles cp ON cp.customer_id=u.id
            LEFT JOIN orders o ON o.customer_id=u.id
            WHERE u.role='CUSTOMER'
            GROUP BY u.id
            ORDER BY u.id DESC
            """); ResultSet r = p.executeQuery()) {
            while (r.next()) rows.add(new CustomerRow(r.getLong("id"), r.getString("username"),
                r.getString("display_name"), r.getInt("order_count"), r.getLong("order_cents")));
        }
        return List.copyOf(rows);
    }
}
