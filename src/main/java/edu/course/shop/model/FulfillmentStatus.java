package edu.course.shop.model;
import java.util.List;
public enum FulfillmentStatus {
    CONFIRMED("Confirmed"), PACKED("Packed"), SHIPPED("Shipped"), DELIVERED("Delivered"), CANCELLED("Cancelled");
    private final String label;
    FulfillmentStatus(String label){this.label=label;}
    @Override public String toString(){return label;}
    public List<FulfillmentStatus> next(){return switch(this){case CONFIRMED->List.of(PACKED,CANCELLED);case PACKED->List.of(SHIPPED,CANCELLED);case SHIPPED->List.of(DELIVERED);default->List.of();};}
}