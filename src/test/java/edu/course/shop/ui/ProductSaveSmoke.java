package edu.course.shop.ui;
import edu.course.shop.*;
import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import edu.course.shop.service.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Real admin save failures, draft recovery and database results; isolated DB, no network. */
public final class ProductSaveSmoke {
    static Scene scene;static Stage stage;static int checks;
    static <T>T fx(Callable<T> job)throws Exception{FutureTask<T> task=new FutureTask<>(()->{if(scene!=null){scene.getRoot().applyCss();scene.getRoot().layout();}return job.call();});Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    static void waitFor(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(!fx(condition::getAsBoolean)){if(System.nanoTime()>end)throw new AssertionError("Timed out waiting for product editor");Thread.sleep(30);}}
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;System.out.println("PASS "+message);}
    static void press(String id)throws Exception{fx(()->{((Button)scene.lookup("#"+id)).fire();return null;});}
    static void text(String id,String value)throws Exception{fx(()->{((TextInputControl)scene.lookup("#"+id)).setText(value);return null;});}
    static void shot(Path path)throws Exception{fx(()->{var image=scene.getRoot().snapshot(null,null);BufferedImage png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));ImageIO.write(png,"png",path.toFile());return null;});}
    static Label feedback(){return (Label)scene.lookup("#product-save-feedback");}
    static void fill(String name)throws Exception{text("product-name",name);text("product-price","399.99");text("product-stock","12");fx(()->{((ComboBox<Category>)scene.lookup("#product-category")).setValue(Category.PC_PARTS);return null;});}
    public static void main(String[] args)throws Exception{
        Path output=Path.of(args[0]);Files.createDirectories(output);DatabaseManager db=new DatabaseManager(output.resolve("product-save-"+System.nanoTime()+".db"));db.initialize();ProductRepository p=new ProductRepository();OrderRepository o=new OrderRepository();
        Services services=new Services(new AuthService(db,new UserRepository()),new ProductService(db,p),new CheckoutService(db,p,o),new OrderService(db,o),new AdminService(db,new AdminRepository()),new ProductReviewService(db,new ProductReviewRepository()));User admin=services.auth().login("admin","admin123");try(var c=db.open();var s=c.createStatement()){s.executeUpdate("DELETE FROM products");}
        CountDownLatch ready=new CountDownLatch(1);Platform.startup(ready::countDown);ready.await();Platform.setImplicitExit(false);
        try{
            fx(()->{Motion.reducedProperty().set(true);stage=new Stage();scene=new Scene(new StackPane(),1280,900);scene.getStylesheets().add(ProductSaveSmoke.class.getResource("/shop.css").toExternalForm());stage.setScene(scene);new ShopWindow(stage,services).showLogin();scene.getRoot().applyCss();scene.getRoot().layout();((TextField)scene.lookup("#username")).setText("admin");((PasswordField)scene.lookup("#password")).setText("admin123");((Button)scene.lookup("#login")).fire();return null;});
            waitFor(()->scene.lookup("#admin-report-overview")!=null);press("nav-inventory");waitFor(()->scene.lookup("#admin-table")!=null);press("new-product");waitFor(()->scene.lookup("#save-product")!=null);fill("Save fixture GPU");text("spec-vram","eight");text("spec-sku","SAVE-001");text("product-price","wrong");press("save-product");
            fx(()->{check(scene.lookup("#admin-product-modal")!=null,"Invalid price keeps the editor open");ListCell<?> unit=((ComboBox<?>)scene.lookup("#spec-vram-unit")).getButtonCell();check(((javafx.scene.text.Text)unit.lookup(".text")).getText().equals("GB"),"Capacity unit is visible without ellipsis");check(!((Label)scene.lookup("#price-error")).getText().isBlank(),"Required price error appears inside the editor");check(((Label)scene.lookup("#spec-vram-error")).getStyleClass().contains("field-warning"),"Custom VRAM text has a nonblocking formatting hint");check(scene.getFocusOwner()==scene.lookup("#product-price"),"Invalid price field receives keyboard focus");check(((TextField)scene.lookup("#product-name")).getText().equals("Save fixture GPU"),"Failed validation retains the draft");return null;});
            check(services.products().search(admin,"Save fixture",null).isEmpty(),"Invalid product was not inserted");shot(output.resolve("01-price-error-and-format-hint.png"));text("product-price","399.99");press("save-product");press("save-product");press("save-product");waitFor(()->scene.lookup("#admin-product-modal")==null);
            var saved=services.products().search(admin,"Save fixture GPU",null);check(saved.size()==1,"Repeated save clicks create exactly one product");check(ProductSpecifications.value(saved.getFirst(),"VRAM").equals("eight"),"Custom VRAM text is saved exactly as entered");check(saved.getFirst().getStock()==12,"Stock persists with the product");
            press("new-product");fill("Save fixture second");text("spec-sku","SAVE-001");press("save-product");waitFor(()->feedback()!=null&&feedback().getText().contains("SKU"));
            fx(()->{check(scene.lookup("#admin-product-modal")!=null,"Database validation keeps the modal open");check(!scene.lookup("#save-product").isDisabled(),"Failed asynchronous save unlocks the form");check(((TextField)scene.lookup("#product-name")).getText().equals("Save fixture second"),"Database validation preserves the draft");return null;});shot(output.resolve("02-duplicate-sku-error.png"));text("spec-sku","SAVE-002");text("spec-storage","2");fx(()->{((ComboBox<String>)scene.lookup("#spec-storage-unit")).setValue("TB");return null;});press("save-product");waitFor(()->scene.lookup("#admin-product-modal")==null);saved=services.products().search(admin,"Save fixture second",null);check(saved.size()==1&&ProductSpecifications.value(saved.getFirst(),"Storage").equals("2 TB"),"Corrected draft saves using the chosen storage unit");
            press("new-product");fill("Save fixture no specs");press("save-product");waitFor(()->scene.lookup("#admin-product-modal")==null);check(services.products().search(admin,"Save fixture no specs",null).size()==1,"Specifications remain optional when adding a product");
            press("new-product");fill("Save fixture units");text("spec-vram","16 gb");text("spec-ram","32");fx(()->{((ComboBox<String>)scene.lookup("#spec-ram-unit")).setValue("MB");return null;});press("save-product");waitFor(()->scene.lookup("#admin-product-modal")==null);Product units=services.products().search(admin,"Save fixture units",null).getFirst();check(ProductSpecifications.value(units,"VRAM").equals("16 GB")&&ProductSpecifications.value(units,"RAM").equals("32 MB"),"Pasted units and dropdown units are normalized correctly");
            System.out.println("PRODUCT SAVE SMOKE PASS: "+checks+" assertions");
        }finally{Platform.exit();}
    }
}