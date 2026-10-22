package edu.course.shop.ui;
import edu.course.shop.model.*;
import edu.course.shop.service.OrderService;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Admin order operations with optimistic state checks and independent payment records. */
public final class OrderProcessingView extends VBox {
    public OrderProcessingView(User admin,OrderService service,Order order,Runnable refreshed){
        super(16);setId("admin-order-processor");getStyleClass().add("card");setMinWidth(0);
        Label feedback=Ui.feedback("order-process-feedback");javafx.beans.property.BooleanProperty busy=new javafx.beans.property.SimpleBooleanProperty();
        ComboBox<FulfillmentStatus> status=new ComboBox<>();status.setId("order-delivery-status");status.getItems().add(order.fulfillmentStatus());status.getItems().addAll(order.fulfillmentStatus().next());status.setValue(order.fulfillmentStatus());status.setMaxWidth(Double.MAX_VALUE);
        TextField carrier=new TextField(order.carrier()),tracking=new TextField(order.trackingNumber());carrier.setId("order-carrier");tracking.setId("order-tracking-number");carrier.setPromptText("Carrier name");tracking.setPromptText("Tracking number");
        VBox details=new VBox(8,Ui.label("Carrier","field-label"),carrier,Ui.label("Tracking number","field-label"),tracking);
        details.visibleProperty().bind(status.valueProperty().isEqualTo(FulfillmentStatus.SHIPPED).or(status.valueProperty().isEqualTo(FulfillmentStatus.DELIVERED)));details.managedProperty().bind(details.visibleProperty());
        Button save=Ui.primary("Update order",()->{
            FulfillmentStatus next=status.getValue();
            if(next==FulfillmentStatus.CANCELLED){
                Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"Cancel order #MK-"+order.id()+" and return its items to inventory?"+(order.paymentStatus().equals("PAID")?" A simulated refund will be recorded.":""),ButtonType.YES,ButtonType.NO);confirm.initOwner(getScene().getWindow());confirm.setHeaderText("Confirm cancellation");if(confirm.showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;
            }
            String courier=carrier.getText(),number=tracking.getText();busy.set(true);
            Ui.async(this,"Updating order…",()->service.update(admin,order.id(),order.fulfillmentStatus(),next,courier,number),updated->{busy.set(false);refreshed.run();},error->{busy.set(false);Ui.failure(feedback,error);});
        });save.setId("save-order-status");save.getStyleClass().add("workspace-orange-button");
        save.disableProperty().bind(busy.or(javafx.beans.binding.Bindings.createBooleanBinding(()->order.fulfillmentStatus().next().isEmpty()||status.getValue()==order.fulfillmentStatus()&&(!order.fulfillmentStatus().equals(FulfillmentStatus.SHIPPED)||carrier.getText().equals(order.carrier())&&tracking.getText().equals(order.trackingNumber())),status.valueProperty(),carrier.textProperty(),tracking.textProperty())));
        VBox items=new VBox(8);for(OrderItem item:order.items())items.getChildren().add(Ui.row(item.quantity()+" × "+item.productName(),Money.format(item.subtotal()),"summary-item","summary-value"));
        Label destination=Ui.label("Deliver to\n"+order.address(),"muted");destination.setId("process-delivery-address");destination.setMinHeight(Region.USE_PREF_SIZE);
        VBox summary=new VBox(14,items,destination,OrderTracking.view(order));summary.setMinWidth(0);summary.setMinHeight(Region.USE_PREF_SIZE);summary.setMaxWidth(Double.MAX_VALUE);
        VBox controls=new VBox(12,Ui.label("Delivery status","field-label"),status,details,save,Ui.label("Delivery updates are recorded locally. Carrier tracking is entered manually.","muted"),feedback);controls.setMinWidth(0);controls.setMinHeight(Region.USE_PREF_SIZE);controls.setMaxWidth(Double.MAX_VALUE);
        if(order.fulfillmentStatus()==FulfillmentStatus.DELIVERED&&order.paymentStatus().equals("PENDING")){
            Button collect=Ui.button("Record COD payment collected · demo",()->{
                Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"Record this delivered order as paid? This is a simulated payment record.",ButtonType.YES,ButtonType.NO);confirm.initOwner(getScene().getWindow());confirm.setHeaderText("Record payment received");
                if(confirm.showAndWait().orElse(ButtonType.NO)==ButtonType.YES){busy.set(true);Ui.async(this,"Recording payment…",()->service.recordDeliveryPayment(admin,order.id()),updated->{busy.set(false);refreshed.run();},error->{busy.set(false);Ui.failure(feedback,error);});}
            });collect.setId("record-order-payment");collect.disableProperty().bind(busy);controls.getChildren().add(collect);
        }
        getChildren().addAll(Ui.label("Process order #MK-"+order.id(),"section-title"),Ui.label("Payment: "+OrderTracking.payment(order)+" · "+order.paymentMethod(),"notice"),Ui.responsiveColumns(summary,controls,800,24));
    }
}