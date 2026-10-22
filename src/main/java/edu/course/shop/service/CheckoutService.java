package edu.course.shop.service;
import edu.course.shop.model.*;
import edu.course.shop.payment.*;
import edu.course.shop.persistence.*;
import edu.course.shop.exception.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class CheckoutService {
    private final DatabaseManager database;
    private final ProductRepository products;
    private final OrderRepository orders;
    public CheckoutService(DatabaseManager database, ProductRepository products, OrderRepository orders) {
        this.database = database; this.products = products; this.orders = orders;
    }
    public Order checkout(User customer,Cart cart,String address,PaymentMethod payment){
        synchronized(cart){return checkoutLocked(customer,cart,address,payment);}
    }
    private Order checkoutLocked(User customer, Cart cart, String address, PaymentMethod payment) {
        if (!(customer instanceof Customer)) throw new ValidationException("Please sign in as a customer to check out.");
        CartService.checkOwner(customer, cart);
        if (cart.getItems().isEmpty()) throw new ValidationException("Your cart is empty.");
        if (address == null || address.trim().length() < 5 || address.trim().length() > 250)
            throw new ValidationException("Enter a delivery address between 5 and 250 characters.");
        if (payment == null) throw new ValidationException("Choose a payment method.");
        try (Connection c = database.open()) {
            c.setAutoCommit(false);
            try {
                List<OrderItem> lines = new ArrayList<>();
                for (CartItem item : cart.getItems()) {
                    Product latest = products.find(c, item.getProduct().getId());
                    if (!latest.isActive()||latest.getCategory().isRetired()) throw new ValidationException(latest.getName() + " is no longer available. Remove it from your cart.");
                    if (latest.getPrice().compareTo(item.getProduct().getPrice()) != 0)
                        throw new ValidationException("The price of " + latest.getName() + " changed. Update its cart quantity to review the new price.");
                    products.deductStock(c, latest, item.getQuantity());
                    lines.add(new OrderItem(latest.getId(), latest.getName(), latest.getPrice(), item.getQuantity()));
                }
                BigDecimal total = lines.stream().map(OrderItem::subtotal).reduce(new BigDecimal("0.00"), BigDecimal::add);
                // Runtime polymorphism: no branch on the concrete payment class.
                PaymentResult result = payment.pay(total);
                if (!result.accepted()) throw new ValidationException("Demo wallet payment declined. Your cart and stock are unchanged.");
                Order order = orders.save(c, new Order(0, customer.getId(), Instant.now().toString(), address.trim(), payment.getName(), result.status(), total, lines));
                try(PreparedStatement saved=c.prepareStatement("DELETE FROM customer_cart_items WHERE customer_id=?")){saved.setLong(1,customer.getId());saved.executeUpdate();}
                c.commit();
                cart.clearAfterCheckout(); // Saved cart was cleared in the order transaction.
                return order;
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException e) { throw new StorageException(e); }
    }
}