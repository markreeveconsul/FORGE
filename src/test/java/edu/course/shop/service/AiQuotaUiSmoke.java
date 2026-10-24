package edu.course.shop.service;

import edu.course.shop.*;
import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import edu.course.shop.ui.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Native quota messages and help screens, with a fixture database and no network. */
public final class AiQuotaUiSmoke {
 private static Scene scene;private static int checks;
 private static <T>T fx(Callable<T> action)throws Exception{var task=new FutureTask<T>(()->{if(scene!=null){scene.getRoot().applyCss();scene.getRoot().layout();}return action.call();});Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
 private static void waitFor(BooleanSupplier condition)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(!fx(condition::getAsBoolean)){if(System.nanoTime()>deadline)throw new AssertionError("Quota UI timed out");Thread.sleep(25);}}
 private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;System.out.println("PASS "+message);}
 private static void press(String id)throws Exception{fx(()->{((ButtonBase)scene.lookup("#"+id)).fire();return null;});}
 private static String labels(Node node){return node.lookupAll(".label").stream().map(n->((Label)n).getText()).collect(java.util.stream.Collectors.joining("\n"));}
 private static Window popup(String id){return Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene().lookup("#"+id)!=null).findFirst().orElse(null);}
 private static void close(Window window){((Button)((DialogPane)window.getScene().getRoot()).lookupButton(ButtonType.CLOSE)).fire();}
 private static void shot(Path file)throws Exception{fx(()->{var image=scene.getRoot().snapshot(null,null);var png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));ImageIO.write(png,"png",file.toFile());return null;});}
 private static GeminiAssistant client(AtomicInteger requests){return new GeminiAssistant(new GeminiAssistant.Config("quota-ui-key","gemini-test",true),r->{requests.incrementAndGet();return AiQuotaTest.reply("GenerateContentInputTokensPerMinutePerProjectPerModel-FreeTier","generativelanguage.googleapis.com/generate_content_free_tier_input_token_count","250000",30);});}
 public static void main(String[] args)throws Exception{
  Path out=Path.of(args[0]);Files.createDirectories(out);var db=new DatabaseManager(out.resolve("quota-ui-"+System.nanoTime()+".db"));db.initialize();var p=new ProductRepository();var o=new OrderRepository();
  var services=new Services(new AuthService(db,new UserRepository()),new ProductService(db,p),new CheckoutService(db,p,o),new OrderService(db,o),new AdminService(db,new AdminRepository()),new ProductReviewService(db,new ProductReviewRepository()));
  User admin=services.auth().login("admin","admin123");services.products().save(admin,0,"Quota fixture graphics card",Category.PC_PARTS,BigDecimal.TEN,3,true);Product product=services.products().search(admin,"Quota fixture",null).getFirst();Cart cart=new Cart(0);cart.add(product,1);
  var ready=new CountDownLatch(1);Platform.startup(ready::countDown);ready.await();Platform.setImplicitExit(false);
  AtomicInteger requests=new AtomicInteger();GeminiAssistant customer=client(requests);
  try{
   fx(()->{Motion.reducedProperty().set(true);new AssistantPreferences().rememberGeminiConsent(0);var panel=new AssistantPanel(services,new Guest(),()->cart,()->null,customer,x->{},()->{},new SavedItems(services.accounts()),()->{});StackPane root=new StackPane(panel);StackPane.setAlignment(panel,javafx.geometry.Pos.CENTER_RIGHT);scene=new Scene(root,1200,900);scene.getStylesheets().add(AiQuotaUiSmoke.class.getResource("/shop.css").toExternalForm());Stage stage=new Stage();stage.setScene(scene);stage.show();return null;});
   fx(()->{((TextField)scene.lookup("#assistant-question")).setText("Review my cart");return null;});press("assistant-send");waitFor(()->scene.lookup("#assistant-error")!=null);
   fx(()->{String text=labels(scene.lookup("#assistant-error"));check(text.contains("input tokens per minute"),"Customer sees the actual minute-token restriction");check(text.contains("250000")&&text.contains("30 seconds"),"Customer sees the reported allowance and provider retry hint");check(!text.contains("daily quota is exhausted")&&!text.contains("quota-ui-key"),"Customer message has no false daily claim or key");Node help=scene.lookup("#assistant-limits");check(help.localToScene(help.getBoundsInLocal()).getMaxX()<=scene.getWidth(),"AI limits button fits the customer footer");return null;});
   shot(out.resolve("01-customer-token-limit.png"));press("assistant-limits");waitFor(()->scene.lookup("#assistant-limits-info")!=null);
   fx(()->{String text=labels(scene.lookup("#assistant-limits-info"));check(text.contains("past report, not live usage")&&text.contains("cannot reliably show how many requests remain"),"Customer help explains the limits of quota visibility");return null;});check(requests.get()==1,"Reading customer AI limits makes no extra API request");shot(out.resolve("02-customer-limits-help.png"));
   GeminiAssistant manager=client(requests);
   fx(()->{Runnable noop=()->{};var workspace=new AdminWorkspace(admin,services,noop,noop,noop,noop,noop,noop,noop);workspace.select("admin-nav-ai-manager");workspace.pageHost().setCenter(new AiManagerView(admin,services,manager));scene.setRoot(new StackPane(workspace));return null;});
   waitFor(()->scene.lookup("#ai-manager-command")!=null);fx(()->{((TextField)scene.lookup("#ai-manager-command")).setText("Draft missing product descriptions");return null;});press("ai-manager-run");waitFor(()->scene.lookup("#ai-manager-connection-detail")!=null);
   fx(()->{String message=((Label)scene.lookup("#ai-manager-connection-detail")).getText();check(message.contains("input tokens per minute")&&message.contains("250000"),"Admin content command preserves the specific quota explanation");check(scene.lookup("#ai-manager-apply")==null,"A quota failure never offers fabricated draft changes");return null;});shot(out.resolve("03-admin-token-limit.png"));
   press("ai-manager-limits");waitFor(()->popup("ai-manager-limits-info")!=null);
   fx(()->{Window dialog=popup("ai-manager-limits-info");String text=((TextArea)dialog.getScene().lookup("#ai-manager-limits-info")).getText();check(text.contains("30 seconds")&&text.contains("not a live usage counter"),"Admin AI limits explains provider hints and shared allowances");close(dialog);return null;});check(requests.get()==2,"Admin limits help also makes no extra API request");
   press("ai-manager-diagnostics");waitFor(()->popup("ai-diagnostic-history")!=null);
   fx(()->{Window dialog=popup("ai-diagnostic-history");String text=((TextArea)dialog.getScene().lookup("#ai-diagnostic-history")).getText();check(text.contains("quotaId=GenerateContentInputTokensPerMinute")&&text.contains("quotaLimit=250000")&&text.contains("providerRetryAfterSeconds=30"),"Admin diagnostics records the safe quota ID, value and retry hint");check(!text.contains("quota-ui-key")&&!text.contains("Draft missing")&&!text.contains("Review my cart"),"Quota diagnostics excludes credentials and questions");close(dialog);((AiManagerView)scene.lookup("#ai-manager-page")).dispose();return null;});
   System.out.println("AI quota UI checks passed: "+checks);
  }finally{Platform.exit();}
 }
}
