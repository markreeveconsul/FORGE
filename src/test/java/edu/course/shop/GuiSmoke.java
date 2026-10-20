package edu.course.shop;
import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import edu.course.shop.service.*;
import edu.course.shop.ui.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Exercises the real controls with a fresh SQLite file. Does not use the user's store database. */
public final class GuiSmoke {
    private static Scene scene;
    private static Path evidence;
    private static int checks;
    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task=new FutureTask<>(()->{if(scene!=null){scene.getRoot().applyCss();scene.getRoot().layout();}return action.call();});Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);
    }
    private static void waitFor(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(!fx(()->{scene.getRoot().applyCss();scene.getRoot().layout();return condition.getAsBoolean();})){
            if(System.nanoTime()>deadline)throw new AssertionError("GUI condition timed out");
            Thread.sleep(50);
        }
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Button button(String text){
        return scene.getRoot().lookupAll(".button").stream().filter(n->n instanceof Button b && b.getText().equals(text))
            .map(n->(Button)n).findFirst().orElseThrow();
    }
    private static void click(String text)throws Exception{fx(()->{button(text).fire();return null;});}
    private static void press(String id)throws Exception{
        fx(()->{((Button)scene.lookup("#"+id)).fire();return null;});
        if(id.equals("nav-cart"))fx(()->{((Button)scene.lookup("#drawer-review")).fire();return null;});
    }
    private static void text(String id,String value)throws Exception{fx(()->{((TextInputControl)scene.lookup("#"+id)).setText(value);return null;});}
    private static String label(String id)throws Exception{return fx(()->((Label)scene.lookup("#"+id)).getText());}
    private static void idle()throws Exception{waitFor(()->!scene.getRoot().isDisabled());}
    private static void catalog()throws Exception{waitFor(()->scene.lookup("#product-grid") instanceof TilePane p && !p.getChildren().isEmpty()&&!scene.getRoot().isDisabled());}
    private static void screenshot(String name,int width,int height)throws Exception{
        waitFor(()->scene.getRoot().lookupAll(".page").stream().allMatch(node->node.getOpacity()==1));
        fx(()->{
            scene.getRoot().resize(width,height);scene.getRoot().applyCss();scene.getRoot().layout();
            var image=scene.getRoot().snapshot(null,null);
            BufferedImage png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));
            ImageIO.write(png,"png",evidence.resolve(name).toFile());return null;
        });
    }
    private static void login(String user,String password)throws Exception{text("username",user);text("password",password);press("login");}
    public static void main(String[] args)throws Exception{
        evidence=Path.of(args[0]);Files.createDirectories(evidence);
        DatabaseManager db=new DatabaseManager(Path.of(args[1]));db.initialize();
        ProductRepository pr=new ProductRepository();OrderRepository or=new OrderRepository();
        Services services=new Services(new AuthService(db,new UserRepository()),new ProductService(db,pr),
            new CheckoutService(db,pr,or),new OrderService(db,or),new edu.course.shop.service.AdminService(db,new edu.course.shop.persistence.AdminRepository()));
        User admin=services.auth().login("admin","admin123");
        TechTestCatalog.prepare(services.products(),admin);
        Product lamp=services.products().get(6);
        services.products().save(admin,6,lamp.getName(),lamp.getCategory(),lamp.getPrice(),0,true);
        CountDownLatch ready=new CountDownLatch(1);Platform.startup(ready::countDown);ready.await();Platform.setImplicitExit(false);
        try{
            fx(()->{
                Stage stage=new Stage();scene=new Scene(new StackPane(),1280,860);
                scene.getStylesheets().add(GuiSmoke.class.getResource("/shop.css").toExternalForm());
                stage.setScene(scene);new ShopWindow(stage,services).showLogin();return null;
            });
            screenshot("01-login.png",1280,860);
            screenshot("24-login-compact.png",960,680);screenshot("01-login.png",1280,860);
            check(fx(()->scene.lookup("#login-pc").getBoundsInParent().getWidth()>scene.lookup("#login-campaign").getBoundsInParent().getWidth()*.80),"Login PC remains prominent at compact size");
            check(fx(()->scene.lookup("#login-pc") instanceof javafx.scene.image.ImageView image&&!image.getImage().isError()
                &&image.getImage().getWidth()>=1000&&image.getImage().getHeight()>=1500
                &&image.getBlendMode()==null),"Account campaign loads the high-resolution PC cutout without lightening its dark interior");
            check(fx(()->"FORGE".equals(scene.lookup("#login-brand").getAccessibleText())),"Login wordmark is accessible and split by brand color");
            check(fx(()->scene.lookup("#login-reduce-motion").getParent().getStyleClass().contains("login-utility")),"Login utility controls overlay the full-height campaign instead of reserving a footer strip");
            check(fx(()->scene.lookup("#login-campaign").getBoundsInParent().getHeight()>scene.getHeight()*.9),"Login campaign extends to the bottom of the window");
            check(fx(()->scene.lookup("#login-utility").getBoundsInParent().getMaxY()>scene.getHeight()*.9),"Login utility controls stay at the bottom edge");
            check(fx(()->scene.lookup(".login-card").getBoundsInParent().getWidth()>=480),"Login panel has the approved wider desktop layout");
            text("password","temporary");press("show-password");
            check(fx(()->scene.lookup("#password-visible").isVisible()&&!scene.lookup("#password").isVisible()),"Show reveals only the visible password control");
            text("password-visible","changed");press("show-password");
            check(fx(()->((PasswordField)scene.lookup("#password")).getText()).equals("changed"),"Password edits survive hiding");
            text("password","");
            fx(()->{((CheckBox)scene.lookup("#login-reduce-motion")).fire();return null;});
            check(fx(()->Motion.isReduced()&&scene.lookup("#hero-float").getTranslateY()==0),"Login motion toggle resets campaign movement");
            fx(()->{((CheckBox)scene.lookup("#login-reduce-motion")).fire();return null;});
            press("login");check(!label("username-error").isBlank(),"Missing username needs field error");
            login("customer","incorrect");idle();check(label("login-feedback").contains("Incorrect"),"Wrong password needs inline error");
            click("← Continue browsing");catalog();
            for(int categoryIndex=1;categoryIndex<=6;categoryIndex++){
                press("hero-category-"+categoryIndex);idle();
                check(fx(()->scene.lookup("#category-banner").isVisible()),"Category banner is visible for category "+categoryIndex);
                check(fx(()->!scene.lookup("#editorial-hero").isManaged()),"Shop all hero stays separate from category banner");
                screenshot("category-"+categoryIndex+"-desktop.png",1280,860);
            }
            press("hero-category-3");idle();screenshot("category-laptop-compact.png",960,680);
            check(fx(()->((javafx.scene.layout.Region)scene.lookup("#category-banner")).getHeight())<=242,"Compact category banner leaves room for products");
            text("search","laptop");press("catalog-search");idle();
            check(fx(()->!scene.lookup("#category-banner").isManaged()),"Searching hides category campaigns");
            click("Shop all");catalog();screenshot("category-shop-all-preserved.png",1280,860);
            // Reset the offscreen fixture after explicit snapshot resizing before the original suite.
            fx(()->{new ShopWindow((Stage)scene.getWindow(),services).showStorefront();return null;});catalog();
            check(fx(()->((Button)scene.lookup("#account")).getText()).equals("Sign in"),"Guests browse without login");
            press("assistant-launcher");waitFor(()->scene.lookup("#assistant-panel").isVisible());
            text("assistant-question","show me USB-C cable");press("assistant-send");
            waitFor(()->!scene.lookup("#assistant-panel").lookupAll(".chat-product-card").isEmpty());
            check(fx(()->scene.lookup("#assistant-product-3")!=null),
                "Assistant renders the matched local catalog product as a card");
            check(fx(()->scene.lookup("#assistant-product-3").lookup(".chat-product-name")!=null),
                "Assistant card contains the matched product name");
            check(fx(()->scene.lookup("#assistant-product-3").lookup(".chat-product-category")!=null&&
                scene.lookup("#assistant-product-3").lookup(".chat-product-note")!=null),
                "Assistant card shows category and catalog-grounded fit note");
            check(fx(()->scene.lookup("#assistant-product-3").lookup(".chat-product-heart")!=null&&
                scene.lookup("#assistant-product-3").lookup(".chat-dark-button")!=null),
                "Assistant card offers save and add actions");
            press("assistant-close");waitFor(()->!scene.lookup("#assistant-panel").isVisible());
            check(fx(()->scene.lookup("#search").isVisible()&&scene.lookup("#catalog-search")!=null),"Catalog search is reachable");
            check(fx(()->((Button)scene.lookup("#hero-electronics")).getGraphic().getLayoutBounds().getHeight())>=270,"Campaign artwork fills the area below integrated navigation");
            check(fx(()->{
                javafx.scene.layout.Region art=(javafx.scene.layout.Region)scene.lookup("#product-4").lookup(".product-art");
                return Math.abs(art.getWidth()-art.getHeight())<1;
            }),"Catalog product artwork uses square frames");
            check(fx(()->scene.lookup("#product-1").lookup(".collection-artwork")!=null),"Seed product renders original offline artwork");
            check(fx(()->scene.lookup("#hero-headset") instanceof javafx.scene.image.ImageView view&&!view.getImage().isError()),"Campaign uses the bundled supplied headset image");
            fx(()->{((CheckBox)scene.lookup("#reduce-motion")).fire();return null;});
            check(fx(Motion::isReduced),"Footer enables reduced motion");
            press("hero-headphones");catalog();
            check(fx(()->((TilePane)scene.lookup("#product-grid")).getChildren().size())==1&&
                fx(()->((TextField)scene.lookup("#search")).getText()).equals("headphones"),"Primary campaign action finds headphones");
            check(fx(()->((CheckBox)scene.lookup("#reduce-motion")).isSelected()),"Motion preference survives catalog navigation");
            click("Shop all");catalog();
            fx(()->{((CheckBox)scene.lookup("#reduce-motion")).fire();return null;});
            check(!fx(Motion::isReduced),"Footer restores normal motion");
            press("hero-shop");
            check(fx(()->((ScrollPane)scene.lookup("#catalog-scroll")).getVvalue()>0),"Hero shopping action scrolls to products");
            screenshot("22-product-collection.png",1280,860);
            fx(()->{((ScrollPane)scene.lookup("#catalog-scroll")).setVvalue(0);return null;});
            screenshot("13-guest-storefront.png",1280,860);
            screenshot("21-storefront-compact.png",960,680);
            check(fx(()->scene.lookup("#editorial-hero").getLayoutBounds().getHeight()<=280),"Compact campaign is shorter, not a squeezed desktop banner");
            check(fx(()->scene.lookup("#hero-category-more").isVisible()&&!scene.lookup("#hero-category-4").isVisible()),"Compact category overflow stays accessible through More");
            check(fx(()->{
                var photo=scene.lookup("#hero-headset");var hero=scene.lookup("#editorial-hero");var nav=scene.lookup("#tech-category-nav");
                var imageBounds=photo.localToScene(photo.getLayoutBounds());
                return imageBounds.getMinY()>=nav.localToScene(nav.getBoundsInLocal()).getMaxY()
                    &&imageBounds.getMaxY()>hero.localToScene(hero.getLayoutBounds()).getMaxY();
            }),"Headset clears category text and overlaps the campaign bottom");
            check(fx(()->{
                var photo=scene.lookup("#hero-headset");var filters=scene.lookup("#toggle-filters");
                return photo.localToScene(photo.getBoundsInLocal()).getMaxY()<filters.localToScene(filters.getBoundsInLocal()).getMinY();
            }),"Headset overlap clears catalog controls");
            screenshot("13-guest-storefront.png",1280,860);
            fx(()->{
                ScrollPane scroll=(ScrollPane)scene.lookup("#catalog-scroll");
                double start=scene.lookup("#collection-links").getParent().getBoundsInParent().getMinY();
                scroll.setVvalue(start/(scroll.getContent().getLayoutBounds().getHeight()-scroll.getViewportBounds().getHeight()));return null;
            });
            screenshot("23-collection-discovery.png",1280,860);
            check(fx(()->((FlowPane)scene.lookup("#collection-links")).getChildren().size())==6,"Discovery contains all six tech collections");
            check(fx(()->scene.lookup("#collection-home")==null&&scene.lookup("#collection-clothing")==null),"Discovery excludes retired home and clothing categories");
            check(fx(()->scene.lookup("#collection-pc_parts").getLayoutBounds().getHeight())<=200,"Collection tiles stay compact");
            fx(()->{((ScrollPane)scene.lookup("#catalog-scroll")).setVvalue(0);return null;});
            press("hero-electronics");catalog();
            check(fx(()->((TilePane)scene.lookup("#product-grid")).getChildren().size())==4,"Campaign action opens peripherals");
            check(fx(()->!scene.lookup("#editorial-hero").isManaged()),"Campaign hides while browsing a category");
            press("collection-pc_parts");catalog();
            check(fx(()->((TilePane)scene.lookup("#product-grid")).getChildren().size())==2&&
                fx(()->scene.lookup("#hero-category-2").getStyleClass().contains("category-active")),"Collection shortcut opens PC parts");
            text("search","thermal");click("Search");catalog();
            press("collection-pc_parts");catalog();
            check(fx(()->((TilePane)scene.lookup("#product-grid")).getChildren().size())==2&&
                fx(()->((TextField)scene.lookup("#search")).getText()).isEmpty(),"Same-category shortcut refreshes after clearing search");
            click("Shop all");catalog();
            fx(()->{var card=scene.lookup("#product-4");((Button)card.lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals("View product")).findFirst().orElseThrow()).fire();return null;});
            waitFor(()->scene.lookup("#product-detail")!=null&&!scene.getRoot().isDisabled());idle();screenshot("14-product-detail.png",1280,860);
            screenshot("15-product-detail-compact.png",960,680);screenshot("14-product-detail.png",1280,860);
            fx(()->{((QuantityControl)scene.lookup("#detail-quantity")).valueProperty().set(2);return null;});
            press("detail-add");idle();
            fx(()->{((Button)scene.lookup("#nav-cart")).fire();return null;});
            check(fx(()->scene.lookup("#cart-drawer")!=null),"Cart opens as drawer without leaving details");
            waitFor(()->Math.abs(scene.lookup("#cart-drawer").getTranslateX())<0.01);
            screenshot("18-cart-drawer.png",1280,860);screenshot("19-cart-drawer-compact.png",960,680);screenshot("18-cart-drawer.png",1280,860);
            fx(()->{
                scene.lookup("#close-cart-drawer").requestFocus();
                scene.lookup("#cart-drawer").fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,"","",javafx.scene.input.KeyCode.TAB,false,false,false,false));
                return null;
            });
            check(fx(()->{
                var focused=scene.getFocusOwner();while(focused!=null){if(focused==scene.lookup("#cart-drawer"))return true;focused=focused.getParent();}return false;
            }),"Tab navigation stays inside cart drawer");
            press("drawer-plus-4");idle();
            check(fx(()->((Button)scene.lookup("#nav-cart")).getText()).equals("Cart · 3"),"Drawer changes update header");
            press("drawer-minus-4");idle();
            fx(()->{scene.lookup("#cart-drawer").fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,"","",javafx.scene.input.KeyCode.ESCAPE,false,false,false,false));return null;});
            waitFor(()->scene.lookup("#cart-drawer")==null);
            check(fx(()->scene.lookup("#cart-drawer")==null&&scene.lookup("#product-detail")!=null),"Escape closes drawer and preserves product page");
            fx(()->{
                ((Button)scene.lookup("#nav-cart")).fire();
                ((Button)scene.lookup("#close-cart-drawer")).fire();
                ((Button)scene.lookup("#close-cart-drawer")).fire();
                return null;
            });
            waitFor(()->scene.lookup("#cart-drawer")==null);
            check(fx(()->!scene.lookup("#nav-cart").isDisabled()),"Repeated close during opening restores navigation");
            fx(()->{
                ((Button)scene.lookup("#nav-cart")).fire();
                ((Button)scene.lookup("#close-cart-drawer")).fire();
                ((Button)scene.lookup("#drawer-review")).fire();
                return null;
            });
            // Allow a stale completion callback to surface if navigation failed to cancel it.
            Thread.sleep(300);
            check(fx(()->scene.lookup("#cart-items")!=null&&scene.lookup("#cart-drawer")==null&&!scene.lookup("#nav-cart").isDisabled()),
                "Navigation during drawer closing cancels motion and keeps the destination usable");
            press("checkout");
            check(fx(()->scene.lookup("#login")!=null),"Guest checkout requires login");
            login("customer","customer123");waitFor(()->scene.lookup("#cart-items")!=null&&!scene.getRoot().isDisabled());
            check(fx(()->((TextField)scene.lookup("#cart-quantity-4")).getText()).equals("2"),"Sign-in preserves guest quantities");
            press("remove-4");click("Browse products");catalog();
            check(fx(()->((Button)scene.lookup("#add-6")).isDisabled()),"Sold-out product must be disabled");
            screenshot("02-catalog.png",1280,860);screenshot("08-catalog-compact.png",960,680);
            // Restore the regular viewport before subsequent control interactions.
            screenshot("02-catalog.png",1280,860);
            text("search","does not exist");click("Search");
            waitFor(()->scene.getRoot().lookupAll(".empty-state").size()==1);
            check(label("catalog-feedback").isBlank(),"Empty results are not a failure");
            check(fx(()->scene.lookup("#catalog-filters")==null),"Filter drawer starts closed");
            press("clear-search");catalog();
            press("toggle-filters");waitFor(()->scene.lookup("#price-range")!=null);
            fx(()->{((PriceRange)scene.lookup("#price-range")).setRange(0,50);return null;});press("apply-filters");idle();
            check(fx(()->((TilePane)scene.lookup("#product-grid")).getChildren().size())==4,"Slider price cap filters products");
            press("toggle-filters");waitFor(()->scene.lookup("#price-range")!=null);
            fx(()->{((CheckBox)scene.lookup("#filter-in-stock")).fire();return null;});press("apply-filters");idle();
            check(fx(()->((TilePane)scene.lookup("#product-grid")).getChildren().size())==3,"Stock and price filters combine");
            screenshot("20-catalog-filtered.png",1280,860);
            press("view-4");waitFor(()->scene.lookup("#product-detail")!=null&&!scene.getRoot().isDisabled());idle();
            check(fx(()->scene.lookup("#related-products") instanceof FlowPane box&&!box.getChildren().isEmpty()),"Related products use current category");
            check(fx(()->scene.lookup("#product-description")!=null),"Product description remains available");
            check(fx(()->scene.lookup("#product-details-panel")==null),"Product description is no longer repeated in a second panel");
            click("← Back to results");catalog();
            check(fx(()->((TilePane)scene.lookup("#product-grid")).getChildren().size())==3,"Back retains price and stock filters");
            click("Price: $0–$50.00  ×");idle();
            check(fx(()->((TilePane)scene.lookup("#product-grid")).getChildren().size())==5,"Removing one chip preserves other filters");
            click("Clear filters");catalog();click("PC Parts");catalog();
            check(fx(()->((TilePane)scene.lookup("#product-grid")).getChildren().size())==2,"Header categories navigate to their collection");
            click("Clear filters");catalog();
            fx(()->{((ComboBox<?>)scene.lookup("#sort")).getSelectionModel().select(1);return null;});idle();
            check(fx(()->((TilePane)scene.lookup("#product-grid")).getChildren().getFirst().getId()).equals("product-4"),"Price ascending order");
            press("nav-cart");check(fx(()->scene.getRoot().lookupAll(".empty-state").size())==1,"Empty cart is actionable");
            click("Browse products");catalog();press("add-4");idle();
            check(fx(()->((Button)scene.lookup("#nav-cart")).getText()).equals("Cart · 1"),"Cart badge after add");
            check(fx(()->((Button)scene.lookup("#add-4")).getText()).equals("Added ✓"),"Catalog action confirms a successful add");
            waitFor(()->scene.lookup("#cart-toast").getOpacity()>.99);
            check(fx(()->scene.lookup("#toast-review-cart").isVisible()),"Added toast offers a cart review action");
            fx(()->{scene.lookup("#toast-review-cart").requestFocus();return null;});
            press("toast-review-cart");waitFor(()->scene.lookup("#cart-drawer")!=null);press("close-cart-drawer");waitFor(()->scene.lookup("#cart-drawer")==null);
            check(fx(()->scene.getFocusOwner()==scene.lookup("#nav-cart")),"Closing toast-opened drawer returns focus to visible cart control");
            press("nav-cart");press("plus-4");idle();
            check(fx(()->((Button)scene.lookup("#nav-cart")).getText()).equals("Cart · 2"),"Cart badge after plus");
            press("minus-4");idle();
            text("cart-quantity-4","0");click("Update");idle();
            check(label("cart-feedback").contains("Quantity"),"Invalid quantity shows useful error");
            text("cart-quantity-4","2");click("Update");idle();
            text("cart-quantity-4","20");click("Update");idle();
            check(fx(()->((Button)scene.lookup("#plus-4")).isDisabled()),"Cart increase disables at available stock");
            fx(()->{((Button)scene.lookup("#nav-cart")).fire();return null;});
            check(fx(()->((Button)scene.lookup("#drawer-plus-4")).isDisabled()),"Drawer increase disables at available stock");
            press("close-cart-drawer");waitFor(()->scene.lookup("#cart-drawer")==null);
            text("cart-quantity-4","2");click("Update");idle();
            screenshot("03-cart.png",1280,860);screenshot("09-cart-compact.png",960,680);
            check(fx(()->((VBox)scene.lookup("#cart-columns")).getChildren().getFirst() instanceof VBox),"Compact cart stacks items and summary");
            screenshot("03-cart.png",1280,860);
            press("remove-4");check(fx(()->scene.getRoot().lookupAll(".empty-state").size())==1,"Removing last item shows empty state");
            click("Browse products");catalog();press("add-4");idle();press("add-1");idle();press("nav-cart");
            press("checkout");
            check(fx(()->scene.lookup(".toast").getOpacity()==0),"Catalog toast is cleared when navigating to checkout");
            fx(()->{((ComboBox<?>)scene.lookup("#payment")).getSelectionModel().clearSelection();((Label)scene.lookup("#payment-error")).setText("Choose a payment method.");Ui.flag((Control)scene.lookup("#payment"),true);return null;});
            fx(()->{((ComboBox<?>)scene.lookup("#payment")).getSelectionModel().selectFirst();return null;});
            check(label("payment-error").isBlank()&&fx(()->!scene.lookup("#payment").getStyleClass().contains("invalid")),"Choosing payment clears its validation feedback");
            press("place-order");check(!label("address-error").isBlank(),"Missing address is inline");
            text("address","123 Sample Street");press("place-order");
            check(!label("recipient-error").isBlank()&&!label("city-error").isBlank()&&!label("postal-error").isBlank(),"Delivery fields require meaningful values");
            check(fx(()->scene.getFocusOwner()==scene.lookup("#recipient")),"Checkout focuses first invalid delivery field");
            check(fx(()->((Button)scene.lookup("#place-order")).getText()).contains("$72.80"),"Checkout action states the actual order total");
            text("recipient","Demo Customer");text("city","Demo City");text("postal-code","12345");
            text("address","123 Sample Street, Demo City");click("← Back to cart");press("checkout");
            check(fx(()->((TextArea)scene.lookup("#address")).getText()).equals("123 Sample Street, Demo City"),"Back navigation preserves draft");
            screenshot("04-checkout.png",1280,860);screenshot("10-checkout-compact.png",960,680);
            check(fx(()->((VBox)scene.lookup("#checkout-columns")).getChildren().getFirst() instanceof VBox),"Compact checkout stacks form and summary in one scroller");
            screenshot("04-checkout.png",1280,860);
            fx(()->{((ComboBox<?>)scene.lookup("#payment")).getSelectionModel().select(2);return null;});
            press("place-order");idle();check(label("checkout-feedback").contains("declined"),"Decline stays on checkout with feedback");
            check(services.orders().history(services.auth().login("customer","customer123")).isEmpty(),"Decline must not create order");
            fx(()->{((ComboBox<?>)scene.lookup("#payment")).getSelectionModel().select(1);return null;});
            press("place-order");waitFor(()->scene.lookup("#confirmation")!=null);
            check(fx(()->((Button)scene.lookup("#nav-cart")).getText()).equals("Cart · 0"),"Success clears badge");
            screenshot("07-confirmation.png",1280,860);
            click("View my orders");waitFor(()->scene.lookup("#order-cards") instanceof VBox t&&t.getChildren().size()==1);
            screenshot("05-orders.png",1280,860);
            click("Sign out");catalog();press("account");login("admin","admin123");
            waitFor(()->scene.lookup("#admin-table") instanceof TableView<?> t&&t.getItems().size()==6);
            check(fx(()->((Button)scene.lookup("#edit-product")).isDisabled()),"Edit unavailable until selection");
            press("new-product");press("save-product");
            check(!label("name-error").isBlank()&&!label("price-error").isBlank(),"Admin field validation");
            text("product-name","GUI smoke product");text("product-price","9.99");text("product-stock","4");
            text("product-description","A sample description entered by the store administrator.");
            Path photo=Path.of(args[1]).toAbsolutePath().getParent().resolve("upload-test.png");
            BufferedImage fixture=new BufferedImage(240,160,BufferedImage.TYPE_INT_RGB);
            var graphics=fixture.createGraphics();graphics.setColor(new java.awt.Color(222,233,217));graphics.fillRect(0,0,240,160);
            graphics.setColor(new java.awt.Color(38,92,72));graphics.fillRoundRect(75,35,90,85,12,12);
            graphics.setColor(java.awt.Color.WHITE);graphics.drawString("TEST PHOTO",82,80);graphics.dispose();ImageIO.write(fixture,"png",photo.toFile());
            fx(()->{((ProductPhotoEditor)scene.lookup("#photo-editor")).importPhoto(photo);return null;});idle();
            check(!label("photo-feedback").isBlank(),"Photo upload provides draft feedback");
            check(fx(()->scene.lookup("#photo-preview").lookupAll(".product-photo").size())==1,"Uploaded photo renders in preview");
            screenshot("16-photo-upload.png",1280,860);
            java.awt.image.BufferedImage alternate=new java.awt.image.BufferedImage(260,180,java.awt.image.BufferedImage.TYPE_INT_RGB);
            var angleGraphics=alternate.createGraphics();angleGraphics.setColor(java.awt.Color.BLUE);angleGraphics.fillRect(0,0,260,180);angleGraphics.dispose();ImageIO.write(alternate,"png",photo.toFile());
            fx(()->{((ProductPhotoEditor)scene.lookup("#photo-editor")).importAdditionalPhoto(photo);return null;});idle();
            text("product-specifications-input","Material: Test fixture\nIncluded items: One demo unit");
            check(fx(()->((ProductPhotoEditor)scene.lookup("#photo-editor")).getAdditionalPhotos().size())==1,"Admin editor accepts a gallery angle in the draft");
            press("save-product");
            waitFor(()->((TableView<?>)scene.lookup("#admin-table")).getItems().size()==7);
            Product uploaded=services.products().search(admin,"GUI smoke",null).getFirst();
            check(uploaded.getPhoto()!=null,"Admin save persists photo");
            check(uploaded.getAdditionalPhotos().size()==1&&uploaded.getSpecifications().contains("Material"),"Admin save persists gallery and specifications");
            Scene inventoryScene=scene;
            fx(()->{
                Stage galleryStage=new Stage();scene=new Scene(new ProductDetailView(uploaded,new Guest(),new Cart(0),new CartService(services.products()),services.products(),()->{},()->{},()->{},p->{}),1280,860);
                scene.getStylesheets().add(GuiSmoke.class.getResource("/shop.css").toExternalForm());galleryStage.setScene(scene);galleryStage.show();return null;
            });idle();
            check(fx(()->scene.lookup("#gallery-thumbnails").isVisible()&&scene.lookup("#product-specifications")!=null),"Product details offers real uploaded angles and specifications");
            press("gallery-photo-1");
            check(fx(()->scene.lookup("#gallery-photo-1").getStyleClass().contains("gallery-selected")),"Selecting an angle updates the gallery selection");
            check(fx(()->((javafx.scene.image.ImageView)scene.lookup("#gallery-main").lookup(".product-photo")).getImage().getPixelReader().getColor(10,10).getBlue())>.9,"Selected angle is rendered in the large photo");
            screenshot("25-product-gallery.png",1280,860);screenshot("26-product-gallery-compact.png",960,680);
            fx(()->{((Stage)scene.getWindow()).close();scene=inventoryScene;return null;});

            Files.delete(photo);
            check(services.products().get(uploaded.getId()).getPhoto()!=null,"Deleting original file keeps saved photo");
            text("admin-search","GUI smoke");check(fx(()->((TableView<?>)scene.lookup("#admin-table")).getItems().size())==1,"Admin search");
            fx(()->{((TableView<?>)scene.lookup("#admin-table")).getSelectionModel().selectFirst();return null;});
            press("edit-product");
            check(fx(()->((TextField)scene.lookup("#product-name")).getText()).equals("GUI smoke product"),"Explicit edit fills form");
            screenshot("11-admin-editor.png",1280,860);screenshot("17-admin-editor-compact.png",960,680);screenshot("11-admin-editor.png",1280,860);
            check(fx(()->scene.lookup("#photo-preview").lookupAll(".product-photo").size())==1,"Reopening edit restores uploaded photo");
            check(fx(()->((ProductPhotoEditor)scene.lookup("#photo-editor")).getAdditionalPhotos().size())==1&&fx(()->((TextArea)scene.lookup("#product-specifications-input")).getText()).contains("Material"),"Reopening editor retains gallery and specifications");
            press("remove-photo");click("Cancel");press("edit-product");
            check(fx(()->scene.lookup("#photo-preview").lookupAll(".product-photo").size())==1,"Cancel preserves saved photo");
            Path bad=Path.of(args[1]).toAbsolutePath().getParent().resolve("bad-upload.jpg");Files.writeString(bad,"not a photo");
            fx(()->{((ProductPhotoEditor)scene.lookup("#photo-editor")).importPhoto(bad);return null;});idle();
            check(label("photo-feedback").contains("valid"),"Invalid upload shows inline error");
            check(fx(()->scene.lookup("#photo-preview").lookupAll(".product-photo").size())==1,"Invalid upload keeps existing photo");
            press("remove-photo");press("save-product");idle();press("edit-product");
            check(services.products().get(uploaded.getId()).getPhoto()==null,"Saving removal clears persistent photo");
            check(services.products().get(uploaded.getId()).getAdditionalPhotos().isEmpty(),"Removing main photo also clears gallery angles");
            fx(()->{((CheckBox)scene.lookup("#product-active")).setSelected(false);return null;});press("save-product");
            waitFor(()->!scene.getRoot().isDisabled()&&((TableView<?>)scene.lookup("#admin-table")).getItems().size()==1);
            text("admin-search","");
            fx(()->{((ComboBox<?>)scene.lookup("#admin-status")).getSelectionModel().select(1);return null;});
            check(fx(()->((TableView<?>)scene.lookup("#admin-table")).getItems().size())==6,"Active filter hides deactivated item");
            screenshot("06-admin.png",1280,860);screenshot("12-admin-compact.png",960,680);
            check(services.products().search(services.auth().login("customer","customer123"),"GUI smoke",null).isEmpty(),"Inactive hidden from customer");
            System.out.println("GUI SMOKE PASS: "+checks+" assertions; editorial actions, artwork, catalog, filters, sold-out state, cart edits, draft retention, declined/approved payment, confirmation, history, admin create/edit/filter; 26 snapshots.");
        }finally{Platform.exit();}
    }
}
