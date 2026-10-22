package edu.course.shop.persistence;
import edu.course.shop.model.*;
import java.sql.*;
import java.util.*;

public final class OrderRepository {
    public Order save(Connection c, Order order) throws SQLException {
        long id;
        try (PreparedStatement p = c.prepareStatement("INSERT INTO orders(customer_id,created_at,address,payment_method,payment_status,total_cents) VALUES(?,?,?,?,?,?)")) {
            p.setLong(1, order.customerId()); p.setString(2, order.createdAt()); p.setString(3, order.address());
            p.setString(4, order.paymentMethod()); p.setString(5, order.paymentStatus()); p.setLong(6, Money.cents(order.total())); p.executeUpdate();
        }
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT last_insert_rowid()")) { r.next(); id = r.getLong(1); }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO order_items(order_id,product_id,product_name,unit_price_cents,quantity) VALUES(?,?,?,?,?)")) {
            for (OrderItem item : order.items()) {
                p.setLong(1, id); p.setLong(2, item.productId()); p.setString(3, item.productName());
                p.setLong(4, Money.cents(item.unitPrice())); p.setInt(5, item.quantity()); p.addBatch();
            }
            p.executeBatch();
        }
        event(c,id,FulfillmentStatus.CONFIRMED,order.createdAt(),"Order confirmed",order.customerId());
        return find(c,id);
    }
    public List<Order> forCustomer(Connection c, long customerId) throws SQLException {
        List<Order> orders = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("SELECT * FROM orders WHERE customer_id=? ORDER BY id DESC")) {
            p.setLong(1, customerId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) orders.add(read(c,r));
            }
        }
        return orders;
    }
    public Order find(Connection c,long id)throws SQLException{
        try(var p=c.prepareStatement("SELECT * FROM orders WHERE id=?")){p.setLong(1,id);try(var r=p.executeQuery()){if(!r.next())throw new edu.course.shop.exception.ValidationException("Order no longer exists.");return read(c,r);}}
    }
    private Order read(Connection c,ResultSet r)throws SQLException{
        long id=r.getLong("id");List<OrderEvent> events=new ArrayList<>();
        try(var p=c.prepareStatement("SELECT * FROM order_events WHERE order_id=? ORDER BY id")){p.setLong(1,id);try(var history=p.executeQuery()){while(history.next())events.add(new OrderEvent(FulfillmentStatus.valueOf(history.getString("status")),history.getString("created_at"),history.getString("note")));}}
        if(events.isEmpty())events.add(new OrderEvent(FulfillmentStatus.valueOf(r.getString("fulfillment_status")),r.getString("created_at"),"Order recorded"));
        return new Order(id,r.getLong("customer_id"),r.getString("created_at"),r.getString("address"),r.getString("payment_method"),r.getString("payment_status"),Money.amount(r.getLong("total_cents")),items(c,id),FulfillmentStatus.valueOf(r.getString("fulfillment_status")),r.getString("carrier"),r.getString("tracking_number"),events);
    }
    public void event(Connection c,long id,FulfillmentStatus status,String time,String note,long actor)throws SQLException{
        try(var p=c.prepareStatement("INSERT INTO order_events(order_id,status,created_at,note,actor_id) VALUES(?,?,?,?,?)")){p.setLong(1,id);p.setString(2,status.name());p.setString(3,time);p.setString(4,note);p.setLong(5,actor);p.executeUpdate();}
    }
    private List<OrderItem> items(Connection c, long id) throws SQLException {
        List<OrderItem> items = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("SELECT * FROM order_items WHERE order_id=? ORDER BY product_name")) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) items.add(new OrderItem(r.getLong("product_id"), r.getString("product_name"), Money.amount(r.getLong("unit_price_cents")), r.getInt("quantity")));
            }
        }
        return items;
    }
}