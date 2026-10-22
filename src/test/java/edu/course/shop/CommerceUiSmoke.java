package edu.course.shop;
import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import edu.course.shop.service.*;
import edu.course.shop.ui.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** End-to-end local shopping/admin lifecycle against synthetic data; never calls an AI provider. */
public final class CommerceUiSmoke {
    private static Scene scene;private static Stage stage;private static Path evidence;private static int checks;
    private static <T>T fx(Callable<T> work)throws Exception{FutureTask<T> task=new FutureTask<>(()->{if(scene!=null){scene.getRoot().applyCss();scene.getRoot().layout();}return work.call();});Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private static void waitFor(BooleanSupplier condition)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(!fx(condition::getAsBoolean)){if(System.nanoTime()>until)throw new AssertionError("Commerce screen timed out");Thread.sleep(35);}}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;System.out.println("PASS "+message);}
    private static void press(String id)throws Exception{fx(()->{((Button)scene.lookup("#"+id)).fire();return null;});}
    private static void text(String id,String value)throws Exception{fx(()->{((TextInputControl)scene.lookup("#"+id)).setText(value);return null;});}
    private static void click(String label)throws Exception{fx(()->{scene.getRoot().lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals(label)).map(n->(Button)n).findFirst().orElseThrow().fire();return null;});}
    private static void login(String name,String password)throws Exception{press("account");text("username",name);text("password",password);press("login");}
    private static void catalog(int count)throws Exception{waitFor(()->scene.lookup("#product-grid") instanceof TilePane grid&&grid.getChildren().size()==count);}
    private static void snapshot(String name,int width,int height)throws Exception{fx(()->{scene.getRoot().resize(width,height);for(int i=0;i<5;i++){scene.getRoot().applyCss();scene.getRoot().layout();}var image=scene.getRoot().snapshot(null,null);BufferedImage png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));ImageIO.write(png,"png",evidence.resolve(name).toFile());return null;});}
    private static void advance(FulfillmentStatus status)throws Exception{
        Node before=fx(()->scene.lookup("#admin-order-processor"));
        fx(()->{((ComboBox<FulfillmentStatus>)scene.lookup("#order-delivery-status")).setValue(status);return null;});
        if(status==FulfillmentStatus.SHIPPED){text("order-carrier","FORGE Demo Courier");text("order-tracking-number","FORGE-TRACK-001");}
        press("save-order-status");waitFor(()->scene.lookup("#admin-order-processor")!=before&&scene.lookup("#order-delivery-status") instanceof ComboBox<?> box&&box.getValue()==status);
    }
    private static DialogPane dialog(){return Window.getWindows().stream().filter(w->w.isShowing()&&w!=stage&&w.getScene().getRoot() instanceof DialogPane).map(w->(DialogPane)w.getScene().getRoot()).findFirst().orElse(null);}
    private static void confirmDelete(boolean yes)throws Exception{fx(()->{Platform.runLater(()->((Button)scene.lookup("#delete-product")).fire());return null;});waitFor(()->dialog()!=null);fx(()->{DialogPane panel=dialog();check(panel.getContentText().contains("Studio GPU"),"Delete confirmation names the selected product");ButtonType type=yes?panel.getButtonTypes().stream().filter(t->t.getText().equals("Delete product")).findFirst().orElseThrow():ButtonType.CANCEL;((Button)panel.lookupButton(type)).fire();return null;});waitFor(()->dialog()==null);}
    public static void main(String[] args)throws Exception{
        evidence=Path.of(args[0]);Files.createDirectories(evidence);DatabaseManager db=new DatabaseManager(evidence.resolve("commerce-"+System.nanoTime()+".db"));db.initialize();ProductRepository p=new ProductRepository();OrderRepository o=new OrderRepository();
        Services services=new Services(new AuthService(db,new UserRepository()),new ProductService(db,p),new CheckoutService(db,p,o),new OrderService(db,o),new AdminService(db,new AdminRepository()),new ProductReviewService(db,new ProductReviewRepository()));
        User admin=services.auth().login("admin","admin123"),customer=services.auth().login("customer","customer123");try(var c=db.open();var s=c.createStatement()){s.executeUpdate("DELETE FROM products");}
        byte[] photo;try(var input=CommerceUiSmoke.class.getResourceAsStream("/images/category-laptop.png")){photo=input.readAllBytes();}
        for(int ram:new int[]{16,32})services.products().save(admin,0,"Studio laptop "+ram,Category.LAPTOPS,new BigDecimal(ram==16?"900":"1100"),8,true,"A synthetic configuration used for local UI verification.",photo,List.of(),"Brand: FORGE\nModel: Studio\nSKU: ST-"+ram+"\nVariant: "+ram+" GB / 1 TB\nRAM: "+ram+" GB\nStorage: 1 TB\nMemory type: DDR5");
        services.products().save(admin,0,"Studio GPU",Category.PC_PARTS,new BigDecimal("500"),3,true,"Fixture",null,List.of(),"Brand: FORGE\nVRAM: 8 GB\nWattage: 450 W");
        long sixteen=services.products().search(admin,"Studio laptop 16",null).getFirst().getId(),thirtytwo=services.products().search(admin,"Studio laptop 32",null).getFirst().getId(),gpu=services.products().search(admin,"Studio GPU",null).getFirst().getId();
        CountDownLatch ready=new CountDownLatch(1);Platform.startup(ready::countDown);ready.await();Platform.setImplicitExit(false);
        try{
            fx(()->{Motion.reducedProperty().set(true);stage=new Stage();scene=new Scene(new StackPane(),1440,980);scene.getStylesheets().add(CommerceUiSmoke.class.getResource("/shop.css").toExternalForm());stage.setScene(scene);new ShopWindow(stage,services).showStorefront();return null;});catalog(3);
            press("toggle-filters");waitFor(()->scene.lookup("#filter-spec-ram")!=null);snapshot("01-specification-filters.png",1440,980);
            fx(()->{((ComboBox<String>)scene.lookup("#filter-spec-ram")).setValue("16 GB");return null;});press("apply-filters");catalog(1);
            fx(()->{check(scene.lookup("#product-"+sixteen)!=null&&scene.lookup("#product-"+thirtytwo)==null,"RAM filter shows the exact configuration");check(!scene.lookup("#filter-chips").lookupAll(".filter-chip").isEmpty(),"Specification filter has a removable chip");return null;});
            press("view-"+sixteen);waitFor(()->scene.lookup("#product-configurations") instanceof ComboBox<?> box&&box.getItems().size()==2);snapshot("02-configurations.png",1440,980);
            fx(()->{ComboBox<Product> box=(ComboBox<Product>)scene.lookup("#product-configurations");check(box.getItems().stream().allMatch(item->ProductSpecifications.value(item,"Model").equals("Studio")),"Configurations are grouped by brand and exact model");box.setValue(box.getItems().stream().filter(item->item.getId()==thirtytwo).findFirst().orElseThrow());return null;});
            waitFor(()->scene.lookup("#product-configurations") instanceof ComboBox<?> box&&box.getValue() instanceof Product item&&item.getId()==thirtytwo);press("detail-add");waitFor(()->((Button)scene.lookup("#nav-cart")).getText().contains("1"));
            login("customer","customer123");waitFor(()->scene.lookup("#cart-quantity-"+thirtytwo)!=null);snapshot("03-restored-cart.png",1440,980);
            check(services.products().savedCarts().restore(customer,null).cart().getItems().size()==1,"Guest selections are saved when signing in");click("Sign out");catalog(1);login("customer","customer123");waitFor(()->scene.lookup("#cart-quantity-"+thirtytwo)!=null);check(services.products().savedCarts().restore(customer,null).cart().getItems().size()==1,"Cart survives sign-out and sign-in");
            press("checkout");waitFor(()->scene.lookup("#checkout-page")!=null);text("recipient","Test Shopper");text("address","123 Test Street");text("city","Test City");text("postal-code","12345");press("place-order");waitFor(()->scene.lookup("#confirmation")!=null);click("View my orders");waitFor(()->scene.lookup("#order-cards") instanceof VBox box&&!box.lookupAll(".order-card").isEmpty());
            long order=services.orders().history(customer).getFirst().id();waitFor(()->scene.lookup("#order-tracking-"+order)!=null);check(services.products().savedCarts().restore(customer,null).cart().getItems().isEmpty(),"Checkout clears the saved cart");snapshot("04-customer-confirmed.png",1440,980);
            click("Sign out");login("admin","admin123");waitFor(()->scene.lookup("#admin-report-overview")!=null);press("admin-nav-orders");waitFor(()->scene.lookup("#admin-order-processor")!=null);advance(FulfillmentStatus.PACKED);advance(FulfillmentStatus.SHIPPED);snapshot("05-admin-shipped.png",1440,980);
            check(services.orders().get(customer,order).trackingNumber().equals("FORGE-TRACK-001"),"Admin tracking update is visible to the customer service");snapshot("06-admin-compact.png",960,760);
            fx(()->{Label address=(Label)scene.lookup("#process-delivery-address");check(address.getHeight()>=address.prefHeight(address.getWidth())-1,"Compact admin order keeps the full delivery address readable");return null;});
            press("admin-sign-out");login("customer","customer123");catalog(1);click("My orders");waitFor(()->scene.lookup("#tracking-number-"+order)!=null);snapshot("07-customer-tracking.png",1440,980);
            fx(()->{check(((TextField)scene.lookup("#tracking-number-"+order)).getText().contains("FORGE-TRACK-001"),"Customer sees the recorded carrier and tracking number");return null;});
            click("Sign out");login("admin","admin123");waitFor(()->scene.lookup("#admin-report-overview")!=null);press("nav-inventory");waitFor(()->scene.lookup("#admin-table") instanceof TableView<?> table&&table.getItems().size()==3);
            fx(()->{TableView<Product> table=(TableView<Product>)scene.lookup("#admin-table");table.getSelectionModel().select(table.getItems().stream().filter(item->item.getId()==gpu).findFirst().orElseThrow());return null;});confirmDelete(false);check(!services.products().search(admin,"Studio GPU",null).isEmpty(),"Cancelling deletion keeps the product");confirmDelete(true);waitFor(()->scene.lookup("#admin-table") instanceof TableView<?> table&&table.getItems().size()==2);check(services.orders().get(customer,order).items().size()==1,"Product deletion preserves existing receipts");snapshot("08-inventory-delete.png",1440,980);
            fx(()->{TableView<Product> table=(TableView<Product>)scene.lookup("#admin-table");table.getSelectionModel().selectFirst();return null;});press("edit-product");waitFor(()->scene.lookup("#spec-ram")!=null);snapshot("09-category-specification-editor.png",1440,980);
            fx(()->{check(scene.lookup("#spec-sku")!=null&&scene.lookup("#spec-storage")!=null,"Admin editor exposes identity and hardware fields");check(!((TextField)scene.lookup("#spec-ram")).getText().isBlank()&&((ComboBox<?>)scene.lookup("#spec-ram-unit")).getValue().equals("GB"),"Existing specifications populate the structured capacity and unit");return null;});
            System.out.println("COMMERCE UI PASS: "+checks+" assertions.");
        }finally{Platform.exit();}
    }
}