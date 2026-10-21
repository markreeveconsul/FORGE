package edu.course.shop.ui;
import edu.course.shop.model.*;
import edu.course.shop.service.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

public final class OrdersView extends VBox {
    public OrdersView(User user,OrderService orders,ProductService products,Runnable browse,Consumer<Order> repeat) {
        setId("orders-page");getStyleClass().add("page");setSpacing(20);
        VBox cards=new VBox(16);cards.setId("order-cards");cards.setMinWidth(0);cards.getChildren().setAll(Ui.skeletonRows(3));VBox receipt=new VBox(16);receipt.getStyleClass().add("card");receipt.setMinWidth(0);receipt.setId("order-receipt");receipt.getChildren().setAll(Ui.skeletonRows(3));
        VBox body=Ui.responsiveColumns(cards,receipt,950,24);Label feedback=Ui.feedback("orders-feedback");
        DateTimeFormatter date=DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.systemDefault());
        Runnable refresh=()->Ui.async(this,"Loading your orders…",()->{
            List<Order> history=orders.history(user);Map<Long,Product> images=new HashMap<>();
            for(Order order:history)for(OrderItem item:order.items())if(!images.containsKey(item.productId())) {
                try{images.put(item.productId(),products.get(item.productId()));}catch(edu.course.shop.exception.ValidationException ignored){/* Missing photo never hides a saved receipt. */}
            }return new History(history,images);
        },data->{
            cards.getChildren().clear();receipt.getChildren().clear();
            if(data.orders().isEmpty()){cards.getChildren().add(Ui.empty("Your story starts here","Your completed purchases will appear here.","Browse products",browse));receipt.setVisible(false);receipt.managedProperty().bind(receipt.visibleProperty());return;}
            receipt.setVisible(true);java.util.List<VBox> orderCards=new ArrayList<>();
            for(Order order:data.orders()) {
                VBox card=new VBox(12);card.getStyleClass().add("order-card");card.setId("order-card-"+order.id());orderCards.add(card);
                Runnable select=()->{for(VBox c:orderCards)c.getStyleClass().remove("order-selected");card.getStyleClass().add("order-selected");showReceipt(receipt,order);};
                HBox pictures=new HBox(10);for(OrderItem item:order.items().stream().limit(3).toList()) {
                    Product product=data.images().get(item.productId());ProductArtwork art=product==null?new ProductArtwork((byte[])null,item.productName(),56):new ProductArtwork(product,56);art.setPrefWidth(74);art.setMaxWidth(74);pictures.getChildren().add(art);
                }
                int itemCount=order.items().stream().mapToInt(OrderItem::quantity).sum();
                boolean paid=order.paymentStatus().equals("PAID");
                Label title=Ui.label("#MK-"+order.id()+"  /  "+date.format(Instant.parse(order.createdAt()))+"  /  "+itemCount+(itemCount==1?" item":" items"),"order-title");title.setWrapText(true);
                Label state=Ui.label(paid?"Paid · demo":"Due on delivery","order-status");state.getStyleClass().add(paid?"order-status-paid":"order-status-pending");
                Region gap=new Region();HBox.setHgrow(gap,Priority.ALWAYS);
                HBox header=new HBox(12,title,gap,state);header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                Button view=Ui.link("Details →",select);view.setId("receipt-"+order.id());Button again=Ui.link("Buy again",()->repeat.accept(order));again.setId("buy-again-"+order.id());
                card.getChildren().addAll(header,Ui.label(Money.format(order.total()),"price"),pictures,new HBox(16,view,again));cards.getChildren().add(card);
            }
            orderCards.getFirst().getStyleClass().add("order-selected");showReceipt(receipt,data.orders().getFirst());
        },error->{Ui.failure(feedback,error);cards.getChildren().setAll(Ui.empty("Could not load orders","Your orders are saved. Please retry.","Retry",()->((Button)lookup("#refresh-orders")).fire()));});
        Button refreshButton=Ui.link("Refresh orders",refresh);refreshButton.setId("refresh-orders");
        getChildren().addAll(Ui.label("Your orders","page-title"),Ui.label("Good finds, kept on record. Photos show the current catalog; receipts retain your purchase details.","muted"),refreshButton,feedback,Ui.scroll(body));Platform.runLater(refresh);
    }
    private record History(List<Order> orders,Map<Long,Product> images) { }
    private static void showReceipt(VBox receipt,Order order) {
        receipt.getChildren().setAll(Ui.label("Receipt · #MK-"+order.id(),"section-title"),Ui.label("Deliver to\n"+order.address(),"muted"),new Separator());
        for(OrderItem item:order.items())receipt.getChildren().add(Ui.row(item.quantity()+" × "+item.productName(),Money.format(item.subtotal()),"summary-item","summary-value"));
        receipt.getChildren().addAll(new Separator(),Ui.row("Total",Money.format(order.total()),"field-label","total"),Ui.label(order.paymentMethod()+" · "+(order.paymentStatus().equals("PAID")?"Paid (demo)":"Due on delivery"),"notice"));
    }
}
