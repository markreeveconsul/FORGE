package edu.course.shop.ui;
import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import edu.course.shop.service.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.input.*;
import javafx.scene.paint.Color;
import javafx.stage.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Native popup, filter disclosure and motion integration checks; no API calls or production data. */
public final class InteractionSmoke {
    static Scene scene;static Stage stage;static StackPane root;static int checks;
    static <T>T fx(Callable<T> job)throws Exception{FutureTask<T> task=new FutureTask<>(()->{if(scene!=null){scene.getRoot().applyCss();scene.getRoot().layout();}return job.call();});Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    static void waitFor(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(!fx(condition::getAsBoolean)){if(System.nanoTime()>end)throw new AssertionError("Timed out waiting for UI");Thread.sleep(30);}}
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;System.out.println("PASS "+message);}
    static Parent popup(){return Window.getWindows().stream().filter(w->w.isShowing()&&w instanceof PopupWindow).map(w->w.getScene().getRoot()).findFirst().orElse(null);}
    static void shot(Parent node,Path path)throws Exception{var image=node.snapshot(null,null);BufferedImage png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));ImageIO.write(png,"png",path.toFile());}
    static double linear(double x){return x<=.04045?x/12.92:Math.pow((x+.055)/1.055,2.4);}
    static double luminance(Color c){return .2126*linear(c.getRed())+.7152*linear(c.getGreen())+.0722*linear(c.getBlue());}
    static void verifySurface(Parent popup){
        Region surface=(Region)popup.lookup(".forge-options");
        check(surface.getBackground()!=null&&!surface.getBackground().getFills().isEmpty(),"Menu has a painted surface");
        var fill=surface.getBackground().getFills().getFirst();Color background=(Color)fill.getFill(),text=(Color)((Label)surface.lookup(".menu-item .label")).getTextFill();
        double a=luminance(background),b=luminance(text);check((Math.max(a,b)+.05)/(Math.min(a,b)+.05)>=4.5,"Menu text contrast meets 4.5:1");
        check(fill.getRadii().getTopLeftHorizontalRadius()>=10,"Native menu uses rounded corners");
    }
    public static void main(String[] args)throws Exception{
        Path output=Path.of(args[0]);Files.createDirectories(output);DatabaseManager db=new DatabaseManager(output.resolve("interactions-"+System.nanoTime()+".db"));db.initialize();ProductService products=new ProductService(db,new ProductRepository());
        CountDownLatch ready=new CountDownLatch(1);Platform.startup(ready::countDown);ready.await();Platform.setImplicitExit(false);
        try{
            CatalogState state=new CatalogState();
            fx(()->{Motion.reducedProperty().set(false);root=new StackPane();root.getStyleClass().add("forge-store");root.getStylesheets().add(InteractionSmoke.class.getResource("/storefront.css").toExternalForm());Interactions.install(root);scene=new Scene(root,960,820);scene.getStylesheets().add(InteractionSmoke.class.getResource("/shop.css").toExternalForm());stage=new Stage();stage.setScene(scene);stage.show();CatalogView catalog=new CatalogView(new Guest(),new Cart(0),products,new CartService(products),()->{},()->{},p->{},new TextField(),state);root.getChildren().add(catalog);return null;});
            waitFor(()->scene.lookup("#product-grid")!=null);
            fx(()->{MenuButton more=(MenuButton)scene.lookup("#hero-category-more");check(more.isVisible(),"Compact categories show More");more.show();return null;});
            waitFor(()->popup()!=null&&popup().lookup(".forge-options")!=null&&popup().lookup(".forge-options").getOpacity()==1);
            fx(()->{Parent p=popup();p.applyCss();p.layout();check(p.lookup(".forge-options").getTranslateY()==0,"Menu entrance settles without displacement");MenuButton more=(MenuButton)scene.lookup("#hero-category-more");CheckMenuItem item=(CheckMenuItem)more.getItems().getFirst();check(item.getGraphic()!=null,"More options include category icons");verifySurface(p);shot(p,output.resolve("01-menu-light.png"));item.fire();more.hide();return null;});
            waitFor(()->state.getCategoryIndex()==4);
            fx(()->{MenuButton more=(MenuButton)scene.lookup("#hero-category-more");check(((CheckMenuItem)more.getItems().getFirst()).isSelected(),"Chosen hidden category has a checkmark");check(more.getAccessibleText().contains("Phones"),"More announces the selected category");check(scene.lookup("#category-active-indicator").isVisible(),"Active category underline remains visible");root.getStyleClass().add("store-dark");more.show();return null;});
            waitFor(()->popup()!=null&&popup().lookup(".forge-options-dark")!=null&&popup().lookup(".forge-options").getOpacity()==1);
            fx(()->{Parent p=popup();p.applyCss();p.layout();Label label=(Label)p.lookup(".menu-item .label");check(((Color)label.getTextFill()).getBrightness()>.75,"Dark menu text remains readable");verifySurface(p);shot(p,output.resolve("02-menu-dark.png"));javafx.event.Event.fireEvent(p.lookup(".forge-options"),new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.ESCAPE,false,false,false,false));return null;});
            waitFor(()->!((MenuButton)scene.lookup("#hero-category-more")).isShowing());check(true,"Escape closes the native category menu");
            fx(()->{ComboBox<String> combo=new ComboBox<>();combo.getItems().addAll("Name A–Z","Price low–high","Price high–low");combo.getSelectionModel().selectFirst();combo.setId("test-sort");VBox section=Interactions.section("Price range",new VBox(new TextField("100"),new CheckBox("In stock")));section.setId("test-section");VBox box=new VBox(16,combo,section,Ui.skeletonRows(2));box.setMaxWidth(380);root.getChildren().setAll(box);combo.show();return null;});
            waitFor(()->popup()!=null&&popup().lookup(".forge-options")!=null);
            fx(()->{Node options=popup().lookup(".forge-options");check(options.getStyleClass().contains("forge-options-dark"),"Sort popup follows dark mode");((ComboBox<?>)scene.lookup("#test-sort")).hide();Button disclosure=(Button)scene.lookup(".disclosure-toggle");disclosure.fire();disclosure.fire();disclosure.fire();return null;});
            waitFor(()->!scene.lookup(".disclosure-body").isManaged());check(true,"Rapid filter toggles settle fully collapsed");
            fx(()->{Motion.reducedProperty().set(true);((Button)scene.lookup(".disclosure-toggle")).fire();check(scene.lookup(".disclosure-body").isManaged(),"Reduced-motion expansion is immediate");check(scene.lookup(".skeleton-row").getParent().getOpacity()==1,"Reduced-motion skeletons remain static");ComboBox<?> combo=(ComboBox<?>)scene.lookup("#test-sort");combo.show();return null;});
            waitFor(()->popup()!=null&&popup().lookup(".forge-options")!=null);
            fx(()->{Node p=popup().lookup(".forge-options");check(p.getOpacity()==1&&p.lookup(".forge-options").getTranslateY()==0,"Reduced-motion popup is immediately visible");((ComboBox<?>)scene.lookup("#test-sort")).hide();Motion.reducedProperty().set(false);((Button)scene.lookup(".disclosure-toggle")).fire();Motion.reducedProperty().set(true);check(!scene.lookup(".disclosure-body").isManaged(),"Changing motion preference settles an active collapse");shot(root,output.resolve("03-disclosure-dark.png"));return null;});
            System.out.println("INTERACTION SMOKE PASS: "+checks+" assertions");
        }finally{Platform.exit();}
    }
}