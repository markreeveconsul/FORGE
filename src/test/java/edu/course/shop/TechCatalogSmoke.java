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
import javafx.scene.input.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** New-feature smoke checks on a shown desktop window and an isolated synthetic catalog. */
public final class TechCatalogSmoke {
    private static Scene scene;private static Stage stage;private static Path evidence;private static int checks;
    private static <T> T fx(Callable<T> action)throws Exception{FutureTask<T> task=new FutureTask<>(()->{if(scene!=null){scene.getRoot().applyCss();scene.getRoot().layout();}return action.call();});Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private static void waitFor(BooleanSupplier test)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(!fx(()->test.getAsBoolean())){if(System.nanoTime()>end)throw new AssertionError("Condition timed out");Thread.sleep(40);}}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void idle()throws Exception{waitFor(()->!scene.getRoot().isDisabled());}
    private static void catalog()throws Exception{waitFor(()->scene.lookup("#product-grid") instanceof TilePane p&&!p.getChildren().isEmpty()&&!scene.getRoot().isDisabled());}
    private static void press(String id)throws Exception{fx(()->{((Button)scene.lookup("#"+id)).fire();return null;});}
    private static void text(String id,String value)throws Exception{fx(()->{((TextInputControl)scene.lookup("#"+id)).setText(value);return null;});}
    private static void click(String value)throws Exception{fx(()->{scene.getRoot().lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals(value)).map(n->(Button)n).findFirst().orElseThrow().fire();return null;});}
    private static String label(String id)throws Exception{return fx(()->((Label)scene.lookup("#"+id)).getText());}
    private static void resize(double width,double height)throws Exception {
        fx(()->{stage.setWidth(width+stage.getWidth()-scene.getWidth());stage.setHeight(height+stage.getHeight()-scene.getHeight());return null;});
        waitFor(()->Math.abs(scene.getWidth()-width)<2&&Math.abs(scene.getHeight()-height)<2);
    }
    private static void screenshot(String name)throws Exception{
        waitFor(()->scene.getRoot().lookupAll(".page").stream().allMatch(n->n.getOpacity()==1));
        fx(()->{write(scene.getRoot(),evidence.resolve(name));return null;});
    }
    private static void write(Node node,Path file)throws Exception{var im=node.snapshot(null,null);BufferedImage out=new BufferedImage((int)im.getWidth(),(int)im.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<out.getHeight();y++)for(int x=0;x<out.getWidth();x++)out.setRGB(x,y,im.getPixelReader().getArgb(x,y));ImageIO.write(out,"png",file.toFile());}
    private static DialogPane dialog() {return Window.getWindows().stream().filter(w->w.isShowing()&&w!=stage&&w.getScene().getRoot() instanceof DialogPane).map(w->(DialogPane)w.getScene().getRoot()).findFirst().orElse(null);}
    private static void openDialog(String id)throws Exception{fx(()->{Platform.runLater(()->((Button)scene.lookup("#"+id)).fire());return null;});waitFor(()->dialog()!=null);}
    private static void closeDialog()throws Exception{fx(()->{((Button)dialog().lookupButton(ButtonType.CLOSE)).fire();return null;});waitFor(()->dialog()==null);}
    private static byte[] photo(int color)throws Exception{BufferedImage img=new BufferedImage(1200,900,BufferedImage.TYPE_INT_RGB);var g=img.createGraphics();g.setColor(new java.awt.Color(237,238,239));g.fillRect(0,0,1200,900);g.setColor(new java.awt.Color(color));g.fillRoundRect(350,150,500,600,60,60);g.setColor(java.awt.Color.WHITE);g.drawString("SYNTHETIC PRODUCT FIXTURE",465,460);g.dispose();var bytes=new java.io.ByteArrayOutputStream();ImageIO.write(img,"png",bytes);return bytes.toByteArray();}
    public static void main(String[] args)throws Exception {
        evidence=Path.of(args[0]);Files.createDirectories(evidence);DatabaseManager db=new DatabaseManager(Path.of(args[1]));db.initialize();
        ProductRepository pr=new ProductRepository();OrderRepository or=new OrderRepository();
        Services services=new Services(new AuthService(db,new UserRepository()),new ProductService(db,pr),new CheckoutService(db,pr,or),new OrderService(db,or),new edu.course.shop.service.AdminService(db,new edu.course.shop.persistence.AdminRepository()),new edu.course.shop.service.ProductReviewService(db,new edu.course.shop.persistence.ProductReviewRepository()));
        CountDownLatch ready=new CountDownLatch(1);Platform.startup(ready::countDown);ready.await();Platform.setImplicitExit(false);
        try {
            fx(()->{Motion.reducedProperty().set(true);stage=new Stage();scene=new Scene(new StackPane(),1280,860);scene.getStylesheets().add(TechCatalogSmoke.class.getResource("/shop.css").toExternalForm());stage.setScene(scene);stage.show();new ShopWindow(stage,services).showStorefront();return null;});catalog();
            check(fx(()->((TilePane)scene.lookup("#product-grid")).getChildren().size())==2,"Only ready tech products appear in storefront");
            check(fx(()->((ComboBox<?>)scene.lookup("#category")).getItems().size())==7,"Filter has All categories plus six tech categories");
            check(fx(()->((FlowPane)scene.lookup("#collection-links")).getChildren().size())==6,"Discovery has six tech cards");
            check(fx(()->scene.lookup("#collection-home")==null&&scene.lookup("#collection-clothing")==null),"Retired collections are absent from discovery");
            check(fx(()->scene.lookup("#tech-category-nav").isVisible()),"Wide header exposes category navigation");
            check(fx(()->scene.lookup("#tech-category-nav").lookupAll(".button").size())==7,"Wide category row has all six choices plus Shop all");
            screenshot("01-tech-storefront.png");
            for(Category category:Category.shopping()) {
                press("collection-"+category.name().toLowerCase(Locale.ROOT));idle();
                check(fx(()->((ComboBox<?>)scene.lookup("#category")).getSelectionModel().getSelectedIndex())==category.shoppingIndex(),"Discovery routes to "+category.label());
                int expected=services.products().search(new Guest(),"",category).size();
                check(fx(()->{Node grid=scene.lookup("#product-grid");return expected==0?scene.getRoot().lookupAll(".empty-state").size()==1:grid instanceof TilePane tiles&&tiles.getChildren().size()==expected;}),"Collection results match "+category.label());
                press("clear-active-filters");catalog();
            }
            fx(()->{ScrollPane scroll=(ScrollPane)scene.lookup("#catalog-scroll");Node links=scene.lookup("#collection-links").getParent();double start=links.getBoundsInParent().getMinY();scroll.setVvalue(start/(scroll.getContent().getLayoutBounds().getHeight()-scroll.getViewportBounds().getHeight()));return null;});
            screenshot("02-find-your-next-upgrade.png");
            check(fx(()->scene.lookup("#collection-links").lookupAll(".collection-artwork").size())==6,"All six tech cards have offline illustrations");
            resize(960,680);
            fx(()->{ScrollPane scroll=(ScrollPane)scene.lookup("#catalog-scroll");Node links=scene.lookup("#collection-links").getParent();double start=links.getBoundsInParent().getMinY();scroll.setVvalue(start/(scroll.getContent().getLayoutBounds().getHeight()-scroll.getViewportBounds().getHeight()));return null;});
            screenshot("03-tech-collections-compact.png");
            check(fx(()->scene.lookup("#compact-shop-menu").isVisible()&&!scene.lookup("#tech-category-nav").isManaged()),"Compact header uses accessible Shop menu");
            check(fx(()->((MenuButton)scene.lookup("#compact-shop-menu")).getItems().size())==7,"Compact Shop menu has all six tech choices");
            check(fx(()->{for(Node tile:((FlowPane)scene.lookup("#collection-links")).getChildren()){var bounds=tile.localToScene(tile.getBoundsInLocal());if(bounds.getMinX()<0||bounds.getMaxX()>scene.getWidth())return false;}return true;}),"Compact collection cards fit viewport width");
            resize(1280,860);press("account");text("username","admin");text("password","admin123");press("login");
            waitFor(()->scene.lookup("#admin-table") instanceof TableView<?> table&&table.getItems().size()==6&&!scene.getRoot().isDisabled());
            check(fx(()->((TableView<Product>)scene.lookup("#admin-table")).getItems().stream().filter(p->!p.isActive()).count())==4,"Four old products stay inactive in admin inventory");
            screenshot("04-retained-inventory.png");
            fx(()->{TableView<Product> table=(TableView<Product>)scene.lookup("#admin-table");table.getSelectionModel().select(table.getItems().stream().filter(p->p.getId()==3).findFirst().orElseThrow());return null;});press("edit-product");
            check(fx(()->scene.lookup("#retired-category-notice").isVisible()),"Retained item explains how to replace and republish it");
            check(fx(()->((ComboBox<?>)scene.lookup("#product-category")).getItems().size())==6,"Editor offers six tech categories only");
            check(fx(()->((ComboBox<?>)scene.lookup("#product-category")).getValue()==null&&((ComboBox<?>)scene.lookup("#product-category")).getButtonCell().getText().equals("Choose a tech category")),"Retired editor prompts for a replacement category instead of an empty legacy selection");
            screenshot("05-replace-old-product.png");
            fx(()->{((CheckBox)scene.lookup("#product-active")).setSelected(true);return null;});press("save-product");
            check(!label("category-error").isEmpty(),"Retired item cannot go on sale before recategorization");
            fx(()->{((ComboBox<Category>)scene.lookup("#product-category")).setValue(Category.LAPTOPS);((CheckBox)scene.lookup("#product-active")).setSelected(false);return null;});
            text("product-name","Replacement laptop");text("product-price","899.00");press("save-product");waitFor(()->scene.lookup("#admin-table").isVisible()&&!scene.getRoot().isDisabled());
            check(services.products().get(3).getCategory()==Category.LAPTOPS&&!services.products().get(3).isActive(),"Replacement draft stays off storefront until enabled");
            press("edit-product");fx(()->{((CheckBox)scene.lookup("#product-active")).setSelected(true);return null;});press("save-product");waitFor(()->scene.lookup("#admin-table").isVisible()&&!scene.getRoot().isDisabled());
            check(services.products().search(new Guest(),"Replacement laptop",Category.LAPTOPS).size()==1,"Ready replacement becomes visible in its new tech category");
            System.out.println("TECH CATALOG SMOKE PASS: "+checks+" assertions, six collection routes, empty states, compact navigation, retained inventory and republishing; five screenshots.");
        }finally{Platform.exit();}
    }
}
