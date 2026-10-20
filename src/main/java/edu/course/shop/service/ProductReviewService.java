package edu.course.shop.service;

import edu.course.shop.exception.StorageException;
import edu.course.shop.exception.ValidationException;
import edu.course.shop.model.ProductReview;
import edu.course.shop.model.User;
import edu.course.shop.persistence.DatabaseManager;
import edu.course.shop.persistence.ProductReviewRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Verified-purchase review reads and customer writes. */
public final class ProductReviewService {
    private final DatabaseManager database;
    private final ProductReviewRepository reviews;

    public ProductReviewService(DatabaseManager database, ProductReviewRepository reviews) {
        this.database = database; this.reviews = reviews;
    }

    public List<ProductReview> list(long productId) {
        try (Connection c = database.open()) { return reviews.list(c, productId); }
        catch (SQLException e) { throw new StorageException(e); }
    }

    public boolean canReview(User actor, long productId) {
        if (actor == null || actor.getId() <= 0 || !actor.canShop() || actor.canManageProducts()) return false;
        try (Connection c = database.open()) { return reviews.hasPurchased(c, actor.getId(), productId); }
        catch (SQLException e) { throw new StorageException(e); }
    }

    public void submit(User actor, long productId, int rating, String text) {
        if (actor == null || actor.getId() <= 0 || !actor.canShop() || actor.canManageProducts())
            throw new ValidationException("Sign in with a customer account to review a product.");
        if (rating < 1 || rating > 5) throw new ValidationException("Choose a rating from 1 to 5 stars.");
        String review = text == null ? "" : text.trim();
        if (review.length() < 8 || review.length() > 800)
            throw new ValidationException("Write 8 to 800 characters about your experience.");
        try (Connection c = database.open()) {
            if (!reviews.hasPurchased(c, actor.getId(), productId))
                throw new ValidationException("A completed purchase is required before reviewing this product.");
            reviews.save(c, actor.getId(), productId, rating, review);
        } catch (SQLException e) { throw new StorageException(e); }
    }
}
