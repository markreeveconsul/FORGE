package edu.course.shop.ui;

import edu.course.shop.Services;
import edu.course.shop.exception.ValidationException;
import edu.course.shop.model.*;
import edu.course.shop.service.*;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import java.util.*;

/** One full admin module. Background work never replaces or disables the entire window. */
public final class AiManagerView extends BorderPane {
    private final User actor;private final Services services;private final AiManagerService manager;
    private final TextField input=new TextField();
    private final Label badge=Ui.label("AI Active","manager-status"),feedback=Ui.feedback("ai-manager-feedback");
    private final VBox results=new VBox(16);
    private final FlowPane chips=new FlowPane(8,8),metrics=new FlowPane(12,12);
    private final CheckBox research=new CheckBox("Research missing specifications online");
    private final Button run=Ui.button("Run  ↗",this::run),cancel=Ui.button("Cancel",this::cancel);
    private final List<Review> reviews=new ArrayList<>();
    private final VBox activity=new VBox(10);
    private final Label activityStatus=Ui.label("○ IDLE","manager-terminal-state");
    private Task<?> pending,metricsTask;private Animation shimmer;private int generation,metricsGeneration;private boolean disposed,busy,applying;
    private record Review(ProductService.ManagerChange original,CheckBox included,TextArea description,TextArea specs) { }

