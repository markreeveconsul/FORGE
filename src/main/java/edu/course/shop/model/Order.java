package edu.course.shop.model;
import java.math.BigDecimal;
import java.util.List;
public record Order(long id,long customerId,String createdAt,String address,String paymentMethod,String paymentStatus,
                    BigDecimal total,List<OrderItem> items,FulfillmentStatus fulfillmentStatus,String carrier,String trackingNumber,List<OrderEvent> events) {
    public Order {items=List.copyOf(items);events=List.copyOf(events);}
    public Order(long id,long customerId,String createdAt,String address,String paymentMethod,String paymentStatus,BigDecimal total,List<OrderItem> items){
        this(id,customerId,createdAt,address,paymentMethod,paymentStatus,total,items,FulfillmentStatus.CONFIRMED,"","",List.of());
    }
}