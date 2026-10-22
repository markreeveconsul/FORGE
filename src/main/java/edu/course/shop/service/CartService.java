package edu.course.shop.service;
import edu.course.shop.model.*;
import edu.course.shop.exception.ValidationException;

public final class CartService {
    private final ProductService products;
    public CartService(ProductService products) { this.products = products; }
    public void add(User user, Cart cart, long productId, int quantity) {
        checkOwner(user, cart); cart.add(products.get(productId), quantity);
    }
    public void update(User user, Cart cart, long productId, int quantity) {
        checkOwner(user, cart); cart.setQuantity(products.get(productId), quantity);
    }
    /** Transfer snapshots without silently changing prices; checkout revalidates stock and prices. */
    public Cart transferGuest(Cart guest, User customer) {
        if (guest.getCustomerId() != 0 || !(customer instanceof Customer))
            throw new ValidationException("A guest cart can only transfer to a customer.");
        Cart transferred = new Cart(customer.getId());
        for (CartItem item : guest.getItems()) transferred.add(item.getProduct(), item.getQuantity());
        return transferred;
    }
    public String review(Cart cart) {
        for (CartItem item : cart.getItems()) {
            Product latest = products.get(item.getProduct().getId());
            if (!latest.isActive() || latest.getCategory().isRetired() || latest.getStock() < item.getQuantity() ||
                latest.getPrice().compareTo(item.getProduct().getPrice()) != 0)
                return "A product changed while you were shopping. Review its availability and update or remove it before checkout.";
        }
        return "Your selections are saved. Review your cart before checkout.";
    }
    public static void checkOwner(User user, Cart cart) {
        if (user == null || !user.canShop() || user.getId() != cart.getCustomerId())
            throw new ValidationException("Sign in as the customer who owns this cart.");
    }
    public record RepeatLine(Product product,int quantity) { }
    public record RepeatPlan(java.util.List<RepeatLine> lines,java.util.List<String> unavailable) {
        public RepeatPlan {lines=java.util.List.copyOf(lines);unavailable=java.util.List.copyOf(unavailable);}
    }
    public RepeatPlan prepareRepeat(User user,Cart cart,Order order) {
        checkOwner(user,cart);
        if(!(user instanceof Customer)||order.customerId()!=user.getId())throw new ValidationException("This order belongs to another customer.");
        java.util.List<RepeatLine> lines=new java.util.ArrayList<>();java.util.List<String> unavailable=new java.util.ArrayList<>();
        for(OrderItem item:order.items()) {
            Product product=products.get(item.productId());
            int existing=cart.getItems().stream().filter(row->row.getProduct().getId()==product.getId()).mapToInt(CartItem::getQuantity).sum();
            if(!product.isActive()||product.getCategory().isRetired()||product.getStock()-existing<item.quantity())unavailable.add(item.productName()+" — unavailable in the requested quantity");
            else lines.add(new RepeatLine(product,item.quantity()));
        }return new RepeatPlan(lines,unavailable);
    }
    /** Build a complete replacement before committing, so a stale line cannot partially change the cart. */
    public void applyRepeat(User user,Cart cart,RepeatPlan plan) {
        checkOwner(user,cart);if(!(user instanceof Customer))throw new ValidationException("Customer access is required.");
        Cart replacement=new Cart(cart.getCustomerId());
        for(CartItem existing:cart.getItems())replacement.add(existing.getProduct(),existing.getQuantity());
        for(RepeatLine line:plan.lines()) {
            Product latest=products.get(line.product().getId());
            if(latest.getPrice().compareTo(line.product().getPrice())!=0)throw new ValidationException("A price changed. Review Buy again before adding items.");
            replacement.add(latest,line.quantity());
        }
        cart.replaceWith(replacement);
    }
}