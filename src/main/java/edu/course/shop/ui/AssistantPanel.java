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
    private final SavedItems savedItems;
    private final AssistantPreferences preferenceStore=new AssistantPreferences();
    private AssistantPreferences.Profile savedPreferences;
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
    private final RobotAvatar robot=new RobotAvatar(44);
    private Timeline dots;
    private int generation;
    private boolean consent, busy;
    private String lastFailed;

    public AssistantPanel(Services services, User user, Supplier<Cart> cart, Supplier<Product> page,
                          GeminiAssistant gemini, Consumer<Product> viewProduct, Runnable cartChanged,
                          SavedItems savedItems, Runnable close) {
        this.services=services;this.user=user;this.cart=cart;this.page=page;this.gemini=gemini;
        this.savedPreferences=preferenceStore.load(user.getId());this.consent=preferenceStore.hasGeminiConsent(user.getId());
        this.retrieval=new CatalogAssistant(services);this.viewProduct=viewProduct;this.cartChanged=cartChanged;this.savedItems=savedItems;
        setId("assistant-panel");getStyleClass().add("forge-chat");setMinSize(0,0);setPrefWidth(420);setMaxWidth(420);
        robot.setId("assistant-robot");robot.setSleepAfter(Duration.seconds(45));robot.watchInput(input,()->busy);
        Label subtitle=Ui.label(gemini.configurationProblem().isBlank()?"AI BUILD HELPER":"AI OFFLINE · CATALOG SEARCH STILL WORKS","chat-subtitle");subtitle.setId("assistant-status");
        VBox title=new VBox(2,Ui.label("FORGE AI","chat-title"),subtitle);
        Region gap=new Region();HBox.setHgrow(gap,Priority.ALWAYS);
        Button fresh=Ui.button("New chat",this::newChat);fresh.setId("assistant-new-chat");fresh.getStyleClass().add("chat-header-button");
        Button dismiss=Ui.button("×",close);dismiss.setId("assistant-close");dismiss.setAccessibleText("Close assistant");dismiss.getStyleClass().add("chat-header-button");
        HBox header=new HBox(9,robot,title,gap,fresh,dismiss);header.setAlignment(Pos.CENTER_LEFT);header.getStyleClass().add("chat-header");
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
        Button privacy=Ui.button("Privacy",this::showPrivacy);privacy.getStyleClass().add("chat-link");
        Button limits=Ui.button("AI limits",this::showLimits);limits.setId("assistant-limits");limits.getStyleClass().add("chat-link");
        HBox footer=new HBox(4,disclaimer,limits,privacy);HBox.setHgrow(disclaimer,Priority.ALWAYS);footer.setAlignment(Pos.CENTER_LEFT);footer.getStyleClass().add("chat-disclaimer-row");
        setBottom(new VBox(composer,footer));
        newChat();
    }
    public void greet(){if(!busy)robot.react(RobotAvatar.State.HAPPY);}
    public void refreshContext() {
        Product p=page.get();
        context.setText(p!=null?"Viewing · "+p.getName():"Browsing the catalog · Cart: "+cart.get().getItems().size()+" products"+preferenceSuffix());
    }
    private String preferenceSuffix(){
        if(savedPreferences==null||savedPreferences.empty())return "";
        List<String> parts=new ArrayList<>();if(savedPreferences.category()!=null)parts.add(savedPreferences.category().label());
        if(savedPreferences.budget()!=null)parts.add("up to "+Money.format(savedPreferences.budget()));
        return " · Saved preference: "+String.join(" / ",parts);
    }
    private static List<String> preferenceLabels(AssistantPreferences.Profile profile){
        List<String> parts=new ArrayList<>();if(profile.category()!=null)parts.add(profile.category().label());
        if(profile.budget()!=null)parts.add("budget up to "+Money.format(profile.budget()));return parts;
    }
    private String personalize(String question){
        if(savedPreferences==null||savedPreferences.empty())return question;
        String q=question.toLowerCase(Locale.ROOT);
        boolean recommendation=q.matches("(?s).*(\\brecommend\\b|\\bsuggest\\b|what should i buy|help me choose|show me options|find me (?:something|products?)|looking for (?:something|products?)).*");
        return recommendation?AssistantPreferences.apply(question,savedPreferences):question;
    }
    private void newChat() {
        stop();gemini.clearCaches();memory=CatalogAssistant.Memory.empty();messages.getChildren().clear();lastFailed=null;input.clear();refreshContext();
        RobotAvatar spark=new RobotAvatar(68);spark.setAnimated(false);spark.setId("assistant-welcome-robot");
        VBox welcome=new VBox(12,spark,wrap("What are we forging?","chat-welcome-title"),
            wrap("Tell me what you want to play or build. I'll find listed products and explain what to check.","chat-muted"));
        welcome.setId("assistant-welcome");welcome.getStyleClass().add("chat-welcome");
        welcome.getChildren().add(wrap("Save a shopping preference by saying ‘Remember I prefer laptops under $1,500’. Say ‘forget my preferences’ to clear it.","chat-muted"));
        welcome.getChildren().add(wrap("You can also ask ‘remove [product] from my cart’ or ‘change quantity of [product] to 2’. FORGE asks before changing anything.","chat-muted"));
        for(String text:List.of("Show gaming laptops under $1,000","Review my cart","Compare these products","Where is my order?")) {
            Button prompt=Ui.button(text+"  →",()->{input.setText(text);submit();});prompt.setMaxWidth(Double.MAX_VALUE);prompt.setWrapText(true);prompt.getStyleClass().add("chat-prompt");welcome.getChildren().add(prompt);
        }
        messages.getChildren().add(welcome);
    }
    private void showLimits(){VBox info=bot("FORGE AI usage limits");info.setId("assistant-limits-info");info.getChildren().add(wrap(gemini.limitsSummary(),"chat-muted"));scrollDown();}
    private void showPrivacy(){
        VBox notice=bot("Questions and relevant catalog/cart records go to Google when an AI explanation is needed. Image search also sends your selected image and FORGE product photos, names and specifications to Gemini after you approve. Free-tier content may improve Google's products. Don't enter sensitive information. Order summaries require separate approval and exclude addresses, usernames and payment methods. Chat history stays in memory until New chat or sign-out. Shopping preferences and approvals are stored locally on this device.");
        if(consent){Button revoke=Ui.button("Forget Gemini approval",()->{});
            revoke.setOnAction(event->{
            try{preferenceStore.forgetGeminiConsent(user.getId());consent=false;notice.getChildren().add(Ui.label("Gemini approval cleared. FORGE will ask again before the next request sent to Google.","chat-muted"));revoke.setDisable(true);}
            catch(RuntimeException error){notice.getChildren().add(Ui.label(friendly(error),"chat-error"));}
            });revoke.getStyleClass().add("chat-prompt");notice.getChildren().add(revoke);}
        if(preferenceStore.hasGeminiImageConsent(user.getId())){Button revokeImages=Ui.button("Forget image search approval",()->{});
            revokeImages.setOnAction(event->{
                try{preferenceStore.forgetGeminiImageConsent(user.getId());notice.getChildren().add(Ui.label("Image search approval cleared. FORGE will ask again before sending photos to Google.","chat-muted"));revokeImages.setDisable(true);}
                catch(RuntimeException error){notice.getChildren().add(Ui.label(friendly(error),"chat-error"));}
            });revokeImages.getStyleClass().add("chat-prompt");notice.getChildren().add(revokeImages);}
    }
    private void submit() { submit(input.getText().trim(),false,false); }
    private void submit(String question, boolean orderApproved, boolean retry) {
        if(busy||question.isBlank())return;
        Optional<AssistantCart.Edit> edit=AssistantCart.prepareEdit(question,cart.get().getItems());
        if(edit.isPresent()){confirmCartEdit(edit.get());return;}
        if(question.toLowerCase(Locale.ROOT).matches(".*\\bforget my preferences\\b.*")) {
            try{preferenceStore.clear(user.getId());savedPreferences=preferenceStore.load(user.getId());refreshContext();bot("Your saved shopping preferences were cleared from this device.");}
            catch(RuntimeException error){bot(friendly(error));}return;
        }
        try {
            AssistantPreferences.Profile saved=preferenceStore.parseAndSave(user.getId(),question);
            if(saved!=null){savedPreferences=saved;refreshContext();bot("Saved on this device: "+String.join(" / ",preferenceLabels(saved))+". Say ‘forget my preferences’ to clear it.");return;}
        } catch(RuntimeException error){bot(friendly(error));return;}
        String effectiveQuestion=personalize(question);
        if(CatalogAssistant.asksOrders(question)&&user instanceof Customer&&!orderApproved) {
            VBox notice=bot("May I let FORGE look up your own recent order records? If your question needs a Gemini explanation, I'll ask separately before sending a summary to Google.");
            HBox choices=new HBox(8);Button allow=Ui.button("View my orders",()->{choices.getChildren().clear();choices.getChildren().add(Ui.label("Approved · checking your order records","chat-muted"));submit(question,true,retry);});allow.getStyleClass().add("chat-orange");
            Button cancel=Ui.button("Not now",()->{choices.getChildren().clear();choices.getChildren().add(Ui.label("No order records were accessed.","chat-muted"));});choices.getChildren().addAll(allow,cancel);notice.getChildren().add(choices);return;
        }
        input.clear();if(!retry)userBubble(question);lastFailed=null;
        List<CartItem> snapshot=cart.get().getItems();Product currentPage=page.get();var previous=memory;
        int ticket=++generation;setBusy(true);showThinking("Checking FORGE records…");robot.setState(RobotAvatar.State.SEARCHING);
        Task<CatalogAssistant.Plan> task=new Task<>() {@Override protected CatalogAssistant.Plan call() {
            return retrieval.prepare(user,snapshot,currentPage,previous,effectiveQuestion,orderApproved);
        }};
        pending=task;
        task.setOnSucceeded(e->{if(ticket!=generation)return;finish();
            CatalogAssistant.Plan plan=task.getValue();
            if(plan.localAnswer()!=null) {renderAnswer(plan,previous,effectiveQuestion,plan.localAnswer());return;}
            if(!consent) {showAiConsent(plan,effectiveQuestion,previous);return;}
            startGemini(plan,effectiveQuestion,previous);
        });
        task.setOnFailed(e->{if(ticket!=generation)return;finish();robot.react(RobotAvatar.State.ERROR);lastFailed=question;
            VBox error=bot(friendly(task.getException()));error.setId("assistant-error");error.getStyleClass().add("chat-error");
            error.getChildren().add(wrap("Your message is saved in this chat. Browsing and checkout still work.","chat-muted"));
            Button again=Ui.button("Try again",()->{error.setDisable(true);submit(question,orderApproved,true);});again.getStyleClass().add("chat-dark-button");error.getChildren().add(again);
        });
        start(task);
    }
    private void confirmCartEdit(AssistantCart.Edit edit) {
        VBox card=bot(edit.removes()?"Remove "+edit.productName()+" from your cart?":"Change "+edit.productName()+" to quantity "+edit.quantity()+"?");
        Button apply=Ui.button(edit.removes()?"Confirm remove":"Confirm quantity",()->{
            if(busy)return;int ticket=++generation;card.setDisable(true);setBusy(true);
            Task<Boolean> task=new Task<>() {@Override protected Boolean call(){AssistantCart.applyEdit(user,cart.get(),edit,services.products());return true;}};
            pending=task;
            task.setOnSucceeded(e->{if(ticket!=generation)return;finish();robot.react(RobotAvatar.State.HAPPY);cartChanged.run();refreshContext();bot(edit.removes()?edit.productName()+" was removed from your cart.":"Updated "+edit.productName()+" to quantity "+edit.quantity()+".");});
            task.setOnFailed(e->{if(ticket!=generation)return;finish();robot.react(RobotAvatar.State.ERROR);bot(friendly(task.getException()));});
            start(task);
        });
        Button cancel=Ui.button("Keep my cart",()->{card.setDisable(true);bot("No cart changes were made.");});
        apply.getStyleClass().add("chat-orange");cancel.getStyleClass().add("chat-prompt");card.getChildren().addAll(apply,cancel,wrap("Nothing changes until you confirm.","chat-muted"));
    }
    private void showAiConsent(CatalogAssistant.Plan plan, String question, CatalogAssistant.Memory previous) {
        int ticket=generation;
        VBox notice=bot("To answer this, I need to send your question and the relevant product, cart or approved order records to Google Gemini. Free-tier content may be used to improve Google's products. Don't include sensitive information.");
        HBox choices=new HBox(8);
        Button allow=Ui.button("Continue with FORGE AI",()->{
            if(ticket!=generation||busy)return;
            try{preferenceStore.rememberGeminiConsent(user.getId());}
            catch(RuntimeException error){choices.getChildren().clear();choices.getChildren().add(Ui.label(friendly(error),"chat-error"));return;}
            consent=true;
            choices.getChildren().clear();choices.getChildren().add(Ui.label("Approved · this request is being sent to Gemini.","chat-muted"));
            startGemini(plan,question,previous);
        });allow.setId("assistant-consent-continue");allow.getStyleClass().add("chat-orange");
        Button cancel=Ui.button("Keep it here",()->{if(ticket!=generation||busy)return;choices.getChildren().clear();choices.getChildren().add(Ui.label("Not sent · no information was shared with Google.","chat-muted"));});cancel.getStyleClass().add("chat-prompt");
        choices.getChildren().addAll(allow,cancel);notice.getChildren().add(choices);
    }
    private void startGemini(CatalogAssistant.Plan plan, String question, CatalogAssistant.Memory previous) {
        int ticket=++generation;setBusy(true);showThinking("Preparing your answer…");robot.setState(RobotAvatar.State.THINKING);
        Task<String> task=new Task<>() {@Override protected String call(){return gemini.answer(question,plan.facts());}};pending=task;
        task.setOnSucceeded(e->{if(ticket!=generation)return;finish();renderAnswer(plan,previous,question,task.getValue());});
        task.setOnFailed(e->{if(ticket!=generation)return;finish();robot.react(RobotAvatar.State.ERROR);lastFailed=question;
            VBox error=bot(friendly(task.getException()));error.setId("assistant-error");error.getStyleClass().add("chat-error");
            error.getChildren().add(wrap("Your message is saved in this chat. Browsing and checkout still work.","chat-muted"));
            if(!isSetupFailure(task.getException())) {
                Button again=Ui.button("Try again",()->{submit(question,plan.orderContext(),true);});again.getStyleClass().add("chat-dark-button");error.getChildren().add(again);
            }
        });start(task);
    }
    private void renderAnswer(CatalogAssistant.Plan plan, CatalogAssistant.Memory previous, String question, String text) {
        robot.replyPresented();memory=CatalogAssistant.remember(previous,plan,question,text);context.setText(plan.context());VBox bubble=bot(text);
        appendProductCards(bubble,plan);
        scrollDown();
    }
    private void appendProductCards(VBox bubble,CatalogAssistant.Plan plan) {
        int firstPage=Math.min(6,plan.products().size());
        boolean recommendation=plan.ceiling()!=null;
        for(int i=0;i<firstPage;i++)bubble.getChildren().add(productCard(plan.products().get(i),recommendation&&i==0,plan));
        if(plan.products().size()>1) {
            List<Product> available=plan.products().subList(0,firstPage).stream().filter(p->p.getStock()>0).toList();
            if(!available.isEmpty()) {Button all=Ui.button("Review adding these "+available.size()+" items",()->confirmAdd(available));all.setWrapText(true);all.getStyleClass().add("chat-orange");bubble.getChildren().add(all);}
        }
        if(!plan.products().isEmpty()) {
            Button cheaper=Ui.button("Show cheaper alternatives",()->submit("Show cheaper alternatives",false,false));cheaper.getStyleClass().add("chat-prompt");bubble.getChildren().add(cheaper);
        }
        if(plan.products().size()>firstPage)addLoadMore(bubble,plan,firstPage);
    }
    private void addLoadMore(VBox bubble,CatalogAssistant.Plan plan,int offset) {
        int nextCount=Math.min(6,plan.products().size()-offset);
        Button more=Ui.button("Load more products · "+(plan.products().size()-offset)+" remaining",()->{});
        // Replace the action with the next page, then leave a fresh action if more results remain.
        more.setOnAction(e->{bubble.getChildren().remove(more);int end=Math.min(offset+6,plan.products().size());
            for(int i=offset;i<end;i++)bubble.getChildren().add(productCard(plan.products().get(i),false,plan));
            if(end<plan.products().size())addLoadMore(bubble,plan,end);scrollDown();});
        more.setWrapText(true);more.getStyleClass().add("chat-prompt");more.setAccessibleText("Load the next "+nextCount+" matching products");
        bubble.getChildren().add(more);
    }
    private Node productCard(Product p,boolean bestMatch,CatalogAssistant.Plan plan) {
        ProductArtwork photo=new ProductArtwork(p,76,true);
        // ProductArtwork binds its square height properties to its width; set only width here.
        photo.setMinWidth(76);photo.setPrefWidth(76);photo.setMaxWidth(76);
        Label category=Ui.label(p.getCategory().label().toUpperCase(Locale.ROOT),"chat-product-category");
        Label name=wrap(p.getName(),"chat-product-name");Label price=Ui.label(Money.format(p.getPrice()),"chat-price");
        Label stock=Ui.label(p.getStock()==0?"Out of stock":p.getStock()<=3?"Only "+p.getStock()+" left":"In stock",p.getStock()==0?"chat-stock-out":p.getStock()<=3?"chat-stock-low":"chat-stock");
        HBox priceRow=new HBox(8,price,stock);priceRow.setAlignment(Pos.CENTER_LEFT);priceRow.setMinWidth(0);
        VBox details=new VBox(4,category,name,priceRow);details.setMinWidth(0);HBox.setHgrow(details,Priority.ALWAYS);
        HBox summary=new HBox(12,photo,details);summary.setAlignment(Pos.CENTER_LEFT);
        String note=productNote(p,plan);
        Button view=Ui.button("View",()->viewProduct.accept(p));view.getStyleClass().add("chat-link");
        Button heart=savedItems.heart(p);heart.getStyleClass().add("chat-product-heart");
        Button add=Ui.button("+ Add",()->confirmAdd(List.of(p)));add.setDisable(p.getStock()==0);add.getStyleClass().add("chat-dark-button");
        Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        HBox actions=new HBox(8,view,spacer,heart,add);actions.setAlignment(Pos.CENTER_LEFT);
        VBox card=new VBox(9);
        if(bestMatch){Label badge=Ui.label("BEST MATCH","chat-best-match");badge.setMaxWidth(Double.MAX_VALUE);card.getChildren().add(badge);}
        card.getChildren().add(summary);
        if(!note.isBlank())card.getChildren().add(wrap(note,"chat-product-note"));
        Node evidence=productEvidence(p);
        if(evidence!=null)card.getChildren().add(evidence);
        card.getChildren().add(actions);
        card.getStyleClass().add("chat-product-card");card.setId("assistant-product-"+p.getId());return card;
    }
    private static String productNote(Product p,CatalogAssistant.Plan plan) {
        if(p.getStock()==0)return "Ask me for similar products that are available.";
        if(plan.ceiling()!=null)return "Within your "+Money.format(plan.ceiling())+" budget.";
        if(p.getStock()==1)return "Only one left; stock can change.";
        return "";
    }
    private static Node productEvidence(Product p) {
        String specs=p.getSpecifications().lines().filter(line->!line.isBlank()).limit(2).reduce((a,b)->a+" · "+b).orElse("");
        if(specs.isBlank())return null;
        return wrap("Key specifications: "+specs,"chat-evidence");
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
            task.setOnSucceeded(e->{if(ticket!=generation)return;finish();try{AssistantCart.apply(user,cart.get(),task.getValue());cartChanged.run();robot.react(RobotAvatar.State.HAPPY);bot("Added to your cart. You can review quantities before checkout.");refreshContext();}catch(RuntimeException failure){robot.react(RobotAvatar.State.ERROR);bot(friendly(failure));}});
            task.setOnFailed(e->{if(ticket!=generation)return;finish();robot.react(RobotAvatar.State.ERROR);bot(friendly(task.getException()));});start(task);
        });confirm.getStyleClass().add("chat-orange");confirm.setId("assistant-confirm-add");
        Button cancel=Ui.button("Not now",()->{card.setDisable(true);bot("Nothing was added.");});cancel.getStyleClass().add("chat-prompt");
        card.getChildren().addAll(confirm,cancel,wrap("Nothing is added until you confirm.","chat-muted"));card.setId("assistant-confirmation");scrollDown();
    }
    public void stop() {
        generation++;if(pending!=null)pending.cancel(true);pending=null;
        boolean wasBusy=busy;finish();if(wasBusy)bot("Stopped. You can ask another question.");
    }
    private void finish(){setBusy(false);if(dots!=null){dots.stop();dots=null;}if(thinking!=null){messages.getChildren().remove(thinking);thinking=null;}}
    private void setBusy(boolean value){busy=value;if(value)robot.setState(RobotAvatar.State.WORKING);else robot.returnToRest();input.setDisable(value);send.setDisable(value);if(!value)input.requestFocus();}
    private void showThinking(String message){
        Label status=wrap(message,"chat-muted");Label animation=Ui.label("●  ●  ●","chat-muted");
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
    static String friendly(Throwable error){
        if(error instanceof AiFailure failure)return failure.getMessage().replaceFirst("^HTTP \\d+: ","");
        String msg=error.getMessage()==null?"":error.getMessage();
        if(msg.contains("GEMINI_API_KEY")||msg.contains("FORGE_AI")||msg.contains("rejected access"))return "FORGE AI is unavailable right now. You can still browse products and use your cart.";
        if(msg.contains("quota")||msg.contains("rate limit"))return "FORGE AI has reached its current usage limit. Please try again later. You can still browse and search the catalog.";
        if(msg.matches("(?s).*\\bHTTP\\s+5\\d\\d\\b.*"))return "FORGE AI is temporarily unavailable. Please try again in a moment.";
        if(msg.contains("could not be reached"))return "FORGE AI couldn't connect. Check your internet connection and try again.";
        if(msg.startsWith("Gemini"))return "FORGE AI couldn't complete that answer. Please try again.";
        if(error instanceof IllegalArgumentException||error instanceof IllegalStateException||error instanceof edu.course.shop.exception.ValidationException)return msg;
        return "FORGE AI couldn't complete that request. Please try again.";
    }
    private static boolean isSetupFailure(Throwable error){if(error instanceof AiFailure failure)return failure.kind()==AiFailure.Kind.ACCESS||failure.kind()==AiFailure.Kind.MODEL;String msg=error.getMessage()==null?"":error.getMessage();return msg.contains("GEMINI_API_KEY")||msg.contains("FORGE_AI")||msg.contains("rejected access");}
}
