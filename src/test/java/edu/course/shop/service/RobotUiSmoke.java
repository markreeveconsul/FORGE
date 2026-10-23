package edu.course.shop.service;

import com.google.gson.Gson;
import edu.course.shop.*;
import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import edu.course.shop.ui.*;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Actual native playback, lifecycle, storefront and scripted AI states; isolated records, no API calls. */
public final class RobotUiSmoke {
    private static Scene scene;private static Stage stage;private static int checks;
    private static <T>T fx(Callable<T> job)throws Exception{FutureTask<T> task=new FutureTask<>(()->{if(scene!=null){scene.getRoot().applyCss();scene.getRoot().layout();}return job.call();});Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private static void waitFor(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(!fx(condition::getAsBoolean)){if(System.nanoTime()>end)throw new AssertionError("Timed out waiting for robot");Thread.sleep(30);}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;System.out.println("PASS "+message);}
    private static ImageView picture(RobotAvatar robot){return (ImageView)robot.getChildrenUnmodifiable().getFirst();}
    private static RobotAvatar chatRobot(){return (RobotAvatar)scene.lookup("#assistant-robot");}
    private static void press(String id)throws Exception{fx(()->{((Button)scene.lookup("#"+id)).fire();return null;});}
    private static void ask(String text)throws Exception{fx(()->{((TextField)scene.lookup("#assistant-question")).setText(text);((Button)scene.lookup("#assistant-send")).fire();return null;});}
    private static void shot(Path file)throws Exception{fx(()->{var image=scene.getRoot().snapshot(null,null);BufferedImage png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));ImageIO.write(png,"png",file.toFile());return null;});}
    private static GeminiAssistant.Reply reply(String text){return new GeminiAssistant.Reply(200,new Gson().toJson(Map.of("candidates",List.of(Map.of("finishReason","STOP","content",Map.of("parts",List.of(Map.of("text",text))))))));}
    private static void panel(Services services,Cart cart,GeminiAssistant gemini)throws Exception{fx(()->{AssistantPanel panel=new AssistantPanel(services,new Guest(),()->cart,()->null,gemini,p->{},()->{},new SavedItems(services.accounts()),()->{});StackPane shell=new StackPane(panel);StackPane.setAlignment(panel,Pos.CENTER_RIGHT);scene.setRoot(shell);return null;});}
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[0]);Files.createDirectories(out);DatabaseManager db=new DatabaseManager(out.resolve("robot-"+System.nanoTime()+".db"));db.initialize();ProductRepository pr=new ProductRepository();OrderRepository or=new OrderRepository();Services services=new Services(new AuthService(db,new UserRepository()),new ProductService(db,pr),new CheckoutService(db,pr,or),new OrderService(db,or),new AdminService(db,new AdminRepository()),new ProductReviewService(db,new ProductReviewRepository()));
        User admin=services.auth().login("admin","admin123");try(var c=db.open();var s=c.createStatement()){s.executeUpdate("DELETE FROM products");}
        byte[] art;try(var in=RobotUiSmoke.class.getResourceAsStream("/images/category-laptop.png")){art=in.readAllBytes();}
        services.products().save(admin,0,"Robot fixture notebook",Category.LAPTOPS,new BigDecimal("899.00"),8,true,"A synthetic test notebook.",art,List.of(),"Memory: 16 GB");Product product=services.products().search(new Guest(),"",null).getFirst();Cart cart=new Cart(0);cart.add(product,1);
        CountDownLatch ready=new CountDownLatch(1);Platform.startup(ready::countDown);ready.await();Platform.setImplicitExit(false);
        try{
            RobotAvatar avatar=fx(()->{Motion.reducedProperty().set(false);stage=new Stage();RobotAvatar robot=new RobotAvatar(80);HBox gallery=new HBox(24);gallery.setAlignment(Pos.CENTER);gallery.setStyle("-fx-background-color:#171719;-fx-padding:50;");for(RobotAvatar.State state:RobotAvatar.State.values()){RobotAvatar still=new RobotAvatar(80);still.setState(state);still.setAnimated(false);Label name=new Label(state.name());name.setStyle("-fx-text-fill:white;");VBox cell=new VBox(18,still,name);cell.setAlignment(Pos.CENTER);gallery.getChildren().add(cell);}VBox root=new VBox(40,gallery,robot);root.setAlignment(Pos.CENTER);scene=new Scene(root,1280,820);scene.getStylesheets().add(RobotUiSmoke.class.getResource("/shop.css").toExternalForm());stage.setScene(scene);stage.show();return robot;});
            Set<Image> atlases=Collections.newSetFromMap(new IdentityHashMap<>());
            for(RobotAvatar.State state:RobotAvatar.State.values()){
                fx(()->{avatar.setState(state);return null;});waitFor(()->picture(avatar).getImage().getWidth()>162);
                fx(()->{Image image=picture(avatar).getImage();check(!image.isError()&&image.getWidth()==1296&&image.getHeight()==1872,state+" atlas is decoded at the expected geometry");atlases.add(image);return null;});
            }
            long bytes=atlases.stream().mapToLong(i->(long)(i.getWidth()*i.getHeight()*4)).sum();check(bytes<64L*1024*1024,"All six shared atlases stay below 64 MiB decoded");
            fx(()->{avatar.setState(RobotAvatar.State.IDLE);return null;});var before=fx(()->picture(avatar).getViewport());Thread.sleep(220);check(!before.equals(fx(()->picture(avatar).getViewport())),"Visible avatar advances native animation frames");
            RobotAvatar shared=fx(()->{RobotAvatar another=new RobotAvatar(32);((VBox)scene.getRoot()).getChildren().add(another);return another;});waitFor(()->picture(shared).getImage().getWidth()>162);check(fx(()->picture(shared).getImage()==picture(avatar).getImage()),"Instances share the same decoded atlas");
            fx(()->{((VBox)scene.getRoot()).getChildren().remove(shared);return null;});check(fx(()->picture(shared).getImage().getWidth()==162),"Detached avatar falls back to a static frame");
            check(fx(()->avatar.getWidth()==80&&avatar.getHeight()==80),"Animation preserves the requested layout size");shot(out.resolve("01-six-states.png"));
            fx(()->{avatar.getParent().setVisible(false);return null;});check(fx(()->picture(avatar).getImage().getWidth()==162),"Hidden ancestors stop animated playback");fx(()->{avatar.getParent().setVisible(true);return null;});waitFor(()->picture(avatar).getImage().getWidth()>162);
            fx(()->{Motion.reducedProperty().set(true);avatar.setState(RobotAvatar.State.ERROR);return null;});check(fx(()->picture(avatar).getImage().getWidth()==162&&avatar.getAccessibleText().contains("error")),"Reduce motion retains an accessible static expression");
            fx(()->{avatar.react(RobotAvatar.State.HAPPY);return null;});waitFor(()->avatar.getState()==RobotAvatar.State.IDLE);check(true,"Success reaction returns to idle");
            fx(()->{Motion.reducedProperty().set(false);stage.hide();return null;});check(fx(()->picture(avatar).getImage().getWidth()==162),"Hidden window stops playback");fx(()->{stage.show();return null;});waitFor(()->picture(avatar).getImage().getWidth()>162);
            fx(()->{new ShopWindow(stage,services).showStorefront();return null;});waitFor(()->scene.lookup("#assistant-launcher-robot")!=null);waitFor(()->picture((RobotAvatar)scene.lookup("#assistant-launcher-robot")).getImage().getWidth()>162);
            check(fx(()->picture(chatRobot()).getImage().getWidth()==162),"Closed chat header does not animate offscreen");shot(out.resolve("02-storefront-launcher.png"));press("assistant-launcher");waitFor(()->picture(chatRobot()).getImage().getWidth()>162);
            check(fx(()->picture((RobotAvatar)scene.lookup("#assistant-launcher-robot")).getImage().getWidth()==162),"Opening chat stops the hidden launcher robot");
            waitFor(()->Math.abs(scene.lookup("#assistant-panel").getTranslateX())<.5);shot(out.resolve("03-chat-welcome.png"));fx(()->{Node close=scene.lookup("#assistant-close");check(close.localToScene(close.getLayoutBounds()).getMaxX()<=scene.getWidth(),"Chat header controls stay within the window");return null;});shot(out.resolve("03-chat-welcome.png"));
            fx(()->{((TextField)scene.lookup("#assistant-question")).setText("Show me laptops");((Button)scene.lookup("#assistant-send")).fire();check(chatRobot().getState()==RobotAvatar.State.SEARCHING,"Local catalog lookup selects the searching expression");return null;});waitFor(()->scene.lookup("#assistant-product-"+product.getId())!=null);check(fx(()->chatRobot().getState()==RobotAvatar.State.HAPPY),"Product results select success only after completion");shot(out.resolve("04-chat-results.png"));
            fx(()->{new AssistantPreferences().rememberGeminiConsent(0);return null;});CountDownLatch requested=new CountDownLatch(1),release=new CountDownLatch(1);
            GeminiAssistant mock=new GeminiAssistant(new GeminiAssistant.Config("test-fixture","gemini-test",true),request->{requested.countDown();if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Fixture timeout");return reply("Your cart contains one listed notebook. Review its recorded memory before buying.");});panel(services,cart,mock);ask("Review my cart");check(requested.await(20,TimeUnit.SECONDS),"Scripted explanation reaches the transport without a real API request");check(fx(()->chatRobot().getState()==RobotAvatar.State.THINKING),"Pending explanation selects thinking");shot(out.resolve("05-chat-thinking.png"));release.countDown();waitFor(()->chatRobot().getState()==RobotAvatar.State.HAPPY);check(true,"Completed explanation selects success");
            GeminiAssistant broken=new GeminiAssistant(new GeminiAssistant.Config("test-fixture","gemini-test",true),request->new GeminiAssistant.Reply(503,"{}"));panel(services,cart,broken);ask("Review my cart");waitFor(()->scene.lookup("#assistant-error")!=null);check(fx(()->chatRobot().getState()==RobotAvatar.State.ERROR),"Failed explanation selects error instead of success");shot(out.resolve("06-chat-error.png"));press("assistant-new-chat");check(fx(()->chatRobot().getState()==RobotAvatar.State.IDLE),"New chat clears the previous activity expression");
            System.out.println("ROBOT UI PASS: "+checks+" assertions; no network requests");
        }finally{Platform.exit();}
    }
}