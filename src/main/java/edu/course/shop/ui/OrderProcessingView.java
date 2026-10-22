package edu.course.shop.ui;
import edu.course.shop.model.*;
import edu.course.shop.service.OrderService;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Admin order operations with optimistic state checks and a separate payment summary. */
public final class OrderProcessingView extends VBox {
    public OrderProcessingView(User admin,OrderService service,Order order,Runnable refreshed){
        super(16);setId("admin-order-processor");getStyleClass().add("card");setMinWidth(0);
        Label feedback=Ui.feedback("order-process-feedback");
        ComboBox<FulfillmentStatus> status=new ComboBox<>();status.setId("order-delivery-status");status.getItems().add(order.fulfillmentStatus());status.getItems().addAll(order.fulfillmentStatus().next());status.setValue(order.fulfillmentStatus());status.setMaxWidth(Double.MAX_VALUE);
        TextField carrier=new TextField(order.carrier()),tracking=new TextField(order.trackingNumber());carrier.setId("order-carrier");tracking.setId("order-tracking-number");carrier.setPromptText("Carrier name");tracking.setPromptText("Tracking number");
        VBox details=new VBox(8,Ui.label("Carrier","field-label"),carrier,Ui.label("Tracking number","field-label"),tracking);
        details.visibleProperty().bind(status.valueProperty().isEqualTo(FulfillmentStatus.SHIPPED).or(status.valueProperty().isEqualTo(FulfillmentStatus.DELIVERED)));details.managedProperty().bind(details.visibleProperty());
        Button save=Ui.primary("Update order",()->{
            FulfillmentStatus next=status.getValue();
            if(next==FulfillmentStatus.CANCELLED){
                Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"Cancel order #MK-"+order.id()+" and return its items to inventory?"+(order.paymentStatus().equals("PAID")?" A simulated refund will be recorded.":""),ButtonType.YES,ButtonType.NO);confirm.initOwner(getScene().getWindow());confirm.setHeaderText("Confirm cancellation");if(confirm.showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;
            }
            String courier=carrier.getText(),number=tracking.getText();
            Ui.async(this,"Updating order…",()->service.update(admin,order.id(),order.fulfillmentStatus(),next,courier,number),updated->{Ui.success(feedback,"Order updated.");refreshed.run();},error->Ui.failure(feedback,error));
        });save.setId("save-order-status");save.getStyleClass().add("workspace-orange-button");
        save.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(()->order.fulfillmentStatus().next().isEmpty()||status.getValue()==order.fulfillmentStatus()&&(!order.fulfillmentStatus().equals(FulfillmentStatus.SHIPPED)||carrier.getText().equals(order.carrier())&&tracking.getText().equals(order.trackingNumber())),status.valueProperty(),carrier.textProperty(),tracking.textProperty()));
        VBox items=new VBox(8);for(OrderItem item:order.items())items.getChildren().add(Ui.row(item.quantity()+" × "+item.productName(),Money.format(item.subtotal()),"summary-item","summary-value"));
        getChildren().addAll(Ui.label("Process order #MK-"+order.id(),"section-title"),Ui.label("Payment: "+OrderTracking.payment(order)+" · "+order.paymentMethod(),"notice"),items,Ui.label("Deliver to\n"+order.address(),"muted"),OrderTracking.view(order),new Separator(),Ui.label("Delivery status","field-label"),status,details,save,feedback);
    }
}