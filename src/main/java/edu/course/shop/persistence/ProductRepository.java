package edu.course.shop.persistence;
import edu.course.shop.model.*;
import edu.course.shop.exception.*;
import java.sql.*;
import java.util.*;

public final class ProductRepository {
    public List<Product> list(Connection c, boolean includeInactive) throws SQLException {
        List<Product> products = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("SELECT * FROM products WHERE deleted=0" + (includeInactive ? "" : " AND active = 1") + " ORDER BY name"); ResultSet r = p.executeQuery()) {
            while (r.next()) products.add(read(c,r));
        }
        return products;
    }
    public Product find(Connection c, long id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT * FROM products WHERE id = ?")) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new ValidationException("Product no longer exists.");
                return read(c,r);
            }
        }
    }
    public void save(Connection c, Product product) throws SQLException {
        String sql = product.getId() == 0
            ? "INSERT INTO products(name,category,price_cents,stock,active,description,photo,specifications,photo_frame,photo_x,photo_y) VALUES(?,?,?,?,?,?,?,?,?,?,?)"
            : "UPDATE products SET name=?,category=?,price_cents=?,stock=?,active=?,description=?,photo=?,specifications=?,photo_frame=?,photo_x=?,photo_y=? WHERE id=? AND deleted=0";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, product.getName()); p.setString(2, product.getCategory().name()); p.setLong(3, Money.cents(product.getPrice()));
            p.setInt(4, product.getStock()); p.setBoolean(5, product.isActive());
            p.setString(6, product.getDescription()); p.setBytes(7, product.getPhoto());
            p.setString(8,product.getSpecifications());
            p.setString(9,product.getPhotoFrame().mode().name());p.setDouble(10,product.getPhotoFrame().x());p.setDouble(11,product.getPhotoFrame().y());
            if (product.getId() != 0) p.setLong(12, product.getId());
            if (p.executeUpdate() != 1) throw new ValidationException("Product no longer exists.");
        }
        long id=product.getId();
        if(id==0)try(Statement query=c.createStatement();ResultSet r=query.executeQuery("SELECT last_insert_rowid()")){r.next();id=r.getLong(1);}
        try(PreparedStatement clear=c.prepareStatement("DELETE FROM product_photos WHERE product_id=?")) {clear.setLong(1,id);clear.executeUpdate();}
        try(PreparedStatement insert=c.prepareStatement("INSERT INTO product_photos(product_id,position,photo) VALUES(?,?,?)")) {
            int position=0;
            for(byte[] bytes:product.getAdditionalPhotos()) {insert.setLong(1,id);insert.setInt(2,position++);insert.setBytes(3,bytes);insert.executeUpdate();}
        }
    }
    public void deductStock(Connection c, Product product, int quantity) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("UPDATE products SET stock=stock-? WHERE id=? AND active=1 AND deleted=0 AND stock>=?")) {
            p.setInt(1, quantity); p.setLong(2, product.getId()); p.setInt(3, quantity);
            if (p.executeUpdate() != 1) throw new OutOfStockException(product.getName());
        }
    }
    private Product read(Connection c,ResultSet r) throws SQLException {
        List<byte[]> gallery=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement("SELECT photo FROM product_photos WHERE product_id=? ORDER BY position")) {
            p.setLong(1,r.getLong("id"));try(ResultSet images=p.executeQuery()){while(images.next())gallery.add(images.getBytes(1));}
        }
        return new Product(r.getLong("id"), r.getString("name"), Category.valueOf(r.getString("category")), Money.amount(r.getLong("price_cents")), r.getInt("stock"), r.getBoolean("active"), r.getString("description"), r.getBytes("photo"),gallery,r.getString("specifications"),new PhotoFrame(PhotoFrame.Mode.valueOf(r.getString("photo_frame")),r.getDouble("photo_x"),r.getDouble("photo_y")));
    }
}