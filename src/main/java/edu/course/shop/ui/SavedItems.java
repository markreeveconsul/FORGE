package edu.course.shop.ui;
import edu.course.shop.model.*;
import edu.course.shop.service.AccountService;
import javafx.collections.*;
import javafx.scene.control.Button;
import javafx.concurrent.Task;
import java.util.*;

/** Guest selections are session-only; customer selections are persisted before updating the UI. */
public final class SavedItems {
    private final ObservableSet<Long> ids=FXCollections.observableSet(new HashSet<>());
    private final AccountService accounts;
    private final Map<Long,Task<Void>> pending=new HashMap<>();
    private User user=new Guest();
    public SavedItems(AccountService accounts) { this.accounts=accounts; }
    public Set<Long> snapshot() { return Set.copyOf(ids); }
    public void reset() { user=new Guest();pending.clear();ids.clear(); }
    public void signedIn(User customer,Set<Long> stored) { user=customer;pending.clear();ids.clear();ids.addAll(stored); }
    public void observe(javafx.scene.Node owner,Runnable action) {
        SetChangeListener<Long> listener=change->action.run();ids.addListener(new WeakSetChangeListener<>(listener));owner.getProperties().put("market.savedObserver",listener);
    }
    public Button heart(Product product) {
        Button heart=Ui.button("",()->{});heart.setId("save-"+product.getId());heart.getStyleClass().add("save-heart");
        Runnable render=()->{boolean saved=ids.contains(product.getId());heart.setText(saved?"♥":"♡");heart.setAccessibleText(pending.containsKey(product.getId())?"Updating saved item: "+product.getName():(saved?"Remove ":"Save ")+product.getName()+" in saved items");heart.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("selected"),saved);};
        SetChangeListener<Long> listener=c->render.run();ids.addListener(new WeakSetChangeListener<>(listener));heart.getProperties().put("market.savedListener",listener);
        heart.setOnAction(e->{
            if(pending.containsKey(product.getId()))return;
            boolean save=!ids.contains(product.getId());Runnable finish=()->{if(save)ids.add(product.getId());else ids.remove(product.getId());render.run();Motion.reveal(heart);};
            if(user instanceof Guest){finish.run();return;}
            User actor=user;
            Task<Void> task=new Task<>(){protected Void call(){accounts.saveItem(actor,product.getId(),save);return null;}};
            pending.put(product.getId(),task);render.run();
            // Disabling a focused heart makes JavaFX focus/scroll to another product.
            // Keep the control available for focus; pending guards duplicate mutations.
            task.setOnSucceeded(done->{if(user!=actor||pending.get(product.getId())!=task)return;pending.remove(product.getId());finish.run();});
            task.setOnFailed(error->{if(user!=actor||pending.get(product.getId())!=task)return;pending.remove(product.getId());render.run();Ui.error(task.getException());});
            Thread worker=new Thread(task,"shop-save-favorite");worker.setDaemon(true);worker.start();
        });render.run();return heart;
    }
}
