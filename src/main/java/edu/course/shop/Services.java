package edu.course.shop;
import edu.course.shop.service.*;
/** Wires already-constructed services together; contains no business rules. */
public record Services(AuthService auth, ProductService products, CheckoutService checkout, OrderService orders, AdminService admin, ProductReviewService reviews) {
    public AccountService accounts() { return auth.accounts(); }
}
