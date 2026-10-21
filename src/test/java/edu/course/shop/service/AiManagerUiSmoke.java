package edu.course.shop.service;

import com.google.gson.Gson;
import edu.course.shop.Services;
import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import edu.course.shop.ui.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Full admin navigation and reviewed-change smoke with an isolated SQLite fixture and no network calls. */
public final class AiManagerUiSmoke {
    private static Scene scene;private static int checks;
    private static <T> T fx(Callable<T> action)throws Exception{FutureTask<T> task=new FutureTask<>(()->{if(scene!=null){scene.getRoot().applyCss();scene.getRoot().layout();}return action.call();});Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private static void waitFor(BooleanSupplier condition)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(!fx(condition::getAsBoolean)){if(System.nanoTime()>deadline)throw new AssertionError("Timed out waiting for AI Manager");Thread.sleep(25);}}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;System.out.println("PASS "+message);}
    private static void press(String id)throws Exception{fx(()->{((Button)scene.lookup("#"+id)).fire();return null;});}
    private static void snapshot(Path file,int width)throws Exception{fx(()->{scene.getRoot().resize(width,860);for(int i=0;i<4;i++){scene.getRoot().applyCss();scene.getRoot().layout();}var image=scene.getRoot().snapshot(null,null);BufferedImage png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));ImageIO.write(png,"png",file.toFile());return null;});}
    private static GeminiAssistant.Reply reply(Object part){return new GeminiAssistant.Reply(200,new Gson().toJson(Map.of("candidates",List.of(Map.of("finishReason","STOP","content",Map.of("parts",List.of(part)))))));}
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);Files.createDirectories(root);DatabaseManager db=new DatabaseManager(root.resolve("fixture-"+System.nanoTime()+".db"));db.initialize();var pr=new ProductRepository();var or=new OrderRepository();
        Services services=new Services(new AuthService(db,new UserRepository()),new ProductService(db,pr),new CheckoutService(db,pr,or),new OrderService(db,or),new AdminService(db,new AdminRepository()),new ProductReviewService(db,new ProductReviewRepository()));
        User admin=services.auth().login("admin","admin123");try(var c=db.open();var s=c.createStatement()){s.executeUpdate("DELETE FROM products");}
        BufferedImage art=new BufferedImage(24,24,BufferedImage.TYPE_INT_RGB);ByteArrayOutputStream png=new ByteArrayOutputStream();ImageIO.write(art,"png",png);
        services.products().save(admin,0,"Aurora graphics card",Category.PC_PARTS,new BigDecimal("199.00"),3,true,"",png.toByteArray(),List.of(),"Memory: 8 GB");
        services.products().save(admin,0,"Studio notebook",Category.LAPTOPS,new BigDecimal("899.00"),8,false,"A notebook for the studio.",png.toByteArray(),List.of(),"Memory: 16 GB");
        Product gpu=services.products().search(admin,"Aurora",null).getFirst();
        CountDownLatch ready=new CountDownLatch(1);Platform.startup(ready::countDown);ready.await();Platform.setImplicitExit(false);
        try{
            fx(()->{Motion.reducedProperty().set(true);Stage stage=new Stage();scene=new Scene(new StackPane(),1280,860);scene.getStylesheets().add(AiManagerUiSmoke.class.getResource("/shop.css").toExternalForm());stage.setScene(scene);new ShopWindow(stage,services).showLogin();scene.getRoot().applyCss();scene.getRoot().layout();((TextField)scene.lookup("#username")).setText("admin");((PasswordField)scene.lookup("#password")).setText("admin123");((Button)scene.lookup("#login")).fire();return null;});
            waitFor(()->scene.lookup("#admin-nav-ai-manager")!=null);press("admin-nav-ai-manager");
            waitFor(()->scene.lookup("#ai-manager-chip-fix")!=null);
            fx(()->{check(scene.lookup("#ai-manager-page")!=null&&scene.lookup("#admin-page")==null,"AI Manager replaces Products in the right content area");check(scene.lookup("#admin-nav-ai-manager").getStyleClass().contains("admin-nav-active"),"Sidebar marks AI Manager active");return null;});
            snapshot(root.resolve("01-empty-desktop.png"),1280);snapshot(root.resolve("02-empty-compact.png"),960);
            fx(()->{check(scene.lookup("#ai-manager-run").localToScene(scene.lookup("#ai-manager-run").getBoundsInLocal()).getMaxX()<=960,"Command controls fit the compact admin workspace");return null;});
            press("admin-nav-orders");waitFor(()->scene.lookup("#admin-report-orders")!=null);
            fx(()->{check(scene.lookup("#ai-manager-page")==null,"Switching modules removes the AI Manager view");return null;});
            press("admin-nav-ai-manager");waitFor(()->scene.lookup("#ai-manager-page")!=null);
            CountDownLatch drafting=new CountDownLatch(1),release=new CountDownLatch(1);
            GeminiAssistant mock=new GeminiAssistant(new GeminiAssistant.Config("test-fixture","gemini-test",true),request->{
                String body=AiManagerServiceTest.requestBody(request);
                if(body.contains("functionDeclarations"))return reply(Map.of("functionCall",Map.of("name","fix_content","args",Map.of("ids",List.of(gpu.getId())))));
                drafting.countDown();if(!release.await(20,TimeUnit.SECONDS))throw new AssertionError("Draft release timed out");
                String draft=new Gson().toJson(Map.of("drafts",List.of(Map.of("id",gpu.getId(),"description","Aurora graphics card with 8 GB of recorded memory.","specifications","","exactModel",false,"sources",List.of(),"note","Based on the recorded specifications."))));
                return reply(Map.of("text",draft));
            });
            fx(()->{AiManagerView old=(AiManagerView)scene.lookup("#ai-manager-page");BorderPane shell=(BorderPane)old.getParent();old.dispose();shell.setCenter(new AiManagerView(admin,services,mock));scene.getRoot().resize(1280,860);return null;});
            waitFor(()->scene.lookup("#ai-manager-chip-fix")!=null);
            fx(()->{((TextField)scene.lookup("#ai-manager-command")).setText("Please fill in the product details");return null;});press("ai-manager-run");
            check(drafting.await(20,TimeUnit.SECONDS),"Natural command reaches the mocked function-call and content pipeline");
            fx(()->{check(scene.lookup("#ai-manager-loading")!=null,"Loading placeholders stay inside the manager results");check(!scene.lookup("#nav-inventory").isDisabled(),"Other admin navigation remains available while AI thinks");return null;});
            snapshot(root.resolve("03-loading.png"),1280);release.countDown();waitFor(()->scene.lookup("#ai-manager-apply")!=null);
            check(services.products().get(gpu.getId()).getDescription().isBlank(),"Preview does not mutate the database");
            fx(()->{TextArea field=(TextArea)scene.lookup("#ai-manager-drafts").lookup(".text-area");field.setText("Administrator-reviewed graphics card description.");return null;});
            snapshot(root.resolve("04-content-preview.png"),1280);press("ai-manager-apply");waitFor(()->scene.lookup("#ai-manager-success")!=null);
            waitFor(()->((Button)scene.lookup("#ai-manager-chip-fix")).getText().startsWith("Fix 0"));
            check(services.products().get(gpu.getId()).getDescription().equals("Administrator-reviewed graphics card description."),"Reviewed edits are saved and fresh content counts reach zero");
            fx(()->{check(scene.lookup("#ai-manager-products") instanceof TableView<?> table&&table.getItems().size()==1,"Success renders refreshed product rows");return null;});
            snapshot(root.resolve("05-applied.png"),1280);
            var attempts=new java.util.concurrent.atomic.AtomicInteger();
            GeminiAssistant unavailable=new GeminiAssistant(new GeminiAssistant.Config("test-fixture","gemini-test",true),request->{
                if(attempts.getAndIncrement()==0)return new GeminiAssistant.Reply(503,"{}");
                return reply(Map.of("functionCall",Map.of("name","find_low_stock","args",Map.of())));
            });
            fx(()->{AiManagerView old=(AiManagerView)scene.lookup("#ai-manager-page");BorderPane shell=(BorderPane)old.getParent();old.dispose();shell.setCenter(new AiManagerView(admin,services,unavailable));return null;});
            waitFor(()->scene.lookup("#ai-manager-chip-stock")!=null);press("ai-manager-chip-stock");
            waitFor(()->scene.lookup("#ai-manager-connection-issue")!=null);
            fx(()->{check(((Label)scene.lookup("#ai-manager-connection-detail")).getText().contains("Command interpretation: HTTP 503"),"Fallback shows the failed stage and actual HTTP status");check(((Label)scene.lookup("#ai-manager-status")).getText().contains("Local fallback"),"Provider failures are distinguished from an unconfigured AI");return null;});
            snapshot(root.resolve("06-connection-error.png"),1280);press("ai-manager-retry");
            waitFor(()->scene.lookup("#ai-manager-connection-issue")==null&&scene.lookup("#ai-manager-products")!=null);
            fx(()->{check(((Label)scene.lookup("#ai-manager-status")).getText().contains("AI Active"),"Retry reconnects successfully and clears the old error");return null;});
            fx(()->{((AiManagerView)scene.lookup("#ai-manager-page")).dispose();return null;});
            System.out.println("AI Manager UI checks passed: "+checks);
        }finally{Platform.exit();}
    }
}
