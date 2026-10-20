package edu.course.shop.service;
import edu.course.shop.model.*;
import edu.course.shop.exception.ValidationException;
import java.util.List;

/** Revalidates an explicitly approved set; model text never enters this mutation path. */
public final class AssistantCart {
    private AssistantCart() { }
    public static List<Product> validate(List<Product> approved, ProductService products) {
        if (approved.isEmpty() || approved.size() > 6) throw new ValidationException("Choose between one and six products.");
        return approved.stream().map(p -> {
            Product current = products.get(p.getId());
            if (current == null || !current.isActive() || current.getCategory().isRetired() || current.getStock() < 1)
                throw new ValidationException(p.getName() + " is no longer available. Ask for updated options.");
            if (current.getPrice().compareTo(p.getPrice()) != 0)
                throw new ValidationException(p.getName() + " changed price. Ask for updated options before adding it.");
            return current;
        }).toList();
    }
    public static void apply(User user, Cart cart, List<Product> validated) {
        CartService.checkOwner(user, cart);
        Cart replacement = new Cart(cart.getCustomerId());
        for (CartItem item : cart.getItems()) replacement.add(item.getProduct(), item.getQuantity());
        for (Product p : validated) replacement.add(p, 1);
        cart.clear();
        for (CartItem item : replacement.getItems()) cart.add(item.getProduct(), item.getQuantity());
    }
}