    public AiManagerView(User actor,Services services,GeminiAssistant gemini){
        if(actor==null||!actor.canManageProducts())throw new ValidationException("Administrator access is required.");
        this.actor=actor;this.services=services;this.manager=new AiManagerService(services,gemini);
        setId("ai-manager-page");getStyleClass().add("ai-manager-page");setMinSize(0,0);
        Label emblem=Ui.label("✦","manager-emblem");emblem.setVisible(false);emblem.setManaged(false);
        VBox title=new VBox(6,Ui.label("AI NEURAL MANAGER","manager-title"),Ui.label("Command your store with FORGE AI. Review every proposed change.","manager-subtitle"));title.setMinWidth(0);
        Region gap=new Region();HBox.setHgrow(gap,Priority.ALWAYS);
        HBox header=new HBox(14,emblem,title,gap,badge);header.setAlignment(Pos.CENTER_LEFT);header.getStyleClass().add("manager-header");
        badge.setId("ai-manager-status");setMode(!manager.configured());if(manager.configured())badge.setText("● AI ready");
        metrics.widthProperty().addListener((o,a,b)->resizeMetrics());
        input.setPromptText("Ask anything about your store...");input.setId("ai-manager-command");input.setAccessibleText("Store command");input.setMinWidth(0);
        input.setTextFormatter(new TextFormatter<String>(c->c.getControlNewText().length()<=1000?c:null));input.setOnAction(e->run());
        run.setId("ai-manager-run");run.getStyleClass().add("manager-run");cancel.setId("ai-manager-cancel");cancel.setVisible(false);cancel.managedProperty().bind(cancel.visibleProperty());
        HBox composer=new HBox(10,input,run,cancel);composer.setAlignment(Pos.CENTER_LEFT);HBox.setHgrow(input,Priority.ALWAYS);
        research.setId("ai-manager-research");research.getStyleClass().add("manager-research");
        Hyperlink privacy=new Hyperlink("How it works");privacy.setOnAction(e->showPrivacy());
        HBox options=new HBox(10,research,new Region(),privacy);HBox.setHgrow(options.getChildren().get(1),Priority.ALWAYS);options.setAlignment(Pos.CENTER_LEFT);
        run.setText("Run Agent  ↗");
        VBox commandCard=new VBox(18,Ui.label("YOUR STORE. ONE COMMAND.","manager-command-eyebrow"),Ui.label("What action would you like FORGE AI to execute?","section-title"),composer,chips,options);commandCard.getStyleClass().add("manager-command-card");
        Region terminalGap=new Region();HBox.setHgrow(terminalGap,Priority.ALWAYS);
        HBox terminalHeader=new HBox(12,Ui.label("●  ●  ●","manager-terminal-dots"),Ui.label("FORGE AI ACTIVITY","manager-terminal-heading"),terminalGap,activityStatus);terminalHeader.setAlignment(Pos.CENTER_LEFT);terminalHeader.getStyleClass().add("manager-terminal-header");
        activity.getStyleClass().add("manager-terminal-lines");activity.setId("ai-manager-activity");
        VBox terminal=new VBox(terminalHeader,activity);terminal.getStyleClass().add("manager-terminal");log("Ready. Enter a store command to begin.");
        VBox content=new VBox(24,header,commandCard,terminal,feedback,results);content.setMinWidth(0);content.getStyleClass().add("manager-content");
        ScrollPane scroll=Ui.scroll(content);scroll.setId("ai-manager-scroll");scroll.getStyleClass().add("manager-scroll");setCenter(scroll);
        empty();Platform.runLater(this::loadMetrics);
    }
    private void setMode(boolean offline){badge.setText(offline?(manager.configured()?"● Local fallback":"● Offline mode"):"● AI Active");badge.getStyleClass().remove("manager-status-offline");if(offline)badge.getStyleClass().add("manager-status-offline");}
    private void loadMetrics(){
        if(disposed)return;
        int ticket=++metricsGeneration;
        Task<AiManagerService.Metrics> task=new Task<>(){@Override protected AiManagerService.Metrics call(){return manager.metrics(actor);}};metricsTask=task;
        task.setOnSucceeded(e->{if(disposed||ticket!=metricsGeneration)return;var m=task.getValue();
            metrics.getChildren().setAll(metric("PRODUCTS",m.products()),metric("NEED CONTENT",m.needContent()),metric("LOW STOCK",m.lowStock()),metric("INACTIVE WITH STOCK",m.inactiveWithStock()));
            resizeMetrics();
            chips.getChildren().setAll(chip("Fix "+m.needContent()+" Need Content","Fix all products missing content","fix"),chip("Show Low Stock","Show products running low on stock","stock"),chip("Sales Summary","Show this week's sales summary and restock attention","sales"),chip("Inactive with Stock","Find inactive products with stock","inactive"));
            chips.setDisable(busy);
        });task.setOnFailed(e->{if(!disposed&&ticket==metricsGeneration)Ui.failure(feedback,task.getException());});start(task);
    }
    private VBox metric(String label,int count){VBox tile=new VBox(7,Ui.label(Integer.toString(count),"manager-metric-number"),Ui.label(label,"manager-eyebrow"));tile.getStyleClass().add("manager-metric");tile.setMinWidth(120);tile.setPrefWidth(170);return tile;}
    private void resizeMetrics(){double width=metrics.getWidth();if(width<=0)return;int columns=width>=760?4:2;double tileWidth=Math.floor((width-(columns-1)*12)/columns);for(Node node:metrics.getChildren())((Region)node).setPrefWidth(tileWidth);}
    private Button chip(String title,String command,String id){Button chip=Ui.button(title,()->{input.setText(command);run();});chip.setId("ai-manager-chip-"+id);chip.getStyleClass().add("manager-chip");return chip;}
    private void empty(){
        results.getChildren().clear();
    }
    private void run(){
        if(busy||disposed)return;String command=input.getText().trim();if(command.isEmpty()){input.requestFocus();return;}
        feedback.setText("");reviews.clear();int ticket=++generation;setBusy(true);log("Command received. Interpreting your request and checking store records…");loading();boolean useResearch=research.isSelected();
        Task<AiManagerService.Result> task=new Task<>(){@Override protected AiManagerService.Result call(){return manager.prepare(actor,command,useResearch);}};pending=task;
        task.setOnSucceeded(e->{if(disposed||ticket!=generation)return;finish();render(task.getValue());});
        task.setOnFailed(e->{if(disposed||ticket!=generation)return;finish();failure(task.getException());});start(task);
    }
    public void submit(String command){if(disposed||busy)return;input.setText(command);run();}
    private void log(String text){Label line=Ui.label(java.time.LocalTime.now().withNano(0)+"  "+text,"manager-terminal-line");activity.getChildren().add(line);while(activity.getChildren().size()>6)activity.getChildren().removeFirst();}
    private void setBusy(boolean value){busy=value;activityStatus.setText(value?"● RUNNING":"○ IDLE");run.setDisable(value);input.setDisable(value);chips.setDisable(value);research.setDisable(value);cancel.setVisible(value&&!applying);}
    private void loading(){
        VBox skeleton=Ui.skeletonRows(3);Region sweep=new Region();sweep.getStyleClass().add("manager-shimmer-sweep");sweep.setMinWidth(150);sweep.setPrefWidth(150);sweep.setMaxWidth(150);sweep.setMouseTransparent(true);
        StackPane loading=new StackPane(skeleton,sweep);loading.setId("ai-manager-loading");StackPane.setAlignment(sweep,Pos.CENTER_LEFT);
        Rectangle clip=new Rectangle();clip.widthProperty().bind(loading.widthProperty());clip.heightProperty().bind(loading.heightProperty());loading.setClip(clip);
        results.getChildren().setAll(Ui.label(applying?"Applying your reviewed changes…":"Understanding your command and checking store records…","manager-result-title"),loading);
        if(!Motion.isReduced()){
            TranslateTransition motion=new TranslateTransition(Duration.millis(1400),sweep);motion.setFromX(-170);motion.setToX(Math.max(800,getWidth())+170);motion.setCycleCount(Animation.INDEFINITE);motion.play();shimmer=motion;
        }else sweep.setVisible(false);
    }
    private void finish(){if(shimmer!=null){shimmer.stop();shimmer=null;}applying=false;setBusy(false);pending=null;}
    private void render(AiManagerService.Result result){
        log(result.aiIssue().isBlank()?"Completed: "+result.products().size()+" products returned; "+result.changes().size()+" changes ready for review.":"AI request failed. Local results remain available; see the connection details below.");
        setMode(result.offline());reviews.clear();
        Label route=Ui.label(result.command().intent().name().replace('_',' '),"manager-eyebrow");
        VBox summary=new VBox(9,route,Ui.label(result.message(),"manager-result-title"));summary.getStyleClass().add("manager-result-card");
        FlowPane filters=new FlowPane(8,8);var c=result.command();
        if(!c.query().isBlank())filter(filters,c.query());if(c.category()!=null)filter(filters,c.category().label());
        if(c.belowPrice()!=null)filter(filters,"Under "+Money.format(c.belowPrice()));if(c.stockAtMost()!=null)filter(filters,"Stock ≤ "+c.stockAtMost());if(c.stockAtLeast()!=null)filter(filters,"Stock ≥ "+c.stockAtLeast());
        if(!c.status().equals("any"))filter(filters,"Currently "+c.status());if(!c.ids().isEmpty())filter(filters,"Product IDs: "+c.ids());if(c.missingContent())filter(filters,c.missingField().equals("any")?"Missing content":"Missing "+c.missingField());
        if(!filters.getChildren().isEmpty())summary.getChildren().add(filters);
        results.getChildren().setAll(summary);
        if(!result.aiIssue().isBlank()){
            Label explanation=Ui.label(result.aiIssue(),"manager-connection-detail");explanation.setWrapText(true);explanation.setId("ai-manager-connection-detail");
            Button retry=Ui.button("Retry command",this::run);retry.setId("ai-manager-retry");retry.getStyleClass().add("manager-chip");
            VBox notice=new VBox(10,Ui.label("AI request couldn't finish","manager-result-title"),explanation,retry);notice.getStyleClass().addAll("manager-result-card","manager-error");notice.setId("ai-manager-connection-issue");
            results.getChildren().add(0,notice);badge.setTooltip(new Tooltip(result.aiIssue()));
        }else badge.setTooltip(null);
        if(result.sales()!=null){
            var sales=result.sales();HBox values=new HBox(12,salesMetric("ORDER VALUE",Money.format(sales.orderValue())),salesMetric("PAID VALUE",Money.format(sales.paidValue())),salesMetric("ORDERS",Integer.toString(sales.orders())),salesMetric("UNITS",Integer.toString(sales.units())));
            VBox card=new VBox(12,Ui.label(sales.period(),"manager-result-title"),values);card.getStyleClass().add("manager-result-card");card.setId("ai-manager-sales");results.getChildren().add(card);
        }
        if(!result.products().isEmpty())results.getChildren().add(productTable(result.products(),result.totalMatches()));
        if(!result.changes().isEmpty()){
            VBox proposed=new VBox(10,Ui.label("Review proposed changes","manager-result-title"),Ui.label("Select the products to update. Changes are saved together only when you apply them.","muted"));proposed.getStyleClass().add("manager-result-card");
            Accordion accordion=new Accordion();accordion.setId("ai-manager-drafts");
            for(ProductService.ManagerChange change:result.changes()){
                CheckBox included=new CheckBox("Include this product");included.setSelected(true);
                VBox fields=new VBox(12,included);Product product=change.expected();
                TextArea desc=change.description()==null?null:editor(change.description(),"Description draft",fields);
                TextArea specs=change.specifications()==null?null:editor(change.specifications(),"Specifications draft",fields);
                if(change.active()!=null)fields.getChildren().add(Ui.label((product.isActive()?"Active":"Inactive")+"  →  "+(change.active()?"Active":"Inactive"),"manager-state-change"));
                result.drafts().stream().filter(d->d.id()==product.getId()).findFirst().ifPresent(d->{
                    if(!d.note().isBlank())fields.getChildren().add(Ui.label(d.note(),"muted"));
                    for(String source:d.sources()){Hyperlink link=new Hyperlink("Source · "+java.net.URI.create(source).getHost());link.setTooltip(new Tooltip(source));link.setOnAction(event->openSource(source));fields.getChildren().add(link);}
                });
                TitledPane pane=new TitledPane("#"+product.getId()+" · "+product.getName(),fields);pane.setAnimated(!Motion.isReduced());accordion.getPanes().add(pane);reviews.add(new Review(change,included,desc,specs));
            }
            if(!accordion.getPanes().isEmpty())accordion.setExpandedPane(accordion.getPanes().getFirst());
            Button apply=Ui.button("Apply reviewed changes ("+reviews.size()+")",()->apply(result));apply.setId("ai-manager-apply");apply.getStyleClass().add("manager-run");
            for(Review review:reviews)review.included().selectedProperty().addListener((o,a,b)->apply.setText("Apply reviewed changes ("+reviews.stream().filter(r->r.included().isSelected()).count()+")"));
            Button discard=Ui.button("Discard preview",()->{reviews.clear();empty();feedback.setText("Preview discarded. No changes were saved.");});discard.getStyleClass().add("manager-chip");
            proposed.getChildren().addAll(accordion,new HBox(10,apply,discard));results.getChildren().add(proposed);
        }else if(c.intent()==ManagerCommand.Intent.FIX_CONTENT&&!result.drafts().isEmpty()){
            results.getChildren().add(Ui.label("No verified new fields were found to apply. Add an exact model or review the product in Products.","muted"));
        }
    }
    private TextArea editor(String value,String title,VBox parent){TextArea field=new TextArea(value);field.setWrapText(true);field.setPrefRowCount(3);field.setTextFormatter(new TextFormatter<String>(c->c.getControlNewText().length()<=1000?c:null));parent.getChildren().addAll(Ui.label(title,"field-label"),field);return field;}
    private void filter(FlowPane row,String value){Label label=Ui.label(value,"manager-filter-chip");row.getChildren().add(label);}
    private VBox salesMetric(String label,String value){VBox box=new VBox(6,Ui.label(label,"manager-eyebrow"),Ui.label(value,"manager-metric-number"));box.setMinWidth(0);HBox.setHgrow(box,Priority.ALWAYS);return box;}
    private VBox productTable(List<Product> products,int total){
        TableView<Product> table=Ui.table();table.setId("ai-manager-products");
        table.getColumns().setAll(List.of(Ui.column("PRODUCT",p->"#"+p.getId()+" · "+p.getName()),Ui.column("CATEGORY",p->p.getCategory().label()),Ui.column("PRICE",p->Money.format(p.getPrice())),Ui.column("STOCK",p->Integer.toString(p.getStock())),Ui.column("STATUS",p->p.isActive()?"Active":"Inactive"),Ui.column("MISSING",p->{List<String> missing=new ArrayList<>();if(p.getDescription().isBlank())missing.add("Description");if(p.getSpecifications().isBlank())missing.add("Specs");if(p.getPhoto()==null)missing.add("Photo");return missing.isEmpty()?"Complete":String.join(", ",missing);} )));
        table.setItems(FXCollections.observableArrayList(products));table.setMinHeight(150);table.setPrefHeight(Math.min(340,48+products.size()*38));
        VBox card=new VBox(12,Ui.label(products.size()+(products.size()==1?" product shown":" products shown")+(total>products.size()?" · "+total+" total matches":""),"manager-result-title"),table);card.getStyleClass().add("manager-result-card");return card;
    }
    private void apply(AiManagerService.Result result){
        if(busy||disposed)return;
        List<ProductService.ManagerChange> selected=new ArrayList<>();
        for(Review review:reviews)if(review.included().isSelected())selected.add(new ProductService.ManagerChange(review.original().expected(),review.description()==null?null:review.description().getText().trim(),review.specs()==null?null:review.specs().getText().trim(),review.original().active(),review.original().sources()));
        if(selected.isEmpty()){feedback.setText("Select at least one product to apply.");return;}
        if(selected.stream().anyMatch(c->c.description()!=null&&c.description().isBlank()||c.specifications()!=null&&c.specifications().isBlank())){feedback.setText("Complete each included draft, or deselect that product before applying.");return;}
        var approved=new AiManagerService.Result(result.batchId(),result.command(),result.products(),selected,result.drafts(),result.sales(),result.offline(),result.message(),result.totalMatches(),result.aiIssue());
        applying=true;setBusy(true);loading();int ticket=++generation;
        Task<List<Product>> task=new Task<>(){@Override protected List<Product> call(){manager.apply(actor,approved);return selected.stream().map(c->services.products().get(c.expected().getId())).toList();}};pending=task;
        task.setOnSucceeded(e->{if(disposed||ticket!=generation)return;finish();reviews.clear();
            log("Saved "+selected.size()+" reviewed changes. Inventory refreshed.");VBox success=new VBox(8,Ui.label("✓ Changes applied","manager-result-title"),Ui.label("Updated "+selected.size()+" products. Inventory and content counts have been refreshed.","muted"));success.getStyleClass().addAll("manager-result-card","manager-success");success.setId("ai-manager-success");results.getChildren().setAll(success,productTable(task.getValue(),task.getValue().size()));loadMetrics();
        });task.setOnFailed(e->{if(disposed||ticket!=generation)return;finish();failure(task.getException());loadMetrics();});start(task);
    }
    private void failure(Throwable error){
        log("Command could not finish. No unreviewed changes were applied.");
        VBox card=new VBox(10,Ui.label("We couldn't complete that command","manager-result-title"),Ui.label(AssistantPanel.friendly(error),"muted"));card.setId("ai-manager-error");card.getStyleClass().addAll("manager-result-card","manager-error");
        Button retry=Ui.button("Try again",this::run);retry.getStyleClass().add("manager-chip");card.getChildren().add(retry);results.getChildren().setAll(card);
    }
    private void cancel(){if(applying)return;generation++;if(pending!=null)pending.cancel(true);finish();reviews.clear();empty();feedback.setText("Command cancelled. No changes were applied.");}
    public void dispose(){disposed=true;generation++;metricsGeneration++;if(pending!=null&&!applying)pending.cancel(true);if(metricsTask!=null)metricsTask.cancel(true);if(shimmer!=null)shimmer.stop();}
    private void showPrivacy(){Alert info=new Alert(Alert.AlertType.INFORMATION,"FORGE AI uses Google Gemini to interpret your command and draft public product content. Online specification research sends product names and catalog text for web lookup. Customer names, addresses and order rows are not sent. Free-tier content may help improve Google's products. Queries and sales totals run locally. All edits require your review before saving.",ButtonType.OK);info.setTitle("About AI Manager");info.setHeaderText("Your store, your approval");if(getScene()!=null)info.initOwner(getScene().getWindow());info.showAndWait();}
    private void openSource(String source){try{java.awt.Desktop.getDesktop().browse(java.net.URI.create(source));}catch(Exception e){feedback.setText("Could not open the source: "+source);}}
    private static void start(Task<?> task){Thread thread=new Thread(task,"forge-ai-manager");thread.setDaemon(true);thread.start();}
}
