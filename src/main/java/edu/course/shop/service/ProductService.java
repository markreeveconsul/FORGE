package edu.course.shop.service;
import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import edu.course.shop.exception.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

public final class ProductService {
    private final DatabaseManager database;
    private final ProductRepository products;
    public ProductService(DatabaseManager database, ProductRepository products) { this.database = database; this.products = products; }
    public List<Product> search(User user, String query, Category category) {
        if (user == null) throw new ValidationException("Please sign in.");
        String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        try (Connection c = database.open()) {
            return products.list(c, user.canManageProducts()).stream()
                .filter(p -> user.canManageProducts()||!p.getCategory().isRetired())
                .filter(p -> p.getName().toLowerCase(Locale.ROOT).contains(term))
                .filter(p -> category == null || p.getCategory() == category).toList();
        } catch (SQLException e) { throw new StorageException(e); }
    }
    public List<Product> search(User user,String query,Category category,boolean inStockOnly,BigDecimal maximumPrice) {
        if(maximumPrice!=null)Money.price(maximumPrice);
        return search(user,query,category).stream()
            .filter(p->!inStockOnly||p.getStock()>0)
            .filter(p->maximumPrice==null||p.getPrice().compareTo(maximumPrice)<=0).toList();
    }
    public Product get(long id) {
        try (Connection c = database.open()) { return products.find(c, id); }
        catch (SQLException e) { throw new StorageException(e); }
    }
    public record ManagerChange(Product expected,String description,String specifications,Boolean active,String sources) { }
    /** Apply exactly the reviewed snapshots in one transaction. Never overwrite photos, prices or stock. */
    public void applyManagerChanges(User actor,String batchId,String intent,List<ManagerChange> changes) {
        if(actor==null||!actor.canManageProducts())throw new ValidationException("Administrator access is required.");
        try{UUID.fromString(batchId);}catch(RuntimeException e){throw new ValidationException("Invalid change batch.");}
        if(!Set.of("fix_content","update_status").contains(intent)||changes.isEmpty()||changes.size()>20)throw new ValidationException("Review a batch of 1–20 product changes.");
        Set<Long> seen=new HashSet<>();
        for(ManagerChange change:changes){
            Product p=Objects.requireNonNull(change.expected());
            if(!seen.add(p.getId())||p.getId()<=0)throw new ValidationException("Each product can appear only once in a batch.");
            if(change.description()!=null&&(!p.getDescription().isBlank()||change.description().isBlank()||change.description().length()>1000)
                ||change.specifications()!=null&&(!p.getSpecifications().isBlank()||change.specifications().isBlank()||change.specifications().length()>1000))throw new ValidationException("AI content can only fill empty fields with a reviewed draft.");
            if(change.active()!=null&&change.active()&&p.getCategory().isRetired())throw new ValidationException("Choose a current tech category before activating retired inventory.");
            if(change.sources()==null||change.sources().length()>6500)throw new ValidationException("Invalid content sources.");
            if(intent.equals("fix_content")&&change.active()!=null||intent.equals("update_status")&&(change.description()!=null||change.specifications()!=null||change.active()==null))throw new ValidationException("The change does not match the reviewed action.");
            if(change.description()==null&&change.specifications()==null&&change.active()==null)throw new ValidationException("This draft has no changes to apply.");
        }
        try(Connection c=database.open()){
            c.setAutoCommit(false);
            try {
                try(PreparedStatement batch=c.prepareStatement("INSERT INTO admin_ai_batches(id,actor_id,intent,created_at) VALUES(?,?,?,?)")){
                    batch.setString(1,batchId);batch.setLong(2,actor.getId());batch.setString(3,intent);batch.setString(4,java.time.Instant.now().toString());batch.executeUpdate();
                }
                for(ManagerChange change:changes){
                    Product p=change.expected();String description=change.description()==null?p.getDescription():change.description().trim();
                    String specs=change.specifications()==null?p.getSpecifications():change.specifications().trim();boolean active=change.active()==null?p.isActive():change.active();
                    try(PreparedStatement update=c.prepareStatement("UPDATE products SET description=?,specifications=?,active=? WHERE id=? AND name=? AND category=? AND price_cents=? AND stock=? AND active=? AND description=? AND specifications=?")){
                        update.setString(1,description);update.setString(2,specs);update.setBoolean(3,active);update.setLong(4,p.getId());update.setString(5,p.getName());update.setString(6,p.getCategory().name());update.setLong(7,Money.cents(p.getPrice()));update.setInt(8,p.getStock());update.setBoolean(9,p.isActive());update.setString(10,p.getDescription());update.setString(11,p.getSpecifications());
                        if(update.executeUpdate()!=1)throw new ValidationException(p.getName()+" changed since this preview. Run the command again; nothing in this batch was saved.");
                    }
                    try(PreparedStatement audit=c.prepareStatement("INSERT INTO admin_ai_changes VALUES(?,?,?,?,?,?,?,?,?)")){
                        audit.setString(1,batchId);audit.setLong(2,p.getId());audit.setString(3,p.getDescription());audit.setString(4,description);audit.setString(5,p.getSpecifications());audit.setString(6,specs);audit.setBoolean(7,p.isActive());audit.setBoolean(8,active);audit.setString(9,change.sources());audit.executeUpdate();
                    }
                }c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }catch(SQLException e){if(e.getMessage()!=null&&e.getMessage().contains("UNIQUE constraint failed: admin_ai_batches"))throw new ValidationException("This batch has already been applied.");throw new StorageException(e);}
    }
    public void save(User actor, long id, String name, Category category, BigDecimal price, int stock, boolean active) {
        if (actor == null || !actor.canManageProducts()) throw new ValidationException("Administrator access is required.");
        Product existing = id == 0 ? null : get(id);
        save(actor, id, name, category, price, stock, active, existing == null ? "" : existing.getDescription(), existing == null ? null : existing.getPhoto());
    }
    public void save(User actor, long id, String name, Category category, BigDecimal price, int stock, boolean active, String description, byte[] photo) {
        if (actor == null || !actor.canManageProducts()) throw new ValidationException("Administrator access is required.");
        Product existing=id==0?null:get(id);
        save(actor,id,name,category,price,stock,active,description,photo,
            photo==null||existing==null?List.of():existing.getAdditionalPhotos(),existing==null?"":existing.getSpecifications());
    }
    /** Inline edits change only price/stock and reject stale values instead of overwriting another editor. */
    public void updateInventoryNumbers(User actor,Product expected,BigDecimal price,int stock){
        if(actor==null||!actor.canManageProducts())throw new ValidationException("Administrator access is required.");
        if(expected==null||expected.getId()<=0)throw new ValidationException("Choose a saved product.");
        BigDecimal valid=Money.price(price);if(stock<0||stock>100000)throw new ValidationException("Stock must be between 0 and 100,000.");
        try(Connection c=database.open();var statement=c.prepareStatement("UPDATE products SET price_cents=?,stock=? WHERE id=? AND price_cents=? AND stock=?")){
            statement.setLong(1,Money.cents(valid));statement.setInt(2,stock);statement.setLong(3,expected.getId());statement.setLong(4,Money.cents(expected.getPrice()));statement.setInt(5,expected.getStock());
            if(statement.executeUpdate()!=1)throw new ValidationException("This product changed while you were editing. Refresh inventory and try again.");
        }catch(SQLException e){throw new StorageException(e);}
    }
    public void save(User actor,long id,String name,Category category,BigDecimal price,int stock,boolean active,String description,
                     byte[] photo,List<byte[]> additionalPhotos,String specifications) {
        if(actor==null||!actor.canManageProducts())throw new ValidationException("Administrator access is required.");
        save(actor,id,name,category,price,stock,active,description,photo,additionalPhotos,specifications,
            id==0?PhotoFrame.AUTO:get(id).getPhotoFrame());
    }
    public void save(User actor,long id,String name,Category category,BigDecimal price,int stock,boolean active,String description,
                     byte[] photo,List<byte[]> additionalPhotos,String specifications,PhotoFrame frame) {
        if(actor==null||!actor.canManageProducts())throw new ValidationException("Administrator access is required.");
        if(category!=null&&category.isRetired()&&(active||id==0))throw new ValidationException("Choose a tech category before offering this product. Retired inventory can stay inactive until you replace it.");
        byte[] snapshot = photo == null ? null : photo.clone();
        ProductPhotos.validate(snapshot);
        Product product = new Product(id, name, category, price, stock, active, description, snapshot,additionalPhotos,specifications,frame);
        for(byte[] bytes:product.getAdditionalPhotos())ProductPhotos.validate(bytes);
        try (Connection c = database.open()) {
            c.setAutoCommit(false);
            try {products.save(c,product);c.commit();}
            catch(SQLException|RuntimeException error){c.rollback();throw error;}
        }
        catch (SQLException e) { throw new StorageException(e); }
    }
}
