package edu.course.shop.service;

import edu.course.shop.Services;
import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import edu.course.shop.ui.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Manual JavaFX regression check with a fresh database and mocked AI; never calls Google. */
public final class AssistantConversationSmoke {
    private static Scene scene;
    private static Services services;
    private static Product gpu;
    private static int checks;

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task=new FutureTask<>(()->{
            if(scene!=null){scene.getRoot().applyCss();scene.getRoot().layout();}
            return action.call();
        });
        Platform.runLater(task);return task.get(15,TimeUnit.SECONDS);
    }
    private static void waitFor(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(!fx(condition::getAsBoolean)){
            if(System.nanoTime()>deadline)throw new AssertionError("Timed out waiting for assistant");
            Thread.sleep(20);
        }
    }
    private static void check(boolean result,String message){
        if(!result)throw new AssertionError(message);
        checks++;System.out.println("PASS "+message);
    }
    private static void panel(GeminiAssistant ai) throws Exception {
        fx(()->{
            Cart cart=new Cart(0);
            var panel=new AssistantPanel(services,new Guest(),()->cart,()->null,ai,p->{},()->{},new SavedItems(services.accounts()),()->{});
            scene=new Scene(new StackPane(panel),440,800);
            scene.getStylesheets().add(AssistantConversationSmoke.class.getResource("/shop.css").toExternalForm());
            return null;
        });
    }
    private static void ask(String question) throws Exception {
        fx(()->{((TextField)scene.lookup("#assistant-question")).setText(question);((Button)scene.lookup("#assistant-send")).fire();return null;});
    }
    private static void idle() throws Exception {waitFor(()->!scene.lookup("#assistant-question").isDisabled());}
    private static int cards(){return scene.getRoot().lookupAll(".chat-product-card").size();}
    private static GeminiAssistant ai(GeminiAssistant.Sender sender){
        return new GeminiAssistant(new GeminiAssistant.Config("test-fixture","gemini-test",true),sender);
    }
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("forge-conversation-smoke-");
        String previousHome=System.getProperty("user.home");
        System.setProperty("user.home",root.toString());
        DatabaseManager db=new DatabaseManager(root.resolve("test.db"));db.initialize();
        var products=new ProductRepository();var orders=new OrderRepository();
        services=new Services(new AuthService(db,new UserRepository()),new ProductService(db,products),
            new CheckoutService(db,products,orders),new OrderService(db,orders),new AdminService(db,new AdminRepository()),
            new ProductReviewService(db,new ProductReviewRepository()));
        User admin=services.auth().login("admin","admin123");
        for(Product p:services.products().search(admin,"",null))
            services.products().save(admin,p.getId(),p.getName(),p.getCategory(),p.getPrice(),p.getStock(),false);
        services.products().save(admin,0,"Nebulon GPU",Category.PC_PARTS,new BigDecimal("400"),5,true);
        services.products().save(admin,0,"Ducati Desktop",Category.PC_SETS,new BigDecimal("1200"),5,true);
        gpu=services.products().search(new Guest(),"Nebulon",null).getFirst();
        var ready=new CountDownLatch(1);Platform.startup(ready::countDown);
        if(!ready.await(15,TimeUnit.SECONDS))throw new AssertionError("JavaFX startup timed out");
        Platform.setImplicitExit(false);
        try {
            new AssistantPreferences().rememberGeminiConsent(0);
            CountDownLatch requested=new CountDownLatch(1),release=new CountDownLatch(1);
            panel(ai(request->{requested.countDown();if(!release.await(15,TimeUnit.SECONDS))throw new AssertionError("Mock response timed out");return new GeminiAssistant.Reply(200,"{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"Here is the listed GPU.\"}]}}]}");}));
            ask("Explain Nebulon GPU");
            check(requested.await(15,TimeUnit.SECONDS),"AI explanation reaches the mocked provider");
            fx(()->{check(cards()==0,"No premature cards while the answer is loading");check(scene.lookup("#assistant-thinking")!=null,"Loading indicator stays visible");return null;});
            release.countDown();idle();
            fx(()->{check(cards()==1&&scene.lookup("#assistant-product-"+gpu.getId())!=null,"Completed answer renders one card even without specifications");return null;});

            panel(ai(request->new GeminiAssistant.Reply(503,"{}")));
            ask("Explain Nebulon GPU");idle();
            fx(()->{check(cards()==0&&scene.lookup("#assistant-error")!=null,"Failed response has an error and no speculative cards");return null;});

            CountDownLatch lateRequest=new CountDownLatch(1),lateRelease=new CountDownLatch(1),lateFinished=new CountDownLatch(1);
            panel(ai(request->{
                lateRequest.countDown();
                // Simulate a provider that returns after cancellation instead of respecting interruption.
                boolean released=false;while(!released){try{released=lateRelease.await(20,TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){}}
                lateFinished.countDown();
                return new GeminiAssistant.Reply(200,"{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"Old answer\"}]}}]}");
            }));
            ask("Explain Nebulon GPU");check(lateRequest.await(15,TimeUnit.SECONDS),"Cancellation test starts an AI request");
            fx(()->{((Button)scene.lookup("#assistant-stop")).fire();return null;});
            ask("i said pc sets not pc parts");idle();lateRelease.countDown();
            check(lateFinished.await(15,TimeUnit.SECONDS),"Cancelled provider response finishes late");
            fx(()->{check(cards()==1&&scene.lookup("#assistant-product-"+gpu.getId())==null,"Correction shows only the requested category");return null;});

            new AssistantPreferences().forgetGeminiConsent(0);
            panel(ai(request->{throw new AssertionError("Superseded consent must not send a request");}));
            ask("Explain Nebulon GPU");idle();
            Button oldConsent=fx(()->(Button)scene.lookup("#assistant-consent-continue"));
            fx(()->{check(oldConsent!=null&&cards()==0,"Consent request has no premature cards");return null;});
            ask("i said pc sets not pc parts");idle();
            fx(()->{oldConsent.fire();check(cards()==1&&!scene.lookup("#assistant-question").isDisabled(),"Older consent cannot restart an unwanted response");return null;});
            check(!new AssistantPreferences().hasGeminiConsent(0),"Superseded consent does not persist approval");
            System.out.println("Assistant conversation checks passed: "+checks);
        } finally {Platform.exit();System.setProperty("user.home",previousHome);}
    }
}
