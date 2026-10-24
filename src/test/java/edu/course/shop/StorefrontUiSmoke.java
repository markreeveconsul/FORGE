package edu.course.shop;

import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import edu.course.shop.service.*;
import edu.course.shop.ui.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Theme/navigation/purchase integration checks against an isolated catalog; no cloud calls. */
public final class StorefrontUiSmoke {
    private static Scene scene;private static Path evidence;private static int checks;
    private static <T>T fx(Callable<T> work)throws Exception{FutureTask<T> task=new FutureTask<>(()->{if(scene!=null){scene.getRoot().applyCss();scene.getRoot().layout();}return work.call();});Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private static void waitFor(BooleanSupplier condition)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(!fx(condition::getAsBoolean)){if(System.nanoTime()>until)throw new AssertionError("Storefront state timed out");Thread.sleep(35);}}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;System.out.println("PASS "+message);}
    private static void press(String id)throws Exception{fx(()->{((Button)scene.lookup("#"+id)).fire();return null;});}
    private static void catalog()throws Exception{waitFor(()->scene.lookup("#product-grid") instanceof TilePane grid&&grid.getChildren().size()==5);}
    private static void layout(int width,int height)throws Exception{fx(()->{scene.getRoot().resize(width,height);for(int i=0;i<6;i++){scene.getRoot().applyCss();scene.getRoot().layout();}return null;});}
    private static void snapshot(String name)throws Exception{fx(()->{var image=scene.getRoot().snapshot(null,null);BufferedImage png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));ImageIO.write(png,"png",evidence.resolve(name).toFile());return null;});}
    private static byte[] image(String name)throws Exception{try(var input=StorefrontUiSmoke.class.getResourceAsStream("/images/"+name)){return input.readAllBytes();}}
    public static void main(String[] args)throws Exception{
        evidence=Path.of(args[0]);Files.createDirectories(evidence);DatabaseManager db=new DatabaseManager(evidence.resolve("storefront-"+System.nanoTime()+".db"));db.initialize();var pr=new ProductRepository();var orders=new OrderRepository();
        Services services=new Services(new AuthService(db,new UserRepository()),new ProductService(db,pr),new CheckoutService(db,pr,orders),new OrderService(db,orders),new AdminService(db,new AdminRepository()),new ProductReviewService(db,new ProductReviewRepository()));
        User admin=services.auth().login("admin","admin123");try(var c=db.open();var s=c.createStatement()){s.executeUpdate("DELETE FROM products");}
        String[] names={"FORGE RTX Studio Gaming PC","NVIDIA GeForce RTX 5060 Ti","MSI Titan 18 HX Dragon Edition","Studio Wireless Headphones","Mechanical Gaming Keyboard"};
        Category[] categories={Category.PC_SETS,Category.PC_PARTS,Category.LAPTOPS,Category.PERIPHERALS,Category.PERIPHERALS};
        String[] photos={"category-pc.png","category-gpu.png","category-laptop.png","headset-campaign.png","category-keyboard.png"};
        int[] stock={8,3,12,0,15};String[] prices={"1599.00","999.99","4789.99","79.90","129.00"};
        for(int i=0;i<names.length;i++)services.products().save(admin,0,names[i],categories[i],new BigDecimal(prices[i]),stock[i],true,"A synthetic catalog item for verifying the FORGE shopping experience.",image(photos[i]),List.of(image(photos[i])),"Model: UI fixture\nWarranty: Demo only");
        long gpu=services.products().search(admin,"NVIDIA",null).getFirst().getId();
        CountDownLatch ready=new CountDownLatch(1);Platform.startup(ready::countDown);ready.await();Platform.setImplicitExit(false);
        try{
            fx(()->{Motion.reducedProperty().set(true);Stage stage=new Stage();scene=new Scene(new StackPane(),1440,960);scene.getStylesheets().add(StorefrontUiSmoke.class.getResource("/shop.css").toExternalForm());stage.setScene(scene);new ShopWindow(stage,services).showStorefront();return null;});catalog();
            layout(1440,960);snapshot("01-catalog-light.png");
            fx(()->{check(scene.getRoot().getStyleClass().contains("forge-store"),"Storefront uses its own theme scope");check(scene.lookup("#store-category-row").isVisible(),"Categories have a dedicated row");return null;});
            for(int width:new int[]{960,1280,1600}){
                layout(width,900);
                fx(()->{Node search=scene.lookup("#header-search-slot"),profile=scene.lookup("#guest-profile"),header=scene.lookup("#store-header");var sb=search.localToScene(search.getLayoutBounds());var pb=profile.localToScene(profile.getLayoutBounds());check(sb.getWidth()>=200&&pb.getMaxX()<=width,"Search and account controls fit at "+width+"px");check(scene.lookup("#store-category-row").localToScene(scene.lookup("#store-category-row").getLayoutBounds()).getMinY()>=header.getBoundsInParent().getMaxY(),"Category navigation does not crowd search at "+width+"px");return null;});
            }
            layout(960,760);snapshot("02-catalog-compact.png");press("store-theme-toggle");layout(1440,960);snapshot("03-catalog-dark.png");
            fx(()->{check(scene.getRoot().getStyleClass().contains("store-dark"),"Dark Obsidian switches the storefront");Button save=(Button)scene.lookup("#save-"+gpu);Node glyph=save.lookup(".text");check(glyph!=null&&glyph.getLayoutBounds().getWidth()>10,"Save control has room for its visible heart icon");
                Region filter=(Region)scene.lookup("#toggle-filters");check(filter.getBackground().getFills().getFirst().getFill().equals(Color.web("#1C1917")),"Filter control follows the dark theme");
                Label title=(Label)scene.lookup(".collection-title");check(title.getTextFill().equals(Color.web("#FAFAF9")),"Dark theme keeps collection headings readable");return null;});
            press("toggle-filters");waitFor(()->scene.lookup("#catalog-filters")!=null);snapshot("04-filters-dark.png");press("close-filters");
            press("view-"+gpu);waitFor(()->scene.lookup("#product-detail")!=null);layout(1440,960);snapshot("05-product-dark.png");
            fx(()->{check(scene.getRoot().getStyleClass().contains("store-dark"),"Theme survives product navigation");return null;});
            press("zoom-photo");waitFor(()->scene.lookup("#image-viewer-panel")!=null);layout(1440,960);snapshot("06-viewer-dark.png");
            fx(()->{ImageView image=(ImageView)scene.lookup("#image-zoom-photo");check(image.isPreserveRatio(),"Enlarged product photo preserves its proportions");Node controls=scene.lookup("#image-zoom-controls"),viewport=scene.lookup("#image-zoom-viewport");check(Math.abs(controls.localToScene(controls.getLayoutBounds()).getCenterX()-viewport.localToScene(viewport.getLayoutBounds()).getCenterX())<3,"Zoom controls stay centered under the image");return null;});
            press("image-zoom-in");press("image-zoom-reset");fx(()->{check(((Label)scene.lookup("#image-zoom-level")).getText().equals("Fit"),"Zoom reset remains functional");return null;});press("image-close");
            press("detail-add");waitFor(()->((Button)scene.lookup("#nav-cart")).getText().contains("1"));press("nav-cart");waitFor(()->scene.lookup("#cart-drawer")!=null);press("drawer-review");waitFor(()->scene.lookup("#cart-page")!=null);layout(1440,960);snapshot("07-cart-dark.png");
            press("store-theme-toggle");snapshot("08-cart-light.png");press("checkout");waitFor(()->scene.lookup("#username")!=null);
            fx(()->{((TextField)scene.lookup("#username")).setText("customer");((PasswordField)scene.lookup("#password")).setText("customer123");((Button)scene.lookup("#login")).fire();return null;});catalog();press("nav-cart");press("drawer-review");waitFor(()->scene.lookup("#cart-page")!=null);press("checkout");waitFor(()->scene.lookup("#checkout-page")!=null);layout(1440,960);snapshot("09-checkout-light.png");
            fx(()->{Node action=scene.lookup("#place-order");check(action!=null&&action.localToScene(action.getBoundsInLocal()).getMaxY()<960,"Checkout keeps the order action visible");return null;});
            press("store-theme-toggle");snapshot("10-checkout-dark.png");
            fx(()->{Region card=(Region)scene.lookup("#checkout-page .card");check(card.getBackground().getFills().getFirst().getFill().equals(Color.web("#1C1917")),"Checkout surfaces switch with the dark theme");return null;});
            press("assistant-launcher");waitFor(()->scene.lookup("#assistant-panel").isVisible());
            fx(()->{((TextField)scene.lookup("#assistant-question")).setText("show me pc parts listed in the shop");((Button)scene.lookup("#assistant-send")).fire();return null;});
            waitFor(()->scene.lookup("#assistant-product-"+gpu)!=null);snapshot("11-assistant-dark.png");
            fx(()->{check(scene.lookup("#assistant-product-"+gpu)!=null,"Assistant still renders local catalog cards in the new theme");return null;});
            System.out.println("Storefront UI checks passed: "+checks);
        }finally{Platform.exit();}
    }
}