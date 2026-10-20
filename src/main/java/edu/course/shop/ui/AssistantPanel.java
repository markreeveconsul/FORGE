package edu.course.shop.ui;

import edu.course.shop.Services;
import edu.course.shop.model.*;
import edu.course.shop.service.*;
import javafx.animation.*;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.*;

/** Non-modal conversation panel: local records, bounded memory and explicit purchase actions. */
public final class AssistantPanel extends BorderPane {
    private final Services services;
    private final User user;
    private final Supplier<Cart> cart;
    private final Supplier<Product> page;
    private final GeminiAssistant gemini;
    private final CatalogAssistant retrieval;
    private final Consumer<Product> viewProduct;
    private final Runnable cartChanged;
    private final VBox messages = new VBox(12);
    private final ScrollPane scroll = Ui.scroll(messages);
    private final TextField input = new TextField();
    private final Button send = Ui.button("↑", this::submit);
    private final Label context = Ui.label("Browsing the catalog", "chat-context-text");
    private CatalogAssistant.Memory memory = CatalogAssistant.Memory.empty();
    private Task<?> pending;
    private Node thinking;
    private Timeline dots;
    private int generation;
    private boolean consent, busy;
    private String lastFailed;
    private CatalogAssistant.Plan awaitingConsent;
    private String awaitingQuestion;
    private record Result(CatalogAssistant.Plan plan, String text) { }

