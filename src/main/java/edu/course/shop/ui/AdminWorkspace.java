package edu.course.shop.ui;

import edu.course.shop.Services;
import edu.course.shop.model.User;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import java.util.*;

/** Native desktop version of the supplied FORGE dashboard shell. */
public final class AdminWorkspace extends StackPane {
    private final BorderPane pageHost=new BorderPane();
    private final Label section=Ui.label("Overview","workspace-section"),description=Ui.label("Store metrics & trend analytics","workspace-context");
    private final Map<String,Button> navigation=new LinkedHashMap<>();
    private final Map<String,Label> counts=new HashMap<>();
    private final Services services;private final User actor;
    private final Runnable addProduct;
    private final Button theme;
    private StackPane modal;
    private boolean dark;
    public AdminWorkspace(User actor,Services services,Runnable overview,Runnable products,Runnable orders,Runnable customers,Runnable ai,Runnable addProduct,Runnable signOut){
        this.actor=actor;this.services=services;this.addProduct=addProduct;
        setId("admin-workspace");getStyleClass().add("forge-admin");setMinSize(0,0);
        BorderPane frame=new BorderPane();getChildren().add(frame);
        Label monogram=Ui.label("F","workspace-monogram"),brand=Ui.label("FORGE","workspace-brand");brand.setId("admin-forge-logo");
        Region brandSpace=new Region();HBox.setHgrow(brandSpace,Priority.ALWAYS);
        HBox logo=new HBox(10,monogram,brand,brandSpace,Ui.label("PRO","workspace-pro"));logo.setAlignment(Pos.CENTER_LEFT);logo.getStyleClass().add("workspace-logo");
        VBox nav=new VBox(6,Ui.label("MANAGE","workspace-nav-caption"));nav.setId("admin-navigation");
        nav.getChildren().addAll(nav("Overview","dashboard","admin-nav-overview",overview,false),nav("Products","package","nav-inventory",products,true),nav("Orders","bag","admin-nav-orders",orders,true),nav("Customers","users","admin-nav-customers",customers,true),nav("AI Manager","sparkles","admin-nav-ai-manager",ai,false));
        theme=Ui.button("Dark Obsidian",this::toggleTheme);theme.setId("admin-theme-toggle");theme.setGraphic(StoreIcon.of("moon",14));theme.getStyleClass().add("workspace-utility");theme.setMaxWidth(Double.MAX_VALUE);
        Button command=Ui.button("Command menu       Ctrl K",()->commandMenu(overview,products,orders,customers,ai));command.setId("admin-command-menu");command.setGraphic(StoreIcon.of("command",14));command.getStyleClass().add("workspace-utility");command.setMaxWidth(Double.MAX_VALUE);
        VBox utilities=new VBox(8,theme,command);utilities.getStyleClass().add("workspace-utilities");
        BorderPane sidebar=new BorderPane();sidebar.setId("admin-sidebar");sidebar.getStyleClass().add("workspace-sidebar");sidebar.setTop(logo);sidebar.setCenter(nav);sidebar.setBottom(utilities);sidebar.setPrefWidth(256);sidebar.setMinWidth(210);frame.setLeft(sidebar);
        Region gap=new Region();HBox.setHgrow(gap,Priority.ALWAYS);
        Button exit=Ui.button("Sign out",signOut);exit.setId("admin-sign-out");exit.setGraphic(StoreIcon.of("logout",13));exit.getStyleClass().add("workspace-black-button");
        Label online=Ui.label("●  ADMIN ONLINE","workspace-online");online.setTooltip(new Tooltip("Signed in as "+actor.getUsername()));
        HBox header=new HBox(14,section,Ui.label("/","workspace-separator"),description,gap,online,exit);header.setAlignment(Pos.CENTER_LEFT);header.getStyleClass().add("workspace-topbar");
        BorderPane main=new BorderPane(pageHost);main.setMinSize(0,0);main.setTop(header);main.getStyleClass().add("workspace-main");frame.setCenter(main);
        Button docs=Ui.link("Documentation",()->{
            Alert info=new Alert(Alert.AlertType.INFORMATION,"Manage products, review orders and customers, or use AI Manager to prepare changes. AI drafts require review before saving. Ctrl+K opens commands; Ctrl+D changes theme.",ButtonType.OK);
            info.initOwner(getScene().getWindow());info.setHeaderText("FORGE Store Management");info.showAndWait();
        });
        Region footerSpace=new Region();HBox.setHgrow(footerSpace,Priority.ALWAYS);
        HBox footer=new HBox(14,Ui.label("FORGE Enterprise · Store Management System","workspace-footer-text"),footerSpace,docs,Ui.label("USD · Simulated payments","workspace-footer-text"));footer.setAlignment(Pos.CENTER_LEFT);footer.getStyleClass().add("workspace-footer");main.setBottom(footer);
        widthProperty().addListener((o,a,b)->{sidebar.setPrefWidth(b.doubleValue()<1100?220:256);description.setVisible(b.doubleValue()>=1100);description.setManaged(description.isVisible());});
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,e->{
            if(e.getCode()==KeyCode.ESCAPE&&modal!=null){closeModal();e.consume();}
            else if(e.isControlDown()&&e.getCode()==KeyCode.K){commandMenu(overview,products,orders,customers,ai);e.consume();}
            else if(e.isControlDown()&&e.getCode()==KeyCode.D){toggleTheme();e.consume();}
        });
    }
    public BorderPane pageHost(){return pageHost;}
    private Button nav(String title,String icon,String id,Runnable action,boolean count){
        Button button=Ui.button("",()->{closeModal();action.run();});button.setId(id);button.getStyleClass().add("workspace-nav");button.setMaxWidth(Double.MAX_VALUE);
        Label text=Ui.label(title,"workspace-nav-text");Region gap=new Region();HBox.setHgrow(gap,Priority.ALWAYS);
        HBox graphic=new HBox(12,StoreIcon.of(icon,16),text,gap);graphic.setAlignment(Pos.CENTER_LEFT);graphic.setMinWidth(0);graphic.prefWidthProperty().bind(button.widthProperty().subtract(30));
        if(count){Label badge=Ui.label("—","workspace-nav-count");counts.put(id,badge);graphic.getChildren().add(badge);}
        if(id.equals("admin-nav-ai-manager"))graphic.getChildren().add(Ui.label("AI","workspace-ai-tag"));
        button.setGraphic(graphic);button.setAccessibleText(title);navigation.put(id,button);return button;
    }
    public void select(String id){
        navigation.forEach((key,button)->{button.getStyleClass().remove("admin-nav-active");if(key.equals(id))button.getStyleClass().add("admin-nav-active");});
        section.setText(switch(id){case "nav-inventory"->"Products";case "admin-nav-orders"->"Orders";case "admin-nav-customers"->"Customers";case "admin-nav-ai-manager"->"AI Neural Manager";default->"Overview";});
        description.setText(switch(id){case "nav-inventory"->"Inventory & price controls";case "admin-nav-orders"->"Checkouts & billing log";case "admin-nav-customers"->"Customer directory";case "admin-nav-ai-manager"->"Store command workspace";default->"Store metrics & trend analytics";});
        Task<int[]> task=new Task<>(){protected int[] call(){var summary=services.admin().summary(actor);return new int[]{services.products().search(actor,"",null).size(),summary.orders(),summary.customers()};}};
        task.setOnSucceeded(e->{int[] n=task.getValue();counts.get("nav-inventory").setText(""+n[0]);counts.get("admin-nav-orders").setText(""+n[1]);counts.get("admin-nav-customers").setText(""+n[2]);});
        Thread worker=new Thread(task,"admin-navigation-counts");worker.setDaemon(true);worker.start();
    }
    private void toggleTheme(){dark=!dark;getStyleClass().remove("admin-dark");if(dark)getStyleClass().add("admin-dark");theme.setText(dark?"Light Theme":"Dark Obsidian");theme.setGraphic(StoreIcon.of(dark?"sun":"moon",14));}
    private void commandMenu(Runnable overview,Runnable products,Runnable orders,Runnable customers,Runnable ai){
        closeModal();TextField query=new TextField();query.setId("admin-command-search");query.setPromptText("Type a command or search...");
        VBox list=new VBox(4);List<String> titles=List.of("Go to Overview","Manage Products & Inventory","View Customer Orders","Customer Directory","Open AI Manager","Add New Product","Toggle Dark/Light Mode");
        List<Runnable> actions=List.of(overview,products,orders,customers,ai,addProduct,this::toggleTheme);
        Runnable filter=()->{list.getChildren().clear();for(int i=0;i<titles.size();i++){int index=i;if(!titles.get(i).toLowerCase(Locale.ROOT).contains(query.getText().toLowerCase(Locale.ROOT)))continue;Button b=Ui.button(titles.get(i),()->{closeModal();actions.get(index).run();});b.setMaxWidth(Double.MAX_VALUE);b.getStyleClass().add("workspace-command-item");list.getChildren().add(b);}};
        query.textProperty().addListener((o,a,b)->filter.run());query.setOnAction(e->{if(!list.getChildren().isEmpty())((Button)list.getChildren().getFirst()).fire();});filter.run();
        VBox card=new VBox(14,query,Ui.label("NAVIGATION & QUICK ACTIONS","workspace-nav-caption"),list,Ui.label("FORGE Command Center                                      ESC to close","muted"));card.getStyleClass().add("workspace-command-card");card.setMaxWidth(570);card.setMaxHeight(Region.USE_PREF_SIZE);
        showModal(card,false);StackPane.setAlignment(card,Pos.TOP_CENTER);StackPane.setMargin(card,new Insets(80,20,20,20));Platform.runLater(query::requestFocus);
    }
    private void showModal(Node card,boolean drawer){
        closeModal();Region scrim=new Region();scrim.getStyleClass().add("workspace-scrim");scrim.setOnMouseClicked(e->closeModal());
        modal=new StackPane(scrim,card);modal.setId("admin-modal");getChildren().add(modal);if(drawer){StackPane.setAlignment(card,Pos.CENTER_RIGHT);Motion.reveal(card);}
    }
    private void closeModal(){if(modal!=null){getChildren().remove(modal);modal=null;}}
    public static void inspect(Node owner,String type,String title,List<Node> fields,Runnable edit){
        Node ancestor=owner;while(ancestor!=null&&!(ancestor instanceof AdminWorkspace))ancestor=ancestor.getParent();if(!(ancestor instanceof AdminWorkspace workspace))return;
        VBox values=new VBox(16);values.getChildren().addAll(fields);Button close=Ui.button("Close",workspace::closeModal);
        HBox heading=new HBox(10,Ui.label(type,"workspace-pro"),Ui.label(title,"section-title"));heading.setAlignment(Pos.CENTER_LEFT);
        Region space=new Region();VBox.setVgrow(space,Priority.ALWAYS);HBox actions=new HBox(10,close);actions.setAlignment(Pos.CENTER_RIGHT);
        if(edit!=null){Button action=Ui.primary("Edit product",()->{workspace.closeModal();edit.run();});action.getStyleClass().add("workspace-orange-button");actions.getChildren().add(action);}
        VBox drawer=new VBox(24,heading,Ui.scroll(values),space,actions);drawer.getStyleClass().add("workspace-drawer");drawer.setPrefWidth(410);drawer.setMaxWidth(410);VBox.setVgrow(drawer.getChildren().get(1),Priority.ALWAYS);workspace.showModal(drawer,true);
    }
    public static VBox detail(String label,String value){VBox field=new VBox(7,Ui.label(label,"eyebrow"),Ui.label(value,"workspace-detail-value"));field.getStyleClass().add("workspace-detail-field");return field;}
}
