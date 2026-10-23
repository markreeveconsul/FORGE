package edu.course.shop;

import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import edu.course.shop.service.*;
import edu.course.shop.ui.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Real JavaFX focus/scroll and sign-in regressions using only a temporary database. */
public final class CustomerNavigationUiSmoke {
    private static Scene scene;private static Stage stage;private static int checks;
    private static <T>T fx(Callable<T> work)throws Exception{FutureTask<T> task=new FutureTask<>(()->{if(scene!=null){scene.getRoot().applyCss();scene.getRoot().layout();}return work.call();});Platform.runLater(task);return task.get(15,TimeUnit.SECONDS);}
    private static void waitFor(BooleanSupplier condition)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(!fx(condition::getAsBoolean)){if(System.nanoTime()>until)throw new AssertionError("Customer navigation timed out");Thread.sleep(25);}}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;System.out.println("PASS "+message);}
    private static void press(String id)throws Exception{fx(()->{((Button)scene.lookup("#"+id)).fire();return null;});}
    private static void catalog()throws Exception{waitFor(()->scene.lookup("#product-grid") instanceof TilePane grid&&grid.getChildren().size()==18);Thread.sleep(150);}
    private static void login()throws Exception{press("account");fx(()->{((TextField)scene.lookup("#username")).setText("customer");((PasswordField)scene.lookup("#password")).setText("customer123");((Button)scene.lookup("#login")).fire();return null;});catalog();}
    private static void signOut()throws Exception{fx(()->{scene.getRoot().lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals("Sign out")).map(n->(Button)n).findFirst().orElseThrow().fire();return null;});catalog();}
    private static ScrollPane scroll(){return (ScrollPane)scene.lookup("#catalog-scroll");}
    private static Button target()throws Exception{
        fx(()->{scroll().setVvalue(.35);return null;});Thread.sleep(150);
        Button heart=fx(()->{var viewport=scroll().localToScene(scroll().getViewportBounds());return scene.getRoot().lookupAll(".save-heart").stream().filter(n->{var bounds=n.localToScene(n.getBoundsInLocal());return bounds.getMinY()>viewport.getMinY()+80&&bounds.getMaxY()<viewport.getMaxY()-80;}).map(n->(Button)n).findFirst().orElseThrow();});
        fx(()->{heart.requestFocus();return null;});Thread.sleep(150);return heart;
    }
    private static void stable(Button heart,double before,String label)throws Exception{Thread.sleep(200);fx(()->{double range=scroll().getContent().getLayoutBounds().getHeight()-scroll().getViewportBounds().getHeight();check(Math.abs(scroll().getVvalue()-before)*range<2,label+" keeps the catalog scroll position");check(scene.getFocusOwner()==heart,label+" keeps keyboard focus on the heart");return null;});}
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);Files.createDirectories(root);DatabaseManager db=new DatabaseManager(root.resolve("customer-nav-"+System.nanoTime()+".db"));db.initialize();ProductRepository products=new ProductRepository();OrderRepository orders=new OrderRepository();
        Services services=new Services(new AuthService(db,new UserRepository()),new ProductService(db,products),new CheckoutService(db,products,orders),new OrderService(db,orders),new AdminService(db,new AdminRepository()),new ProductReviewService(db,new ProductReviewRepository()));
        User admin=services.auth().login("admin","admin123"),customer=services.auth().login("customer","customer123");
        try(var c=db.open();var s=c.createStatement()){s.executeUpdate("DELETE FROM products");}
        for(int i=0;i<18;i++)services.products().save(admin,0,String.format("Navigation laptop %02d",i),Category.LAPTOPS,new BigDecimal("100.00"),8,true);
        long cartId=services.products().search(admin,"00",null).getFirst().getId();
        CountDownLatch ready=new CountDownLatch(1);Platform.startup(ready::countDown);ready.await();Platform.setImplicitExit(false);
        try{
            fx(()->{Motion.reducedProperty().set(true);stage=new Stage();scene=new Scene(new StackPane(),1200,760);scene.getStylesheets().add(CustomerNavigationUiSmoke.class.getResource("/shop.css").toExternalForm());stage.setScene(scene);new ShopWindow(stage,services).showStorefront();stage.show();return null;});catalog();
            Button guest=target();double before=fx(()->scroll().getVvalue());fx(()->{guest.fire();return null;});stable(guest,before,"Guest favorite");
            login();check(fx(()->scene.lookup("#cart-page")==null&&scroll().getVvalue()<.01),"Empty-cart sign-in opens the main shop at the top");
            Button heart=target();long id=Long.parseLong(heart.getId().substring("save-".length()));boolean initiallySaved=services.accounts().saved(customer).contains(id);
            for(boolean reduced:new boolean[]{true,false}){
                fx(()->{Motion.reducedProperty().set(reduced);heart.requestFocus();return null;});Thread.sleep(150);before=fx(()->scroll().getVvalue());
                try(var lock=db.open();var statement=lock.createStatement()){
                    statement.execute("BEGIN IMMEDIATE");
                    fx(()->{heart.fire();heart.fire();check(!heart.isDisabled(),"Pending favorite remains enabled for focus (reduced="+reduced+")");check(heart.getAccessibleText().startsWith("Updating saved item"),"Pending save exposes accessible busy feedback");return null;});
                    stable(heart,before,"Pending customer favorite");statement.execute("COMMIT");
                }
                waitFor(()->!heart.getAccessibleText().startsWith("Updating saved item"));stable(heart,before,"Completed customer favorite");
                check(services.accounts().saved(customer).contains(id)==!initiallySaved,"Favorite change persists once despite repeated activation");initiallySaved=!initiallySaved;
            }
            // Merge a guest cart, restore it again, then exercise revalidation notices.
            signOut();press("add-"+cartId);waitFor(()->((Button)scene.lookup("#nav-cart")).getText().contains("1"));login();
            check(fx(()->scene.lookup("#cart-page")==null&&((Button)scene.lookup("#nav-cart")).getText().contains("1")),"Guest-cart merge keeps the main shop open and updates the cart count");
            check(services.products().savedCarts().restore(customer,null).cart().getItems().getFirst().getQuantity()==1,"Merged quantity is persisted");
            signOut();login();check(fx(()->scene.lookup("#cart-page")==null&&((Button)scene.lookup("#nav-cart")).getText().contains("1")),"Returning customer with a saved cart lands in the main shop");
            press("nav-cart");press("drawer-review");waitFor(()->scene.lookup("#cart-quantity-"+cartId)!=null);check(true,"Customer can explicitly open the restored cart");
            signOut();Product previous=services.products().get(cartId);services.products().updateInventoryNumbers(admin,previous,new BigDecimal("110.00"),previous.getStock());login();
            check(fx(()->scene.lookup("#cart-page")==null&&((Label)scene.lookup("#cart-toast-message")).getText().contains("price updated")),"Cart revalidation notice appears on the shop without redirecting");
            System.out.println("Customer navigation checks passed: "+checks);
        }finally{Platform.exit();}
    }
}