package edu.course.shop.model;
public record OrderEvent(FulfillmentStatus status,String createdAt,String note) { }