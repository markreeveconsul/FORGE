package edu.course.shop.model;
import edu.course.shop.exception.OutOfStockException;
import edu.course.shop.exception.ValidationException;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

/** Validated snapshots; persistence succeeds before a visible cart mutation is committed. */
public final class Cart {
    private final long customerId;
    private final Map<Long,CartItem> items=new LinkedHashMap<>();
    private Consumer<List<CartItem>> persistence=rows->{};
    public Cart(long customerId){this.customerId=customerId;}
    public long getCustomerId(){return customerId;}
    public synchronized List<CartItem> getItems(){return List.copyOf(items.values());}
    public synchronized void attachPersistence(Consumer<List<CartItem>> save){persistence=Objects.requireNonNull(save);}
    public synchronized void add(Product product,int quantity){
        if(quantity<=0)throw new ValidationException("Quantity must be positive.");
        int old=items.containsKey(product.getId())?items.get(product.getId()).getQuantity():0;
        if((long)old+quantity>100000)throw new ValidationException("Quantity is too large.");setQuantity(product,old+quantity);
    }
    public synchronized void setQuantity(Product product,int quantity){
        if(!product.isActive()||product.getCategory().isRetired())throw new ValidationException("This product is no longer available.");
        CartItem item=new CartItem(product,quantity);if(quantity>product.getStock())throw new OutOfStockException(product.getName());
        Map<Long,CartItem> next=new LinkedHashMap<>(items);next.put(product.getId(),item);commit(next);
    }
    public synchronized void remove(long id){Map<Long,CartItem> next=new LinkedHashMap<>(items);next.remove(id);commit(next);}
    public synchronized void clear(){commit(Map.of());}
    public synchronized void replaceWith(Cart replacement){
        if(replacement.customerId!=customerId)throw new ValidationException("Cart owner does not match.");
        Map<Long,CartItem> next=new LinkedHashMap<>();for(CartItem item:replacement.getItems())next.put(item.getProduct().getId(),item);commit(next);
    }
    /** Checkout deletes the saved cart in the same database transaction as the order. */
    public synchronized void clearAfterCheckout(){items.clear();}
    private void commit(Map<Long,CartItem> next){persistence.accept(List.copyOf(next.values()));items.clear();items.putAll(next);}
    public synchronized BigDecimal getTotal(){return items.values().stream().map(CartItem::getSubtotal).reduce(new BigDecimal("0.00"),BigDecimal::add);}
}