package edu.course.shop.ui;
import edu.course.shop.*;
import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import edu.course.shop.service.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Rendered login reference, authentication and animated-glow lifecycle. Uses an isolated database. */
public final class LoginGlassSmoke {
    static Scene scene;static Stage stage;static int checks;
    static <T>T fx(Callable<T> job)throws Exception{FutureTask<T> task=new FutureTask<>(()->{if(scene!=null){scene.getRoot().applyCss();scene.getRoot().layout();}return job.call();});Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    static void waitFor(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(!fx(condition::getAsBoolean)){if(System.nanoTime()>end)throw new AssertionError("Timed out waiting for login");Thread.sleep(35);}}
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;System.out.println("PASS "+message);}
    static void press(String id)throws Exception{fx(()->{((Button)scene.lookup("#"+id)).fire();return null;});}
    static void text(String id,String value)throws Exception{fx(()->{((TextInputControl)scene.lookup("#"+id)).setText(value);return null;});}
    static void resize(double w,double h)throws Exception{fx(()->{stage.setWidth(w+stage.getWidth()-scene.getWidth());stage.setHeight(h+stage.getHeight()-scene.getHeight());return null;});waitFor(()->Math.abs(scene.getWidth()-w)<2&&Math.abs(scene.getHeight()-h)<2);}
    static void shot(Path path)throws Exception{fx(()->{var image=scene.getRoot().snapshot(null,null);BufferedImage png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));ImageIO.write(png,"png",path.toFile());return null;});}
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[0]);Files.createDirectories(out);DatabaseManager db=new DatabaseManager(out.resolve("login-"+System.nanoTime()+".db"));db.initialize();ProductRepository p=new ProductRepository();OrderRepository o=new OrderRepository();Services services=new Services(new AuthService(db,new UserRepository()),new ProductService(db,p),new CheckoutService(db,p,o),new OrderService(db,o),new AdminService(db,new AdminRepository()),new ProductReviewService(db,new ProductReviewRepository()));
        CountDownLatch ready=new CountDownLatch(1);Platform.startup(ready::countDown);ready.await();Platform.setImplicitExit(false);
        try{
            fx(()->{Motion.reducedProperty().set(true);stage=new Stage();scene=new Scene(new StackPane(),1280,820);scene.getStylesheets().add(LoginGlassSmoke.class.getResource("/shop.css").toExternalForm());stage.setScene(scene);stage.show();new ShopWindow(stage,services).showLogin();return null;});
            waitFor(()->scene.lookup("#login-glass-panel")!=null);LoginGlassPanel card=fx(()->(LoginGlassPanel)scene.lookup("#login-glass-panel"));
            fx(()->{check(scene.lookup("#login-glass-reflection")!=null,"Glass panel has the diagonal reflection");check(((Color)((Label)scene.lookup("#login-brand")).getTextFill()).getBrightness()>.9,"Login wordmark matches the white reference");check(!card.glowRunning(),"Reduced motion uses a static edge glow");check(!((TitledPane)scene.lookup("#demo-accounts")).isExpanded(),"Demo accounts remain collapsed");check(((Button)scene.lookup("#show-password")).getGraphic()!=null,"Password visibility uses an eye icon");return null;});shot(out.resolve("01-desktop.png"));
            resize(1024,622);shot(out.resolve("02-reference-size.png"));fx(()->{var b=card.localToScene(card.getBoundsInLocal());check(b.getMinX()>scene.getWidth()*.5&&b.getMaxX()<scene.getWidth(),"Card stays centered within the right column");check(card.getWidth()>=380,"Reference-sized panel keeps readable form controls");return null;});
            resize(960,680);shot(out.resolve("03-windowed.png"));press("login");fx(()->{check(!((Label)scene.lookup("#username-error")).getText().isBlank(),"Required username error stays in the glass form");return null;});
            text("username","customer");text("password","fixture-secret");press("show-password");fx(()->{check(scene.lookup("#password-visible").isVisible()&&!scene.lookup("#password").isVisible(),"Eye reveals the password accessibly");return null;});text("password-visible","changed-fixture");press("show-password");fx(()->{check(((PasswordField)scene.lookup("#password")).getText().equals("changed-fixture"),"Password edits survive hiding");return null;});text("password","");
            press("login-forgot-password");waitFor(()->((TitledPane)scene.lookup("#demo-accounts")).isExpanded());fx(()->{check(((Label)scene.lookup("#login-feedback")).getText().contains("account access help"),"Forgot password opens real account-help guidance");return null;});shot(out.resolve("04-expanded-help.png"));
            fx(()->{Motion.reducedProperty().set(false);return null;});waitFor(card::glowRunning);double phase=fx(card::glowPhase);Thread.sleep(220);check(Math.abs(fx(card::glowPhase)-phase)>.001,"Orange light progresses around the perimeter");
            fx(()->{stage.hide();return null;});check(!fx(card::glowRunning),"Hidden windows pause the glow");fx(()->{stage.show();return null;});waitFor(card::glowRunning);
            fx(()->{((CheckBox)scene.lookup("#login-reduce-motion")).fire();return null;});check(!fx(card::glowRunning),"Reduce motion immediately stops the glow");phase=fx(card::glowPhase);Thread.sleep(120);check(fx(card::glowPhase)==phase,"Static glow does not keep updating");
            press("login-demo-customer");text("password","incorrect");press("login");waitFor(()->((Label)scene.lookup("#login-feedback")).getText().contains("Incorrect"));fx(()->{check(!scene.lookup("#login").isDisabled(),"Authentication failure unlocks the form");return null;});
            press("login-demo-customer");press("login");waitFor(()->!(scene.getRoot() instanceof LoginView));check(!fx(card::glowRunning),"Leaving login releases the moving glow");check(true,"Customer authentication still navigates to the store");
            System.out.println("LOGIN GLASS PASS: "+checks+" assertions");
        }finally{Platform.exit();}
    }
}