    public AssistantPanel(Services services, User user, Supplier<Cart> cart, Supplier<Product> page,
                          GeminiAssistant gemini, Consumer<Product> viewProduct, Runnable cartChanged, Runnable close) {
        this.services=services;this.user=user;this.cart=cart;this.page=page;this.gemini=gemini;
        this.retrieval=new CatalogAssistant(services);this.viewProduct=viewProduct;this.cartChanged=cartChanged;
        setId("assistant-panel");getStyleClass().add("forge-chat");setMinSize(0,0);setPrefWidth(420);setMaxWidth(420);
        Label spark=Ui.label("✦","chat-spark");
        Label subtitle=Ui.label(gemini.configurationProblem().isBlank()?"AI BUILD HELPER":"AI OFFLINE · CATALOG SEARCH STILL WORKS","chat-subtitle");subtitle.setId("assistant-status");
        VBox title=new VBox(2,Ui.label("Forge Assistant","chat-title"),subtitle);
        Region gap=new Region();HBox.setHgrow(gap,Priority.ALWAYS);
        Button fresh=Ui.button("New chat",this::newChat);fresh.setId("assistant-new-chat");fresh.getStyleClass().add("chat-header-button");
        Button dismiss=Ui.button("×",close);dismiss.setId("assistant-close");dismiss.setAccessibleText("Close assistant");dismiss.getStyleClass().add("chat-header-button");
        HBox header=new HBox(9,spark,title,gap,fresh,dismiss);header.setAlignment(Pos.CENTER_LEFT);header.getStyleClass().add("chat-header");
        Button change=Ui.button("Change",()->{
            stop();
            memory=CatalogAssistant.Memory.empty();context.setText("Browsing the catalog");
            bot("Tell me a new category, product name or budget. Your earlier messages stay visible, but are no longer used as context.");
        });change.getStyleClass().add("chat-link");change.setId("assistant-change-context");
        HBox contextRow=new HBox(8,context,new Region(),change);HBox.setHgrow(contextRow.getChildren().get(1),Priority.ALWAYS);contextRow.setAlignment(Pos.CENTER_LEFT);contextRow.getStyleClass().add("chat-context");
        context.setWrapText(true);context.setMinWidth(0);
        setTop(new VBox(header,contextRow));
        messages.getStyleClass().add("chat-messages");messages.setMinWidth(0);scroll.setId("assistant-messages");setCenter(scroll);
        input.setId("assistant-question");input.setPromptText("Ask about products, builds or orders…");input.setMinWidth(0);
        input.setTextFormatter(new TextFormatter<String>(c->c.getControlNewText().length()<=1000?c:null));input.setOnAction(e->submit());
        send.setId("assistant-send");send.setAccessibleText("Send message");send.getStyleClass().add("chat-send");
        HBox composer=new HBox(8,input,send);HBox.setHgrow(input,Priority.ALWAYS);composer.setAlignment(Pos.CENTER_LEFT);composer.getStyleClass().add("chat-composer");
        Label disclaimer=Ui.label("AI can make mistakes. Check specs before you buy.","chat-disclaimer");disclaimer.setWrapText(true);
        Button privacy=Ui.button("Privacy",()->bot("Questions and relevant catalog/cart records go to Google when an AI explanation is needed. Free-tier content may improve Google's products. Don't enter sensitive information. Order summaries require separate approval and exclude addresses, usernames and payment methods. Chat history stays in memory until New chat or sign-out."));privacy.getStyleClass().add("chat-link");
        HBox footer=new HBox(4,disclaimer,privacy);HBox.setHgrow(disclaimer,Priority.ALWAYS);footer.setAlignment(Pos.CENTER_LEFT);footer.getStyleClass().add("chat-disclaimer-row");
        setBottom(new VBox(composer,footer));
        newChat();
    }
    public void refreshContext() {
        Product p=page.get();
        context.setText(p!=null?"Viewing · "+p.getName():"Browsing the catalog · Cart: "+cart.get().getItems().size()+" products");
    }
    private void newChat() {
        stop();memory=CatalogAssistant.Memory.empty();messages.getChildren().clear();lastFailed=null;input.clear();refreshContext();
        Label spark=Ui.label("✦","chat-welcome-spark");
        VBox welcome=new VBox(12,spark,wrap("What are we forging?","chat-welcome-title"),
            wrap("Tell me what you want to play or build. I'll find listed products and explain what to check.","chat-muted"));
        welcome.setId("assistant-welcome");welcome.getStyleClass().add("chat-welcome");
        for(String text:List.of("Show gaming laptops under $1,000","Review my cart","Compare these products","Where is my order?")) {
            Button prompt=Ui.button(text+"  →",()->{input.setText(text);submit();});prompt.setMaxWidth(Double.MAX_VALUE);prompt.setWrapText(true);prompt.getStyleClass().add("chat-prompt");welcome.getChildren().add(prompt);
        }
        messages.getChildren().add(welcome);
    }
    private void submit() { submit(input.getText().trim(),false,false); }
    private void submit(String question, boolean orderApproved, boolean retry) {
        if(busy||question.isBlank())return;
        if(CatalogAssistant.asksOrders(question)&&user instanceof Customer&&!orderApproved) {
            VBox notice=bot("May I let FORGE look up your own recent order records? If your question needs a Gemini explanation, I'll ask separately before sending a summary to Google.");
            HBox choices=new HBox(8);Button allow=Ui.button("View my orders",()->{choices.getChildren().clear();choices.getChildren().add(Ui.label("Approved · checking your order records","chat-muted"));submit(question,true,retry);});allow.getStyleClass().add("chat-orange");
            Button cancel=Ui.button("Not now",()->{choices.getChildren().clear();choices.getChildren().add(Ui.label("No order records were accessed.","chat-muted"));});choices.getChildren().addAll(allow,cancel);notice.getChildren().add(choices);return;
        }
        input.clear();if(!retry)userBubble(question);lastFailed=null;
        List<CartItem> snapshot=cart.get().getItems();Product currentPage=page.get();var previous=memory;
        int ticket=++generation;setBusy(true);showThinking();
        Task<CatalogAssistant.Plan> task=new Task<>() {@Override protected CatalogAssistant.Plan call() {
            return retrieval.prepare(user,snapshot,currentPage,previous,question,orderApproved);
        }};
        pending=task;
        task.setOnSucceeded(e->{if(ticket!=generation)return;finish();
            CatalogAssistant.Plan plan=task.getValue();
            if(plan.localAnswer()!=null) {renderAnswer(plan,previous,question,plan.localAnswer());return;}
            if(!consent) {awaitingConsent=plan;awaitingQuestion=question;showAiConsent(question,orderApproved,previous);return;}
            startGemini(plan,question,previous);
        });
        task.setOnFailed(e->{if(ticket!=generation)return;finish();lastFailed=question;
            VBox error=bot(friendly(task.getException()));error.setId("assistant-error");error.getStyleClass().add("chat-error");
            error.getChildren().add(wrap("Your message is saved in this chat. Browsing and checkout still work.","chat-muted"));
            Button again=Ui.button("Try again",()->{error.setDisable(true);submit(question,orderApproved,true);});again.getStyleClass().add("chat-dark-button");error.getChildren().add(again);
        });
        start(task);
    }
    private void showAiConsent(String question, boolean orderApproved, CatalogAssistant.Memory previous) {
        VBox notice=bot("To answer this, I need to send your question and the relevant product, cart or approved order records to Google Gemini. Free-tier content may be used to improve Google's products. Don't include sensitive information.");
        HBox choices=new HBox(8);
        Button allow=Ui.button("Continue to Gemini",()->{
            CatalogAssistant.Plan plan=awaitingConsent;String pendingQuestion=awaitingQuestion;
            awaitingConsent=null;awaitingQuestion=null;consent=true;
            choices.getChildren().clear();choices.getChildren().add(Ui.label("Approved · this request is being sent to Gemini.","chat-muted"));
            if(plan!=null)startGemini(plan,pendingQuestion,previous);
        });allow.setId("assistant-consent-continue");allow.getStyleClass().add("chat-orange");
        Button cancel=Ui.button("Keep it here",()->{awaitingConsent=null;awaitingQuestion=null;choices.getChildren().clear();choices.getChildren().add(Ui.label("Not sent · no information was shared with Google.","chat-muted"));});cancel.getStyleClass().add("chat-prompt");
        choices.getChildren().addAll(allow,cancel);notice.getChildren().add(choices);
    }
    private void startGemini(CatalogAssistant.Plan plan, String question, CatalogAssistant.Memory previous) {
        int ticket=++generation;setBusy(true);showThinking();
        Task<String> task=new Task<>() {@Override protected String call(){return gemini.answer(question,plan.facts());}};pending=task;
        task.setOnSucceeded(e->{if(ticket!=generation)return;finish();renderAnswer(plan,previous,question,task.getValue());});
        task.setOnFailed(e->{if(ticket!=generation)return;finish();lastFailed=question;
            VBox error=bot(friendly(task.getException()));error.setId("assistant-error");error.getStyleClass().add("chat-error");
            error.getChildren().add(wrap("Your message is saved in this chat. Browsing and checkout still work.","chat-muted"));
            if(!isSetupFailure(task.getException())) {
                Button again=Ui.button("Try again",()->{submit(question,plan.orderContext(),true);});again.getStyleClass().add("chat-dark-button");error.getChildren().add(again);
            }
        });start(task);
    }
    private void renderAnswer(CatalogAssistant.Plan plan, CatalogAssistant.Memory previous, String question, String text) {
        memory=CatalogAssistant.remember(previous,plan,question,text);context.setText(plan.context());VBox bubble=bot(text);
        for(Product p:plan.products())bubble.getChildren().add(productCard(p));
        if(plan.products().size()>1) {
            List<Product> available=plan.products().stream().filter(p->p.getStock()>0).toList();
            if(!available.isEmpty()) {Button all=Ui.button("Review adding these "+available.size()+" items",()->confirmAdd(available));all.setWrapText(true);all.getStyleClass().add("chat-orange");bubble.getChildren().add(all);}
        }
        if(!plan.products().isEmpty()) {
            Button cheaper=Ui.button("Show cheaper alternatives",()->submit("Show cheaper alternatives",false,false));cheaper.getStyleClass().add("chat-prompt");bubble.getChildren().add(cheaper);
        }
        scrollDown();
    }
    private Node productCard(Product p) {
        ProductArtwork photo=new ProductArtwork(p,54,true);photo.setMinSize(54,54);photo.setPrefSize(54,54);photo.setMaxSize(54,54);
        Label name=wrap(p.getName(),"chat-product-name");Label price=Ui.label(Money.format(p.getPrice()),"chat-price");
        Label stock=Ui.label(p.getStock()==0?"Out of stock":p.getStock()<=3?"Only "+p.getStock()+" left":"In stock",p.getStock()==0?"chat-stock-out":p.getStock()<=3?"chat-stock-low":"chat-stock");
        Button view=Ui.button("View product",()->viewProduct.accept(p));view.getStyleClass().add("chat-link");
        Button add=Ui.button("+ Add",()->confirmAdd(List.of(p)));add.setDisable(p.getStock()==0);add.getStyleClass().add("chat-dark-button");
        HBox actions=new HBox(6,view,add);actions.setAlignment(Pos.CENTER_LEFT);
        VBox details=new VBox(4,name,price,stock,actions);details.setMinWidth(0);HBox.setHgrow(details,Priority.ALWAYS);
        HBox card=new HBox(10,photo,details);card.setAlignment(Pos.TOP_LEFT);card.getStyleClass().add("chat-product-card");card.setId("assistant-product-"+p.getId());return card;
    }
    private void confirmAdd(List<Product> products) {
        if(busy)return;
        VBox card=bot("Add "+products.size()+" item"+(products.size()==1?"":"s")+" to your cart? One of each:");
        for(Product p:products)card.getChildren().add(wrap(p.getName()+" · "+Money.format(p.getPrice()),"chat-product-name"));
        BigDecimal total=products.stream().map(Product::getPrice).reduce(BigDecimal.ZERO,BigDecimal::add);
        card.getChildren().add(Ui.label("Subtotal "+Money.format(total),"chat-price"));
        Button confirm=Ui.button("Add to cart",()->{
            if(busy)return;int ticket=++generation;setBusy(true);card.setDisable(true);
            Task<List<Product>> task=new Task<>() {@Override protected List<Product> call(){return AssistantCart.validate(products,services.products());}};pending=task;
            task.setOnSucceeded(e->{if(ticket!=generation)return;finish();try{AssistantCart.apply(user,cart.get(),task.getValue());cartChanged.run();bot("Added to your cart. You can review quantities before checkout.");refreshContext();}catch(RuntimeException failure){bot(friendly(failure));}});
            task.setOnFailed(e->{if(ticket!=generation)return;finish();bot(friendly(task.getException()));});start(task);
        });confirm.getStyleClass().add("chat-orange");confirm.setId("assistant-confirm-add");
        Button cancel=Ui.button("Not now",()->{card.setDisable(true);bot("Nothing was added.");});cancel.getStyleClass().add("chat-prompt");
        card.getChildren().addAll(confirm,cancel,wrap("Nothing is added until you confirm.","chat-muted"));card.setId("assistant-confirmation");scrollDown();
    }
    public void stop() {
        generation++;if(pending!=null)pending.cancel(true);pending=null;
        awaitingConsent=null;awaitingQuestion=null;
        boolean wasBusy=busy;finish();if(wasBusy)bot("Stopped. You can ask another question.");
    }
    private void finish(){setBusy(false);if(dots!=null){dots.stop();dots=null;}if(thinking!=null){messages.getChildren().remove(thinking);thinking=null;}}
    private void setBusy(boolean value){busy=value;input.setDisable(value);send.setDisable(value);if(!value)input.requestFocus();}
    private void showThinking(){
        Label status=wrap("Checking stock and prices…","chat-muted");Label animation=Ui.label("●  ●  ●","chat-muted");
        Button stop=Ui.button("Stop",this::stop);stop.setId("assistant-stop");stop.getStyleClass().add("chat-prompt");
        VBox box=new VBox(8,animation,status,stop);box.getStyleClass().add("chat-bubble");box.setId("assistant-thinking");thinking=box;messages.getChildren().add(box);
        if(!Motion.isReduced()){dots=new Timeline(new KeyFrame(Duration.ZERO,new KeyValue(animation.opacityProperty(),.35)),new KeyFrame(Duration.millis(650),new KeyValue(animation.opacityProperty(),1)));dots.setAutoReverse(true);dots.setCycleCount(Animation.INDEFINITE);dots.play();}scrollDown();
    }
    private VBox bot(String text){VBox bubble=new VBox(10,wrap(text,"chat-message-text"));bubble.getStyleClass().add("chat-bubble");messages.getChildren().add(bubble);trim();scrollDown();return bubble;}
    private void userBubble(String text){Label label=wrap(text,"chat-user-text");HBox row=new HBox(label);row.setAlignment(Pos.CENTER_RIGHT);messages.getChildren().add(row);trim();scrollDown();}
    private void trim(){while(messages.getChildren().size()>32)messages.getChildren().removeFirst();}
    private static Label wrap(String text,String style){Label label=Ui.label(text,style);label.setWrapText(true);label.setMinWidth(0);label.setMaxWidth(Double.MAX_VALUE);return label;}
    private void scrollDown(){javafx.application.Platform.runLater(()->{messages.applyCss();messages.layout();scroll.setVvalue(1);});}
    private static void start(Task<?> task){Thread thread=new Thread(task,"forge-chat");thread.setDaemon(true);thread.start();}
    private static String friendly(Throwable error){
        String msg=error.getMessage()==null?"":error.getMessage();
        if(msg.contains("GEMINI_API_KEY")||msg.contains("FORGE_AI")||msg.contains("rejected access"))return "Gemini isn't configured for this launch. Restart with the FORGE launcher and enter your API key when prompted. Product listings by category and budget still work locally.";
        if(msg.contains("quota")||msg.contains("rate limit"))return "The assistant has reached its usage limit. Please try again later. You can still browse and search the catalog.";
        if(error instanceof IllegalArgumentException||error instanceof IllegalStateException||error instanceof edu.course.shop.exception.ValidationException)return msg;
        return "I couldn't reach the assistant. Please try again.";
    }
    private static boolean isSetupFailure(Throwable error){String msg=error.getMessage()==null?"":error.getMessage();return msg.contains("GEMINI_API_KEY")||msg.contains("FORGE_AI")||msg.contains("rejected access");}
}
