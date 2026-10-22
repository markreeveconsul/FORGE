package edu.course.shop.ui;
import edu.course.shop.*;
import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import edu.course.shop.service.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Actual table-cell layout and rendered-text contrast; isolated photo fixtures, no cloud requests. */
public final class InventoryRenderingSmoke {
    private static Scene scene;private static int checks;
    private static <T>T fx(Callable<T> action)throws Exception{FutureTask<T> task=new FutureTask<>(()->{if(scene!=null){scene.getRoot().applyCss();scene.getRoot().layout();}return action.call();});Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private static void waitFor(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(!fx(condition::getAsBoolean)){if(System.nanoTime()>end)throw new AssertionError("Inventory did not reach expected state");Thread.sleep(30);}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;System.out.println("PASS "+message);}
    private static void press(String id)throws Exception{fx(()->{((Button)scene.lookup("#"+id)).fire();return null;});}
    private static void layout(int width)throws Exception{fx(()->{scene.getRoot().resize(width,900);for(int i=0;i<6;i++){scene.getRoot().applyCss();scene.getRoot().layout();}return null;});}
    private static void snapshot(Path file)throws Exception{fx(()->{var image=scene.getRoot().snapshot(null,null);BufferedImage png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));ImageIO.write(png,"png",file.toFile());return null;});}
    private static byte[] photo(int width,int height)throws Exception{BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(new java.awt.Color(224,231,235));g.fillRect(0,0,width,height);g.setColor(new java.awt.Color(37,81,101));g.fillRoundRect(width/8,height/8,width*3/4,height*3/4,18,18);g.dispose();var out=new java.io.ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();}
    private static double linear(double x){return x<=.04045?x/12.92:Math.pow((x+.055)/1.055,2.4);}
    private static double luminance(Color c){return .2126*linear(c.getRed())+.7152*linear(c.getGreen())+.0722*linear(c.getBlue());}
    private static double contrast(Color a,Color b){double x=luminance(a),y=luminance(b);return(Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    private static void verifySelected()throws Exception{
        fx(()->{
            TableRow<?> row=scene.getRoot().lookupAll(".table-row-cell").stream().filter(n->n instanceof TableRow<?> r&&r.isSelected()&&!r.isEmpty()).map(n->(TableRow<?>)n).findFirst().orElseThrow();
            ImageView photo=(ImageView)row.lookup(".product-photo");check(photo!=null&&photo.getBoundsInLocal().getHeight()>=20,"Selected product thumbnail has visible height");
            double expected=photo.getImage().getWidth()/photo.getImage().getHeight(),actual=photo.getBoundsInLocal().getWidth()/photo.getBoundsInLocal().getHeight();check(Math.abs(expected-actual)<.01,"Inventory photo retains its original aspect ratio");
            Region well=(Region)row.lookup(".inventory-thumbnail");check(well.getWidth()==52&&well.getHeight()==52,"Thumbnail well remains a fixed square inside the row");
            int badges=0;for(Node node:row.lookupAll(".workspace-pill")){
                Label badge=(Label)node;Text text=(Text)badge.lookup(".text");Color foreground=(Color)text.getFill(),background=(Color)badge.getBackground().getFills().getFirst().getFill();
                check(text.getText().equals(badge.getText()),"Selected badge label is displayed in full");
                check(foreground.equals(badge.getTextFill()),"Selection preserves the badge's actual text color");check(contrast(foreground,background)>=4.5,"Selected badge text remains readable against its background");badges++;
            }check(badges==2,"Status and content badges are both present");
            Label name=(Label)row.lookup(".admin-product-name");Text text=(Text)name.lookup(".text");check(text.getFill().equals(name.getTextFill()),"Selected product name keeps its intended text color");
            return null;
        });
    }
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);Files.createDirectories(root);DatabaseManager db=new DatabaseManager(root.resolve("inventory-"+System.nanoTime()+".db"));db.initialize();var p=new ProductRepository();var o=new OrderRepository();
        Services services=new Services(new AuthService(db,new UserRepository()),new ProductService(db,p),new CheckoutService(db,p,o),new OrderService(db,o),new AdminService(db,new AdminRepository()),new ProductReviewService(db,new ProductReviewRepository()));User admin=services.auth().login("admin","admin123");
        try(var c=db.open();var s=c.createStatement()){s.executeUpdate("DELETE FROM products");}
        for(int i=0;i<7;i++)services.products().save(admin,0,"Fixture product "+i+(i==2?" with a deliberately long descriptive model name":""),Category.PC_PARTS,new BigDecimal("999.99"),i+1,i!=1,i%2==0?"Synthetic product fixture":"",photo(i%2==0?600:400,i%2==0?400:600),List.of(),i%2==0?"VRAM: 8 GB":"");
        CountDownLatch ready=new CountDownLatch(1);Platform.startup(ready::countDown);ready.await();Platform.setImplicitExit(false);
        try{
            fx(()->{Motion.reducedProperty().set(true);Stage stage=new Stage();scene=new Scene(new StackPane(),1440,900);scene.getStylesheets().add(InventoryRenderingSmoke.class.getResource("/shop.css").toExternalForm());stage.setScene(scene);new ShopWindow(stage,services).showLogin();scene.getRoot().applyCss();scene.getRoot().layout();((TextField)scene.lookup("#username")).setText("admin");((PasswordField)scene.lookup("#password")).setText("admin123");((Button)scene.lookup("#login")).fire();return null;});
            waitFor(()->scene.lookup("#admin-report-overview")!=null);press("nav-inventory");waitFor(()->scene.lookup("#admin-table") instanceof TableView<?> t&&t.getItems().size()==7);layout(1440);
            fx(()->{((TableView<?>)scene.lookup("#admin-table")).getSelectionModel().select(0);return null;});layout(1440);verifySelected();snapshot(root.resolve("01-selected-light.png"));
            press("admin-theme-toggle");layout(1440);verifySelected();snapshot(root.resolve("02-selected-dark.png"));
            fx(()->{TableView<?> table=(TableView<?>)scene.lookup("#admin-table");table.getSelectionModel().select(1);table.scrollTo(1);return null;});layout(1440);verifySelected();snapshot(root.resolve("03-warning-inactive-dark.png"));
            layout(960);verifySelected();snapshot(root.resolve("04-selected-compact.png"));
            fx(()->{Label header=(Label)scene.getRoot().lookupAll(".column-header .label").stream().filter(n->n instanceof Label l&&l.getText().equals("Stock")).findFirst().orElseThrow();check(((Text)header.lookup(".text")).getText().equals("Stock"),"Compact stock heading is not truncated");TableView<?> table=(TableView<?>)scene.lookup("#admin-table");table.scrollTo(6);table.getSelectionModel().select(6);return null;});layout(960);verifySelected();snapshot(root.resolve("05-recycled-row.png"));
            System.out.println("INVENTORY RENDERING PASS: "+checks+" assertions.");
        }finally{Platform.exit();}
    }
}