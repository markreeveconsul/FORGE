package edu.course.shop.service;
import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import edu.course.shop.exception.*;
import java.sql.*;
import java.util.*;

/** Customer-owned saved carts. Guest carts remain session-only. No reservation is implied. */
public final class PersistentCartService {
    public record Restored(Cart cart,List<String> notices){public Restored{notices=List.copyOf(notices);}}
    private final DatabaseManager database;private final ProductRepository products;
    public PersistentCartService(DatabaseManager database,ProductRepository products){this.database=database;this.products=products;}
    private void customer(Connection c,User user)throws SQLException{
        if(!(user instanceof Customer))throw new ValidationException("Customer access is required.");
        try(var p=c.prepareStatement("SELECT 1 FROM users WHERE id=? AND role='CUSTOMER'")){p.setLong(1,user.getId());try(var r=p.executeQuery()){if(!r.next())throw new ValidationException("Customer account no longer exists.");}}
    }
    public Restored restore(User user,Cart guest){
        if(guest!=null&&guest.getCustomerId()!=0)throw new ValidationException("Only a guest cart can be merged.");
        Cart restored=new Cart(user.getId());List<String> notices=new ArrayList<>();
        try(Connection c=database.open()){
            customer(c,user);
            try(var p=c.prepareStatement("SELECT * FROM customer_cart_items WHERE customer_id=? ORDER BY product_id")){
                p.setLong(1,user.getId());try(var r=p.executeQuery()){while(r.next())restoreLine(c,restored,r.getLong("product_id"),r.getInt("quantity"),r.getLong("unit_price_cents"),notices);}
            }
            if(guest!=null)for(CartItem item:guest.getItems())restoreLine(c,restored,item.getProduct().getId(),item.getQuantity(),Money.cents(item.getProduct().getPrice()),notices);
        }catch(SQLException e){throw new StorageException(e);}
        save(user,restored.getItems());restored.attachPersistence(rows->save(user,rows));
        return new Restored(restored,notices);
    }
    private void restoreLine(Connection c,Cart cart,long id,int quantity,long oldPrice,List<String> notices)throws SQLException{
        Product p;try{p=products.find(c,id);}catch(ValidationException e){notices.add("A removed product could not be restored.");return;}
        if(!p.isActive()||p.getCategory().isRetired()||p.getStock()==0){notices.add(p.getName()+" is unavailable and was removed from your saved cart.");return;}
        int previous=cart.getItems().stream().filter(i->i.getProduct().getId()==id).mapToInt(CartItem::getQuantity).sum();
        int count=(int)Math.min((long)p.getStock(),(long)previous+quantity);
        if(count<previous+quantity)notices.add(p.getName()+": quantity adjusted to "+count+" available.");
        if(Money.cents(p.getPrice())!=oldPrice)notices.add(p.getName()+": price updated to "+Money.format(p.getPrice())+". Review before checkout.");
        cart.setQuantity(p,count);
    }
    private void save(User user,List<CartItem> rows){
        try(Connection c=database.open()){
            c.setAutoCommit(false);try{
                customer(c,user);
                try(var p=c.prepareStatement("DELETE FROM customer_cart_items WHERE customer_id=?")){p.setLong(1,user.getId());p.executeUpdate();}
                try(var p=c.prepareStatement("INSERT INTO customer_cart_items(customer_id,product_id,quantity,unit_price_cents) VALUES(?,?,?,?)")){
                    for(CartItem item:rows){p.setLong(1,user.getId());p.setLong(2,item.getProduct().getId());p.setInt(3,item.getQuantity());p.setLong(4,Money.cents(item.getProduct().getPrice()));p.addBatch();}p.executeBatch();
                }c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }catch(SQLException e){throw new StorageException(e);}
    }
}