package edu.course.shop.ui;
import edu.course.shop.model.Cart;
import edu.course.shop.model.CartItem;
import edu.course.shop.model.Guest;
import edu.course.shop.model.Money;
import edu.course.shop.model.Product;
import edu.course.shop.model.User;
import edu.course.shop.service.CartService;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class CartView extends VBox {
    private final User user;
    private final Cart cart;
    private final CartService carts;
    private final Runnable changed, checkout, browse;
    private final Label feedback=Ui.feedback("cart-feedback");
    private edu.course.shop.model.CartItem removed;
    private final javafx.animation.PauseTransition undoTimeout=new javafx.animation.PauseTransition(javafx.util.Duration.seconds(6));
    private final Button undo=Ui.link("Undo removal",this::undoRemoval);
    private final HBox undoBar=new HBox(12,Ui.label("Item removed from cart.","muted"),undo);
    private final VBox body=new VBox(18);
    public CartView(User user, Cart cart, CartService carts, Runnable changed, Runnable checkout, Runnable browse) {
        this.user=user; this.cart=cart; this.carts=carts; this.changed=changed; this.checkout=checkout; this.browse=browse;
        getStyleClass().add("page"); setId("cart-page"); setSpacing(16);
        undo.setId("undo-cart-removal");undoBar.setId("undo-removal-bar");undoBar.getStyleClass().add("notice");undoBar.setVisible(false);undoBar.managedProperty().bind(undoBar.visibleProperty());undoTimeout.setOnFinished(e->{removed=null;undoBar.setVisible(false);});sceneProperty().addListener((o,a,b)->{if(b==null){undoTimeout.stop();removed=null;undoBar.setVisible(false);}});
        getChildren().addAll(Ui.label("Your cart", "page-title"),
            Ui.label(user instanceof Guest?"Your guest cart lasts for this visit. Sign in to save it.":"Saved to your account. Review prices and availability before checkout; cart items are not reserved.", "muted"),feedback,undoBar,Ui.scroll(body)); refresh();
    }
    private void refresh() {
        body.getChildren().clear();
        if(cart.getItems().isEmpty()) {
            body.getChildren().add(Ui.empty("Your cart is waiting","Discover something useful for your everyday.","Browse products",browse)); return;
        }
        VBox lines=new VBox(12); lines.setId("cart-items"); lines.setMinWidth(0); HBox.setHgrow(lines,Priority.ALWAYS);
        for(CartItem item:cart.getItems()) lines.getChildren().add(line(item));
        Button next=Ui.primary(user instanceof Guest?"Sign in to checkout":"Continue to checkout",checkout); next.setId("checkout");
        next.setMaxWidth(Double.MAX_VALUE); next.getStyleClass().addAll("cta","purchase-action");
        VBox split=Ui.purchaseColumns(lines,new OrderSummary(cart,next));split.setId("cart-columns");
        Button keepShopping=Ui.link("← Continue shopping",browse); keepShopping.setMaxWidth(Region.USE_PREF_SIZE);
        body.getChildren().addAll(split,keepShopping);
    }
    private VBox line(CartItem item) {
        Product p=item.getProduct();
        VBox title=new VBox(4,Ui.label(p.getName(),"product-name"),Ui.label(Money.format(p.getPrice())+" each","muted"),Ui.label(p.getStock()==0?"Currently sold out":p.getStock()+" available","muted"));
        title.setMinWidth(0); HBox.setHgrow(title,Priority.ALWAYS);
        Label subtotal=Ui.label(Money.format(item.getSubtotal()),"price");
        ProductArtwork art=new ProductArtwork(p,120);art.setPrefWidth(150);art.setMaxWidth(150); Ui.round(art,4);
        HBox header=new HBox(14,art,title,subtotal); header.setAlignment(Pos.CENTER_LEFT);

        // Same pill stepper as the cart drawer; the number is still typeable.
        TextField quantity=new TextField(Integer.toString(item.getQuantity())); quantity.setPrefWidth(48); quantity.setAlignment(Pos.CENTER);
        quantity.getStyleClass().add("stepper-input");
        quantity.setId("cart-quantity-"+p.getId()); quantity.setAccessibleText("Quantity for "+p.getName());
        Button update=Ui.link("Update",()-> {
            try { update(p,Ui.integer(quantity.getText())); }
            catch(RuntimeException error) { Ui.failure(feedback,error); }
        });
        // "Update" only appears once the typed number differs from the cart, so it is never clutter.
        update.visibleProperty().bind(quantity.textProperty().isNotEqualTo(Integer.toString(item.getQuantity())));
        update.managedProperty().bind(update.visibleProperty());
        quantity.setOnAction(e->update.fire());
        Button minus=Ui.button("−",()->update(p,item.getQuantity()-1)); minus.setDisable(item.getQuantity()<=1);
        minus.setAccessibleText("Decrease quantity of "+p.getName()); minus.setId("minus-"+p.getId());
        Button plus=Ui.button("+",()->update(p,item.getQuantity()+1)); plus.setId("plus-"+p.getId());plus.setDisable(item.getQuantity()>=p.getStock());
        plus.setAccessibleText("Increase quantity of "+p.getName());
        minus.getStyleClass().add("stepper-button"); plus.getStyleClass().add("stepper-button");
        HBox stepper=new HBox(2,minus,quantity,plus); stepper.getStyleClass().add("stepper"); stepper.setAlignment(Pos.CENTER);
        Button remove=Ui.button("Remove",()-> { removed=new edu.course.shop.model.CartItem(p,item.getQuantity());cart.remove(p.getId()); changed.run(); refresh();undoBar.setVisible(true);undo.requestFocus();undoTimeout.playFromStart();Ui.success(feedback,p.getName()+" removed."); });
        remove.getStyleClass().add("text-button"); remove.setId("remove-"+p.getId());
        // Typed quantities commit on Enter or Update; +/- buttons commit immediately.
        Region gap=new Region(); HBox.setHgrow(gap,Priority.ALWAYS);
        HBox actions=new HBox(10,stepper,update,gap,remove); actions.setAlignment(Pos.CENTER_LEFT);
        VBox row=new VBox(14,header,actions); row.getStyleClass().addAll("cart-row","cart-item"); row.setId("cart-line-"+p.getId()); return row;
    }
    private void update(Product p,int count) {
        Ui.async(this,"Updating cart…",()-> { carts.update(user,cart,p.getId(),count); return true; },done-> {
            changed.run(); refresh();Motion.reveal(body);Ui.success(feedback,"Quantity updated. Your total is ready.");
        },error->Ui.failure(feedback,error));
    }
    private void undoRemoval() {
        if(removed==null)return;var item=removed;
        Ui.async(this,"Restoring item…",()->{carts.add(user,cart,item.getProduct().getId(),item.getQuantity());return true;},done->{undoTimeout.stop();removed=null;undoBar.setVisible(false);changed.run();refresh();Ui.success(feedback,"Item restored at its current price.");},error->Ui.failure(feedback,error));
    }
}
