package edu.course.shop.ui;
import edu.course.shop.model.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** One timeline for the admin's recorded updates and the customer's tracking screen. */
public final class OrderTracking {
    private OrderTracking(){}
    public static VBox view(Order order){
        VBox box=new VBox(12);box.getStyleClass().add("order-tracking");box.setId("order-tracking-"+order.id());
        box.getChildren().add(Ui.label("Delivery · "+order.fulfillmentStatus(),"section-title"));
        FlowPane steps=new FlowPane(8,8);
        if(order.fulfillmentStatus()==FulfillmentStatus.CANCELLED)steps.getChildren().add(Ui.label("Cancelled · items returned to inventory","order-status"));
        else for(FulfillmentStatus state:java.util.List.of(FulfillmentStatus.CONFIRMED,FulfillmentStatus.PACKED,FulfillmentStatus.SHIPPED,FulfillmentStatus.DELIVERED)){
            Label step=Ui.label((state.ordinal()<order.fulfillmentStatus().ordinal()?"✓ ":"")+state,"order-status");
            step.getStyleClass().add(state.ordinal()<=order.fulfillmentStatus().ordinal()?"tracking-complete":"tracking-upcoming");steps.getChildren().add(step);
        }
        box.getChildren().add(steps);
        if(!order.trackingNumber().isBlank()){
            TextField tracking=new TextField(order.carrier()+" · "+order.trackingNumber());tracking.setEditable(false);tracking.setAccessibleText("Carrier and tracking number");tracking.setId("tracking-number-"+order.id());
            box.getChildren().addAll(tracking,Ui.label("Tracking details are entered by the store. Check delivery progress with the carrier.","muted"));
        }
        DateTimeFormatter date=DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a").withZone(ZoneId.systemDefault());
        for(OrderEvent event:order.events()){
            String time;try{time=date.format(Instant.parse(event.createdAt()));}catch(RuntimeException e){time=event.createdAt();}
            box.getChildren().add(new VBox(3,Ui.label(event.note(),"field-label"),Ui.label(time,"muted")));
        }
        return box;
    }
    public static String payment(Order order){return switch(order.paymentStatus()){case "PAID"->"Paid · demo";case "REFUNDED"->"Refund recorded · demo";case "CANCELLED"->"Payment cancelled";default->"Due on delivery";};}
}