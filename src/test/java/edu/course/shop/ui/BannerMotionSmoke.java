package edu.course.shop.ui;

import edu.course.shop.model.Category;
import edu.course.shop.model.Product;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Real shown-window smoke checks for motion, lifecycle and reduced-motion behavior. No database. */
public final class BannerMotionSmoke {
    private static Stage stage;
    private static VBox root;
    private static EditorialHeader hero;
    private static Button probe;
    private static ProductCard card;
    private static int checks;
    private static <T> T fx(Callable<T> action)throws Exception {
        FutureTask<T> task=new FutureTask<>(action);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);
    }
    private static void runFx(Runnable action)throws Exception { fx(()->{action.run();return null;}); }
    private static void waitFor(BooleanSupplier condition)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!fx(condition::getAsBoolean)) {
            if(System.nanoTime()>end)throw new AssertionError("Motion condition timed out");Thread.sleep(40);
        }
    }
    private static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    private static double drift()throws Exception {return fx(()->hero.lookup("#hero-float").getTranslateY());}
    private static void moveTo(javafx.scene.Node node)throws Exception {
        fx(()->{Bounds b=node.localToScreen(node.getBoundsInLocal());new Robot().mouseMove(b.getCenterX(),b.getCenterY());return null;});
    }
    private static void snapshot(Path path)throws Exception {
        fx(()->{
            WritableImage image=hero.snapshot(null,null);
            BufferedImage png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));
            ImageIO.write(png,"png",path.toFile());return null;
        });
    }
    public static void main(String[] args)throws Exception {
        Path output=Path.of(args[0]),frames=Path.of(args[1]);Files.createDirectories(output);Files.createDirectories(frames);
        CountDownLatch ready=new CountDownLatch(1);Platform.startup(ready::countDown);ready.await();Platform.setImplicitExit(false);
        try {
            fx(()->{
                Motion.reducedProperty().set(false);
                hero=new EditorialHeader(()->{},()->{},()->{});
                probe=Ui.primary("Hover feedback",()->{});probe.setId("hover-probe");
                card=new ProductCard(new Product(10,"Mechanical keyboard",Category.PERIPHERALS,new BigDecimal("12.90"),3,true),()->{},()->{});
                card.setMaxWidth(280);
                root=new VBox(12,hero,probe,card);root.setFocusTraversable(true);Scene scene=new Scene(root,1280,830);
                scene.getStylesheets().add(BannerMotionSmoke.class.getResource("/shop.css").toExternalForm());
                stage=new Stage();stage.setX(20);stage.setY(20);stage.setScene(scene);stage.show();root.applyCss();root.layout();root.requestFocus();return null;
            });
            check(fx(()->((ImageView)hero.lookup("#hero-headset")).getImage().isError())==false,"Supplied headset loads offline");
            check(fx(()->((Button)hero.lookup("#hero-headphones")).getText()).contains("Explore headphones"),"Headphone CTA uses agreed copy");
            waitFor(()->hero.lookup("#hero-headline").getOpacity()>.99);
            double start=drift();Thread.sleep(350);check(Math.abs(drift()-start)>.03,"Headset floats in a shown window");
            double glow=fx(()->hero.lookup("#hero-glow").getOpacity());Thread.sleep(350);
            check(Math.abs(fx(()->hero.lookup("#hero-glow").getOpacity())-glow)>.001,"Orange halo pulses");
            snapshot(output.resolve("hero-wide.png"));

            moveTo(probe);waitFor(()->probe.getTranslateY()<-1.9);check(true,"Buttons lift on real pointer hover");
            Button visual=fx(()->(Button)card.lookup(".photo-button"));moveTo(visual);
            waitFor(()->card.lookup(".product-art").getScaleX()>1.03);check(true,"Product image zooms on hover");
            runFx(()->Motion.reducedProperty().set(true));
            check(fx(()->probe.getTranslateY()==0&&card.lookup(".product-art").getScaleX()==1),"Reduced motion immediately resets hover effects");
            check(drift()==0&&fx(()->hero.lookup("#hero-parallax").getTranslateX())==0,"Reduced motion resets product movement");
            glow=fx(()->hero.lookup("#hero-glow").getOpacity());Thread.sleep(350);
            check(drift()==0&&fx(()->hero.lookup("#hero-glow").getOpacity())==glow,"Reduced motion holds a stable, readable banner");
            check(fx(()->hero.lookup("#hero-headline").getOpacity())==1,"Reduced motion keeps campaign text visible");
            runFx(()->Motion.reducedProperty().set(false));waitFor(()->Math.abs(hero.lookup("#hero-float").getTranslateY())>.03);
            check(true,"Animations resume when reduced motion is disabled");

            // Move over the hero's right edge, then leave it; parallax must return to center.
            fx(()->{Bounds b=hero.localToScreen(hero.getBoundsInLocal());new Robot().mouseMove(b.getMaxX()-30,b.getCenterY());return null;});
            waitFor(()->hero.lookup("#hero-parallax").getTranslateX()>4);check(true,"Pointer produces restrained parallax");
            moveTo(probe);waitFor(()->Math.abs(hero.lookup("#hero-parallax").getTranslateX())<.01);check(true,"Leaving the banner resets parallax");
            runFx(()->hero.setInViewport(false));double stopped=drift();Thread.sleep(350);check(drift()==stopped,"Offscreen banner pauses");
            runFx(()->hero.setInViewport(true));Thread.sleep(350);check(Math.abs(drift()-stopped)>.03,"Visible banner resumes");
            runFx(()->hero.setVisible(false));stopped=drift();Thread.sleep(350);check(drift()==stopped,"Filtered-out banner pauses");
            runFx(()->hero.setVisible(true));
            runFx(()->stage.hide());stopped=drift();Thread.sleep(350);check(drift()==stopped,"Hidden window pauses banner motion");
            runFx(()->{stage.setHeight(830);stage.show();});Thread.sleep(350);check(Math.abs(drift()-stopped)>.03,"Reopened window resumes motion");
            fx(()->root.getChildren().remove(hero));Thread.sleep(350);check(drift()==0,"Detached banner stops and resets its animation");
            runFx(()->root.getChildren().addFirst(hero));
            runFx(()->Motion.reducedProperty().set(true));
            fx(()->{stage.setWidth(stage.getWidth()+960-stage.getScene().getWidth());root.applyCss();root.layout();return null;});Thread.sleep(100);
            snapshot(output.resolve("hero-compact.png"));
            check(fx(()->hero.getStyleClass().contains("hero-compact")),"Compact banner adapts typography");
            check(fx(()->hero.getLayoutBounds().getWidth())<=960,"Compact banner fits the window width");
            // Export real frames from a fresh hero, including its staggered entrance.
            fx(()->{
                Motion.reducedProperty().set(false);root.getChildren().remove(hero);
                stage.setWidth(stage.getWidth()+1280-stage.getScene().getWidth());hero=new EditorialHeader(()->{},()->{},()->{});root.getChildren().addFirst(hero);
                root.applyCss();root.layout();root.requestFocus();return null;
            });
            for(int i=0;i<60;i++) {snapshot(frames.resolve(String.format("frame-%03d.png",i)));Thread.sleep(80);}
            check(fx(()->hero.lookup("#hero-headline").getOpacity())==1,"Entrance settles into fully visible text");
            var db=new edu.course.shop.persistence.DatabaseManager(Files.createTempDirectory("banner-catalog-smoke").resolve("shop.db"));
            db.initialize();
            var products=new edu.course.shop.persistence.ProductRepository();
            var orders=new edu.course.shop.persistence.OrderRepository();
            var services=new edu.course.shop.Services(new edu.course.shop.service.AuthService(db,new edu.course.shop.persistence.UserRepository()),
                new edu.course.shop.service.ProductService(db,products),new edu.course.shop.service.CheckoutService(db,products,orders),
                new edu.course.shop.service.OrderService(db,orders),
                new edu.course.shop.service.AdminService(db,new edu.course.shop.persistence.AdminRepository()),
                new edu.course.shop.service.ProductReviewService(db,new edu.course.shop.persistence.ProductReviewRepository()));
            fx(()->{
                var scene=new Scene(new StackPane(),1280,860);scene.getStylesheets().add(BannerMotionSmoke.class.getResource("/shop.css").toExternalForm());
                stage.setScene(scene);stage.setHeight(860);new ShopWindow(stage,services).showStorefront();return null;
            });
            waitFor(()->stage.getScene().lookup("#hero-float")!=null&&!stage.getScene().getRoot().isDisabled());
            waitFor(()->Math.abs(stage.getScene().lookup("#hero-float").getTranslateY())>.03);
            check(true,"Campaign animates inside the actual storefront");
            fx(()->{((Button)stage.getScene().lookup("#hero-shop")).fire();return null;});
            double catalogStopped=fx(()->stage.getScene().lookup("#hero-float").getTranslateY());Thread.sleep(350);
            check(fx(()->stage.getScene().lookup("#hero-float").getTranslateY())==catalogStopped,"Catalog scrolling pauses offscreen campaign");
            fx(()->{((javafx.scene.control.ScrollPane)stage.getScene().lookup("#catalog-scroll")).setVvalue(0);return null;});Thread.sleep(350);
            check(Math.abs(fx(()->stage.getScene().lookup("#hero-float").getTranslateY())-catalogStopped)>.03,"Scrolling back resumes campaign");
            // Exercise actual viewport geometry with opaque and alpha-padded uploads.
            for(boolean transparent:new boolean[]{false,true}) {
                BufferedImage fixture=new BufferedImage(400,200,BufferedImage.TYPE_INT_ARGB);
                var graphics=fixture.createGraphics();
                if(!transparent){graphics.setColor(java.awt.Color.BLACK);graphics.fillRect(0,0,400,200);}
                graphics.setColor(java.awt.Color.ORANGE);graphics.fillRect(150,40,100,120);graphics.dispose();
                var bytes=new java.io.ByteArrayOutputStream();ImageIO.write(fixture,"png",bytes);
                Product uploaded=new Product(99,"Frame probe",Category.ELECTRONICS,new BigDecimal("10"),1,true,"",bytes.toByteArray());
                fx(()->{
                    ProductCard photoCard=new ProductCard(uploaded,()->{},()->{});photoCard.setMaxWidth(300);
                    var photoScene=new Scene(new StackPane(photoCard),400,600);
                    photoScene.getStylesheets().add(BannerMotionSmoke.class.getResource("/shop.css").toExternalForm());
                    stage.setScene(photoScene);stage.sizeToScene();photoScene.getRoot().applyCss();photoScene.getRoot().layout();return null;
                });
                check(fx(()->{
                    ImageView photo=(ImageView)stage.getScene().lookup(".product-photo");
                    var viewport=photo.getViewport();var bounds=photo.getBoundsInParent();
                    var art=(javafx.scene.layout.Region)stage.getScene().lookup(".product-art");
                    if(transparent)return Math.abs(viewport.getWidth()/viewport.getHeight()-100.0/120)<.02
                        &&viewport.getWidth()<photo.getImage().getWidth()/2&&bounds.getWidth()<art.getWidth()&&bounds.getHeight()<art.getHeight();
                    return Math.abs(viewport.getWidth()-viewport.getHeight())<1&&Math.abs(bounds.getWidth()-art.getWidth())<1&&Math.abs(bounds.getHeight()-art.getHeight())<1;
                }),transparent?"Transparent margins trimmed with full cutout visible":"Opaque photo fills square without distortion or side gaps");
                fx(()->{
                    var shot=stage.getScene().getRoot().snapshot(null,null);
                    BufferedImage png=new BufferedImage((int)shot.getWidth(),(int)shot.getHeight(),BufferedImage.TYPE_INT_ARGB);
                    for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,shot.getPixelReader().getArgb(x,y));
                    ImageIO.write(png,"png",output.resolve(transparent?"photo-cutout.png":"photo-fill.png").toFile());return null;
                });
            }
            fx(()->{
                hero=EditorialHeader.forLogin();hero.setMinHeight(0);hero.setPrefHeight(640);hero.setMaxHeight(Double.MAX_VALUE);
                var loginScene=new Scene(new StackPane(hero),480,640);
                loginScene.getStylesheets().add(BannerMotionSmoke.class.getResource("/shop.css").toExternalForm());
                stage.setScene(loginScene);stage.sizeToScene();return null;
            });
            waitFor(()->Math.abs(hero.lookup("#hero-float").getTranslateY())>.03);
            check(true,"PC account campaign animates in a shown window");
            runFx(()->Motion.reducedProperty().set(true));
            check(drift()==0&&fx(()->hero.lookup("#hero-headline").getOpacity())==1,"Account reduced motion resets drift and reveals all text");
            check(fx(()->{
                ImageView image=(ImageView)hero.lookup("#login-pc");
                return image.getBoundsInParent().getWidth()>hero.getWidth()*.80&&image.getBoundsInParent().getWidth()<=hero.getWidth()
                    &&Math.abs(image.getBoundsInParent().getWidth()/image.getBoundsInParent().getHeight()-image.getImage().getWidth()/image.getImage().getHeight())<.01;
            }),"PC photo scales down responsively while preserving its proportions");
            snapshot(output.resolve("login-campaign.png"));
            System.out.println("BANNER MOTION SMOKE PASS: "+checks+" assertions; real hover, float, glow, parallax, reduced motion, viewport/window/detach lifecycle; 60 rendered frames.");
        } finally {fx(()->{Motion.reducedProperty().set(false);if(stage!=null)stage.close();return null;});Platform.exit();}
    }
}
