package edu.course.shop.model;

/** A public product review from a customer whose order contains this product. */
public record ProductReview(long id, long productId, String author, int rating, String text, String createdAt) { }
