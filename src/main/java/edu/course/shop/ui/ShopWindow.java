package edu.course.shop.ui;
import java.util.ArrayList;
import java.util.List;

import edu.course.shop.Services;
import edu.course.shop.model.Cart;
import edu.course.shop.model.CartItem;
import edu.course.shop.model.Category;
import edu.course.shop.model.Guest;
import edu.course.shop.model.Product;
import edu.course.shop.model.User;
import edu.course.shop.service.CartService;
import edu.course.shop.service.AssistantPreferences;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;


import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Navigation and transient session state, never SQL or pricing rules. */
public final class ShopWindow {
    private final Stage stage;private final Services services;
    private final SavedItems saved;
    private final CatalogState catalogState=new CatalogState();
    private final TextField search=new TextField();
    private User user=new Guest();private Cart cart=new Cart(0),pendingGuestCart;
    private final CheckoutDraft draft=new CheckoutDraft();
    private BorderPane shell;private StackPane window,overlay;private Button cartButton;
    private AdminWorkspace adminWorkspace;
    private HBox headerSearch,headerNavigation;
    private Node previousFocus;
    // Header selection and brief action-feedback animations.
    private final List<Button> navButtons=new ArrayList<>();
    private int lastCartCount=-1;
    private Label toast;private HBox toastBar;private SequentialTransition toastAnimation;
    private ScaleTransition cartPulse;
    private ParallelTransition drawerAnimation;
    private CartDrawer activeDrawer;
    private Region drawerScrim;
    private boolean drawerClosing;
    private final edu.course.shop.service.GeminiAssistant assistant=new edu.course.shop.service.GeminiAssistant();
    private AssistantPanel assistantPanel;
    private Button assistantLauncher;
    private Product assistantProduct;

    public ShopWindow(Stage stage,Services services){this.stage=stage;this.services=services;this.saved=new SavedItems(services.accounts());search.setId("search");}
    public void showStorefront(){buildShell();showCatalog();}
    public void showLogin(){
        if(assistantPanel!=null)assistantPanel.stop();
        dismissDrawer();stopFeedbackAnimations();
        LoginView login=new LoginView(services.auth(),this::signedIn);
        Button browse=Ui.button("← Continue browsing",this::showStorefront);browse.getStyleClass().add("header-link");browse.getStyleClass().add("login-browse");
        CheckBox motion=new CheckBox("Reduce motion");motion.setId("login-reduce-motion");motion.getStyleClass().add("motion-control");
        motion.setSelected(Motion.isReduced());motion.setOnAction(e->Motion.reducedProperty().set(motion.isSelected()));
        javafx.beans.InvalidationListener motionSync=o->motion.setSelected(Motion.isReduced());
        Motion.reducedProperty().addListener(new javafx.beans.WeakInvalidationListener(motionSync));
        motion.getProperties().put("market.motionSync",motionSync);
        Region footerSpace=new Region();HBox.setHgrow(footerSpace,Priority.ALWAYS);
        HBox controls=new HBox(14,footerSpace,browse,motion);controls.setId("login-utility");controls.setAlignment(Pos.CENTER_RIGHT);controls.getStyleClass().add("login-utility");
        controls.setMaxWidth(Double.MAX_VALUE);controls.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(controls,Pos.BOTTOM_CENTER);StackPane.setMargin(controls,new Insets(0,32,12,32));
        login.getChildren().add(controls);
        stage.getScene().setRoot(login);
    }
    private void signedIn(User signedIn){
        if(signedIn.canManageProducts()){saved.reset();completeSignIn(signedIn);return;}
        java.util.Set<Long> guestSaved=saved.snapshot();
        Ui.async(stage.getScene().getRoot(),"Loading saved items…",()->{
            java.util.Set<Long> ids=new java.util.HashSet<>(services.accounts().saved(signedIn));
            java.util.Set<Long> available=services.products().search(signedIn,"",null).stream().map(Product::getId).collect(java.util.stream.Collectors.toSet());
            for(long id:guestSaved)if(available.contains(id)){services.accounts().saveItem(signedIn,id,true);ids.add(id);}
            return ids;
        },ids->{saved.signedIn(signedIn,ids);completeSignIn(signedIn);},Ui::error);
    }
    private void completeSignIn(User signedIn){
        CartService carts=new CartService(services.products());Cart previous=cart;
        if(signedIn.canManageProducts()){
            pendingGuestCart=previous.getCustomerId()==0?previous:null;user=signedIn;cart=new Cart(user.getId());buildShell();showAdminOverview();return;
        }
        cart=previous.getCustomerId()==0?carts.transferGuest(previous,signedIn):new Cart(signedIn.getId());user=signedIn;buildShell();
        if(cart.getItems().isEmpty())showCatalog();
        else{
            showCart();Label feedback=(Label)shell.lookup("#cart-feedback");
            Ui.async(shell,"Reviewing your saved cart…",()->carts.review(cart),message->Ui.success(feedback,message),error->Ui.failure(feedback,error));
        }
    }
    private void signOut(){
        if(user.canShop()&&!cart.getItems().isEmpty()){
            Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"Sign out and discard this cart?",ButtonType.YES,ButtonType.NO);
            confirm.initOwner(stage);if(confirm.showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;
        }
        user=new Guest();cart=pendingGuestCart==null?new Cart(0):pendingGuestCart;pendingGuestCart=null;draft.clear();saved.reset();showStorefront();
    }
    private Button link(String text,Runnable action){Button b=Ui.button(text,action);b.getStyleClass().add("header-link");return b;}
    private void buildShell(){
        if(assistantPanel!=null)assistantPanel.stop();assistantPanel=null;assistantProduct=null;
        dismissDrawer();stopFeedbackAnimations();
        shell=new BorderPane();shell.setMinSize(0,0);adminWorkspace=null;
        // The toast sits in the window stack, above the page and below the cart drawer.
        toast=new Label();toast.setId("cart-toast-message");toast.setWrapText(true);toast.setMinWidth(0);HBox.setHgrow(toast,Priority.ALWAYS);
        Button review=Ui.button("Review cart →",this::showCartDrawer);review.setId("toast-review-cart");review.getStyleClass().add("toast-action");
        toastBar=new HBox(16,toast,review);toastBar.setId("cart-toast");toastBar.getStyleClass().add("toast");toastBar.setAlignment(Pos.CENTER);
        toastBar.setMinWidth(0);toastBar.setMaxWidth(720);toastBar.setMaxHeight(Region.USE_PREF_SIZE);toastBar.setOpacity(0);
        toastBar.visibleProperty().bind(toastBar.opacityProperty().greaterThan(0));
        StackPane.setAlignment(toastBar,Pos.BOTTOM_CENTER);StackPane.setMargin(toastBar,new Insets(0,24,64,24));
        window=new StackPane(shell,toastBar);window.setMinSize(0,0);StackPane.setAlignment(shell,Pos.TOP_LEFT);overlay=null;navButtons.clear();lastCartCount=-1;
        Label operation=Ui.feedback("operation-status");operation.getStyleClass().add("operation");
        if(user.canManageProducts()){
            adminWorkspace=new AdminWorkspace(user,services,this::showAdminOverview,this::showAdmin,this::showAdminOrders,this::showAdminCustomers,this::showAiManager,this::addAdminProduct,this::signOut);
            shell.setCenter(adminWorkspace);cartButton=null;
        }else{
            search.setPromptText("Search the collection");search.setAccessibleText("Search products");search.setMinWidth(130);search.setPrefWidth(260);
            Runnable find=()->{catalogState.setScrollPosition(0);showCatalog();};search.setOnAction(e->find.run());
            Button logo=Ui.button("FORGE",()->category(0));logo.getStyleClass().add("brand-link");logo.setMinWidth(Region.USE_PREF_SIZE);
            cartButton=link("Cart · 0",this::showCartDrawer);cartButton.setId("nav-cart");
            cartButton.setGraphic(StoreIcon.of("bag",18));cartButton.getStyleClass().add("cart-button");
            Button account=link(user instanceof Guest?"Sign in":"My orders",user instanceof Guest?this::showLogin:this::showOrders);account.setId("account");
            account.setGraphic(StoreIcon.of("account",18));account.setMinWidth(Region.USE_PREF_SIZE);
            Button favorites=link("Saved",this::showSaved);favorites.setGraphic(StoreIcon.of("heart",18));favorites.setId("nav-saved");favorites.setAccessibleText("Saved items");
            HBox right=new HBox(6,favorites,account,cartButton);right.setAlignment(Pos.CENTER_RIGHT);right.setMinWidth(Region.USE_PREF_SIZE);
            if(!(user instanceof Guest)){
                Button profile=link("Profile",this::showAccount);profile.setId("nav-profile");
                Button signOut=link("Sign out",this::signOut);
                profile.visibleProperty().bind(shell.widthProperty().greaterThanOrEqualTo(1100));profile.managedProperty().bind(profile.visibleProperty());
                signOut.visibleProperty().bind(profile.visibleProperty());signOut.managedProperty().bind(signOut.visibleProperty());
                account.visibleProperty().bind(profile.visibleProperty());account.managedProperty().bind(account.visibleProperty());
                javafx.scene.control.MenuButton accountMenu=new javafx.scene.control.MenuButton("Account");accountMenu.setId("compact-account-menu");
                accountMenu.getStyleClass().add("compact-menu");
                javafx.scene.control.MenuItem profileItem=new javafx.scene.control.MenuItem("Profile");profileItem.setOnAction(e->showAccount());
                javafx.scene.control.MenuItem exitItem=new javafx.scene.control.MenuItem("Sign out");exitItem.setOnAction(e->signOut());
                javafx.scene.control.MenuItem ordersItem=new javafx.scene.control.MenuItem("My orders");ordersItem.setOnAction(e->showOrders());
                accountMenu.getItems().addAll(ordersItem,profileItem,exitItem);
                accountMenu.visibleProperty().bind(profile.visibleProperty().not());accountMenu.managedProperty().bind(accountMenu.visibleProperty());
                right.getChildren().addAll(profile,signOut,accountMenu);
            }
            if(user instanceof Guest){
                Button guestProfile=link("Profile",this::showLogin);
                guestProfile.setId("guest-profile");
                guestProfile.setAccessibleText("Profile — sign in to view your account");
                right.getChildren().add(guestProfile);
            }
            Button all=Ui.button("Shop all",()->category(0));navButtons.add(all);
            HBox categories=new HBox(6,all);categories.setAlignment(Pos.CENTER_LEFT);
            categories.getStyleClass().addAll("category-nav","integrated-categories");
            for(Category c:Category.shopping()){
                Button b=Ui.button(Ui.category(c),()->category(c.shoppingIndex()));navButtons.add(b);categories.getChildren().add(b);
            }
            Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
            javafx.scene.control.MenuButton menu=new javafx.scene.control.MenuButton("Shop");menu.setId("compact-shop-menu");menu.getStyleClass().add("compact-menu");
            javafx.scene.control.MenuItem allItem=new javafx.scene.control.MenuItem("Shop all");allItem.setOnAction(e->category(0));menu.getItems().add(allItem);
            for(Category c:Category.shopping()){javafx.scene.control.MenuItem item=new javafx.scene.control.MenuItem(Ui.category(c));item.setOnAction(e->category(c.shoppingIndex()));menu.getItems().add(item);}
            headerSearch=new HBox();headerSearch.setId("header-search-slot");headerSearch.setAlignment(Pos.CENTER_LEFT);
            headerSearch.setMinWidth(200);headerSearch.setPrefWidth(320);headerSearch.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(headerSearch,Priority.ALWAYS);
            headerNavigation=new HBox();headerNavigation.setAlignment(Pos.CENTER_LEFT);headerNavigation.setMinWidth(Region.USE_PREF_SIZE);
            HBox header=new HBox(12,logo,menu,headerNavigation,headerSearch,right,spacer);
            categories.setId("secondary-category-nav");categories.setAlignment(Pos.CENTER);categories.getStyleClass().add("tech-category-nav");
            var outsideCatalog=javafx.beans.binding.Bindings.createBooleanBinding(()->!(shell.getCenter() instanceof CatalogView),shell.centerProperty());
            var checkoutPage=javafx.beans.binding.Bindings.createBooleanBinding(()->shell.getCenter() instanceof CheckoutView,shell.centerProperty());
            spacer.visibleProperty().bind(checkoutPage);spacer.managedProperty().bind(checkoutPage);
            right.visibleProperty().bind(checkoutPage.not());right.managedProperty().bind(right.visibleProperty());
            HBox checkoutLinks=new HBox(18,link("← Back to cart",this::showCart),Ui.label("Demo checkout","checkout-header-note"));checkoutLinks.setAlignment(Pos.CENTER_RIGHT);checkoutLinks.visibleProperty().bind(checkoutPage);checkoutLinks.managedProperty().bind(checkoutPage);header.getChildren().add(checkoutLinks);
            headerSearch.visibleProperty().bind(checkoutPage.not());headerSearch.managedProperty().bind(headerSearch.visibleProperty());
            categories.visibleProperty().bind(header.widthProperty().greaterThanOrEqualTo(1100).and(outsideCatalog).and(checkoutPage.not()));categories.managedProperty().bind(categories.visibleProperty());
            menu.visibleProperty().bind(header.widthProperty().lessThan(1100).and(outsideCatalog).and(checkoutPage.not()));menu.managedProperty().bind(menu.visibleProperty());
            header.setMinWidth(0);header.setAlignment(Pos.CENTER_LEFT);header.getStyleClass().add("store-header");
            Label announcement=Ui.label("GOOD FINDS. ZERO SHIPPING FEES.  /  Free shipping on every demo order","store-announcement");announcement.setMaxWidth(Double.MAX_VALUE);
            shell.setTop(new VBox(header,categories,operation));updateCart();
        }
        // Footer: two real labels pushed apart, instead of one label padded with spaces.
        Region footerSpace=new Region();HBox.setHgrow(footerSpace,Priority.ALWAYS);
        CheckBox motion=new CheckBox("Reduce motion");motion.setId("reduce-motion");motion.getStyleClass().add("motion-control");
        motion.selectedProperty().bindBidirectional(Motion.reducedProperty());
        HBox footer=new HBox(16,Ui.label("FORGE · Good things, every day.","muted"),footerSpace,motion,link("About",this::showInfo),Ui.label("USD · Course demo · Simulated payments","muted"));
        if(!user.canManageProducts()) {
            Button ask=link("FORGE AI",this::toggleAssistant);
            ask.setId("ask-forge");footer.getChildren().add(2,ask);
        }
        footer.getStyleClass().add("store-footer");footer.setAlignment(Pos.CENTER_LEFT);
        if(!user.canManageProducts())shell.setBottom(footer);stage.getScene().setRoot(window);
        if(!user.canManageProducts()) {
            assistantLauncher=Ui.button("✦  FORGE AI",this::toggleAssistant);assistantLauncher.setId("assistant-launcher");assistantLauncher.getStyleClass().add("assistant-launcher");
            assistantLauncher.setMaxSize(Region.USE_PREF_SIZE,Region.USE_PREF_SIZE);StackPane.setAlignment(assistantLauncher,Pos.BOTTOM_RIGHT);StackPane.setMargin(assistantLauncher,new Insets(0,18,74,0));
            assistantPanel=new AssistantPanel(services,user,()->cart,()->assistantProduct,assistant,this::showDetails,this::updateCart,saved,this::toggleAssistant);
            assistantPanel.setVisible(false);assistantPanel.managedProperty().bind(assistantPanel.visibleProperty());
            assistantPanel.prefWidthProperty().bind(javafx.beans.binding.Bindings.min(420,window.widthProperty()));
            assistantPanel.maxWidthProperty().bind(assistantPanel.prefWidthProperty());
            assistantPanel.prefHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(()->Math.max(0,window.getHeight()-shell.getTop().getBoundsInParent().getHeight()),window.heightProperty(),shell.getTop().boundsInParentProperty()));
            assistantPanel.maxHeightProperty().bind(assistantPanel.prefHeightProperty());StackPane.setAlignment(assistantPanel,Pos.BOTTOM_RIGHT);
            assistantPanel.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,e->{if(e.getCode()==KeyCode.ESCAPE){toggleAssistant();e.consume();}});
            window.getChildren().addAll(assistantLauncher,assistantPanel);
        }
    }
    private void toggleAssistant(){
        if(assistantPanel==null)return;
        boolean open=!assistantPanel.isVisible();assistantPanel.setVisible(open);assistantLauncher.setVisible(!open);
        if(open){assistantPanel.refreshContext();assistantPanel.toFront();assistantPanel.setTranslateX(0);if(!Motion.isReduced()){TranslateTransition slide=new TranslateTransition(Duration.millis(210),assistantPanel);slide.setFromX(420);slide.setToX(0);slide.setInterpolator(Interpolator.EASE_OUT);slide.play();}}
        else {assistantPanel.stop();assistantLauncher.requestFocus();}
    }
    private void showInfo(){
        Alert info=new Alert(Alert.AlertType.INFORMATION,"Browse without an account. Sign in as a customer to place a simulated order.\n\nShipping is free and taxes are omitted in this demo. No real money or delivery is involved.\n\nAdmins manage descriptions, photos and inventory. Your cart is temporary; completed orders are saved.",ButtonType.OK);
        info.initOwner(stage);info.setTitle("About FORGE");info.setHeaderText("A small shop. Good things.");info.showAndWait();
    }
    private void category(int index){catalogState.setCategoryIndex(index);catalogState.setScrollPosition(0);search.clear();showCatalog();}
    private void show(Node page){assistantProduct=null;BorderPane target=adminWorkspace!=null&&user.canManageProducts()?adminWorkspace.pageHost():shell;if(target.getCenter() instanceof AiManagerView previous&&previous!=page)previous.dispose();if(target.getCenter() instanceof CatalogView previous&&previous!=page)previous.stopSearchSuggestions();dismissDrawer();stopFeedbackAnimations();if(headerNavigation!=null&&!(page instanceof CatalogView)){headerNavigation.setVisible(false);headerNavigation.setManaged(false);}target.setCenter(page instanceof AdminReportsView?Ui.scroll(page):page);if(!(page instanceof CatalogView)){search.setOnAction(e->{catalogState.setScrollPosition(0);showCatalog();});Motion.reveal(page);}updateNav();if(assistantPanel!=null)assistantPanel.refreshContext();}

    /** Highlights the header button for the current category, only while the storefront is showing. */
    private void updateNav(){
        boolean onCatalog=shell!=null&&shell.getCenter() instanceof CatalogView;
        int active=catalogState.getCategoryIndex();
        for(int i=0;i<navButtons.size();i++){
            List<String> styles=navButtons.get(i).getStyleClass();styles.remove("category-active");
            if(onCatalog&&i==active)styles.add("category-active");
        }
    }

    private void updateCart(){
        if(cartButton==null)return;
        int count=cart.getItems().stream().mapToInt(CartItem::getQuantity).sum();
        cartButton.setText("Cart · "+count);
        // One gentle pulse per addition; a new addition replaces any unfinished pulse.
        if(lastCartCount>=0&&count>lastCartCount&&!Motion.isReduced()){
            if(cartPulse!=null)cartPulse.stop();
            cartButton.setScaleX(1);cartButton.setScaleY(1);
            cartPulse=new ScaleTransition(Duration.millis(90),cartButton);
            cartPulse.setFromX(1);cartPulse.setFromY(1);cartPulse.setToX(1.06);cartPulse.setToY(1.06);
            cartPulse.setCycleCount(2);cartPulse.setAutoReverse(true);cartPulse.play();
        }
        lastCartCount=count;
    }

    /** Floating confirmation at the bottom of the window; fades in, holds, fades out. */
    private void showToast(String message){
        if(toast==null)return;
        toast.setText(message);if(toastAnimation!=null)toastAnimation.stop();
        FadeTransition in=new FadeTransition(Duration.millis(Motion.isReduced()?1:150),toastBar);in.setToValue(1);
        PauseTransition hold=new PauseTransition(Duration.seconds(4));
        FadeTransition out=new FadeTransition(Duration.millis(Motion.isReduced()?1:150),toastBar);out.setToValue(0);
        toastAnimation=new SequentialTransition(in,hold,out);toastAnimation.play();
    }

    private void stopFeedbackAnimations(){
        if(toastAnimation!=null){toastAnimation.stop();toastAnimation=null;}
        if(toastBar!=null)toastBar.setOpacity(0);
        if(cartPulse!=null){cartPulse.stop();cartPulse=null;}
        if(cartButton!=null){cartButton.setScaleX(1);cartButton.setScaleY(1);}
    }
    public void showCatalog(){CatalogView catalog=new CatalogView(user,cart,services.products(),new CartService(services.products()),(Runnable)this::updateCart,(Runnable)this::showCartDrawer,(java.util.function.Consumer<Product>)this::showDetails,search,catalogState,(java.util.function.Consumer<String>)this::showToast,(Runnable)this::updateNav,assistant,new AssistantPreferences());catalog.enableSavedItems(saved);headerNavigation.getChildren().clear();catalog.useHeaderNavigation(headerNavigation);headerSearch.getChildren().setAll(catalog.searchBar());HBox.setHgrow(catalog.searchBar(),Priority.ALWAYS);show(catalog);}
    private void showDetails(Product product){
        dismissDrawer();
        VBox loading=Ui.skeletonProductDetail();
        show(loading);
        Ui.readOnly(shell,()->services.products().get(product.getId()),latest->{
            if(shell.getCenter()!=loading)return;
            if(!latest.isActive()){showCatalog();return;}
            show(new ProductDetailView(latest,user,cart,new CartService(services.products()),services.products(),this::updateCart,this::showCatalog,this::showCartDrawer,this::showDetails,saved,this::showToast,services.reviews(),assistant));
            assistantProduct=latest;if(assistantPanel!=null)assistantPanel.refreshContext();
        },error->{
            if(shell.getCenter()!=loading)return;
            VBox failed=Ui.page("Couldn’t open this product","The catalog could not load the latest product details.",Ui.primary("Try again",()->showDetails(product)),Ui.link("Back to products",this::showCatalog));
            show(failed);
        });
    }
    public void showCartDrawer(){
        if(overlay!=null)return;previousFocus=stage.getScene().getFocusOwner();stopFeedbackAnimations();
        drawerClosing=false;
        drawerScrim=new Region();drawerScrim.getStyleClass().add("drawer-scrim");
        drawerScrim.setOnMouseClicked(e->closeDrawer());drawerScrim.setOpacity(0);
        activeDrawer=new CartDrawer(user,cart,new CartService(services.products()),this::updateCart,this::closeDrawer,this::showCart,this::showCheckout);
        overlay=new StackPane(drawerScrim,activeDrawer);StackPane.setAlignment(activeDrawer,Pos.CENTER_RIGHT);
        overlay.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,e->{
            if(e.getCode()==KeyCode.ESCAPE){closeDrawer();e.consume();}
            else if(e.getCode()==KeyCode.TAB) {
                List<Node> focusable=new ArrayList<>();drawerFocusNodes(activeDrawer,focusable);
                if(!focusable.isEmpty()) {
                    int index=focusable.indexOf(stage.getScene().getFocusOwner());
                    focusable.get(Math.floorMod(index+(e.isShiftDown()?-1:1),focusable.size())).requestFocus();
                }
                e.consume();
            }
        });
        shell.setDisable(true);window.getChildren().add(overlay);
        // Lay out first so the slide distance also works in a smaller window.
        window.applyCss();window.layout();activeDrawer.setTranslateX(activeDrawer.getWidth());
        animateDrawer(0,1,null);
        activeDrawer.lookup("#close-cart-drawer").requestFocus();
    }
    private static void drawerFocusNodes(Node node,List<Node> result) {
        if(!node.isVisible()||node.isDisabled())return;
        if(node.isFocusTraversable())result.add(node);
        // Control skins have their own internal focus handling; visit layout containers only.
        if(node instanceof javafx.scene.control.ScrollPane scroll)drawerFocusNodes(scroll.getContent(),result);
        else if(node instanceof javafx.scene.layout.Pane pane)for(Node child:pane.getChildren())drawerFocusNodes(child,result);
    }
    private void animateDrawer(double x,double opacity,Runnable finished){
        if(drawerAnimation!=null)drawerAnimation.stop();
        if(Motion.isReduced()) {
            drawerAnimation=null;activeDrawer.setTranslateX(x);drawerScrim.setOpacity(opacity);
            if(finished!=null)finished.run();return;
        }
        TranslateTransition slide=new TranslateTransition(Duration.millis(200),activeDrawer);
        slide.setToX(x);slide.setInterpolator(Interpolator.EASE_BOTH);
        FadeTransition fade=new FadeTransition(Duration.millis(200),drawerScrim);fade.setToValue(opacity);
        drawerAnimation=new ParallelTransition(slide,fade);
        drawerAnimation.setOnFinished(e->{drawerAnimation=null;if(finished!=null)finished.run();});
        drawerAnimation.play();
    }
    /** Navigation cancels pending motion immediately, so old callbacks cannot change the next page. */
    private void dismissDrawer(){
        if(drawerAnimation!=null){drawerAnimation.stop();drawerAnimation=null;}
        if(overlay!=null){window.getChildren().remove(overlay);overlay=null;shell.setDisable(false);}
        activeDrawer=null;drawerScrim=null;drawerClosing=false;
    }
    private void closeDrawer(){
        if(overlay==null||drawerClosing)return;
        drawerClosing=true;
        animateDrawer(activeDrawer.getWidth(),0,()->{
            dismissDrawer();
            // Reflect drawer edits without losing the checkout draft.
            if(shell.getCenter() instanceof CartView)showCart();else if(shell.getCenter() instanceof CheckoutView)showCheckout();
            if(previousFocus!=null&&previousFocus.getScene()==stage.getScene()&&canFocus(previousFocus))previousFocus.requestFocus();
            else if(cartButton!=null)cartButton.requestFocus();
        });
    }
    private static boolean canFocus(Node node) {
        for(Node current=node;current!=null;current=current.getParent())if(!current.isVisible()||current.isDisabled())return false;
        return true;
    }
    public void showCart(){show(new CartView(user,cart,new CartService(services.products()),this::updateCart,this::showCheckout,this::showCatalog));}
    public void showCheckout(){
        dismissDrawer();if(user instanceof Guest){showLogin();return;}
        if(cart.getItems().isEmpty()){showCart();return;}
        show(new CheckoutView(user,cart,services.checkout(),services.accounts(),draft,order->{updateCart();show(new ConfirmationView(order,this::showOrders,this::showCatalog));},this::showCart));
    }
    public void showOrders(){show(new OrdersView(user,services.orders(),services.products(),this::showCatalog,this::repeatOrder));}
    public void showAdmin(){show(new AdminView(user,services.products(),this::runAdminCommand));markAdminTab("nav-inventory");}
    private void addAdminProduct(){showAdmin();javafx.application.Platform.runLater(()->{Node button=adminWorkspace.lookup("#new-product");if(button instanceof Button add)add.fire();});}
    private void runAdminCommand(String command){showAiManager();javafx.application.Platform.runLater(()->{if(adminWorkspace.pageHost().getCenter() instanceof AiManagerView view)view.submit(command);});}
    private void showAdminOverview(){show(new AdminReportsView(user,services.admin(),AdminReportsView.Section.OVERVIEW,this::showAdminOrders,this::showAdmin,this::showAiManager,this::addAdminProduct));markAdminTab("admin-nav-overview");}
    private void showAdminOrders(){show(new AdminReportsView(user,services.admin(),AdminReportsView.Section.ORDERS,this::showAdminOrders));markAdminTab("admin-nav-orders");}
    private void showAdminCustomers(){show(new AdminReportsView(user,services.admin(),AdminReportsView.Section.CUSTOMERS,this::showAdminOrders));markAdminTab("admin-nav-customers");}
    private void showAiManager(){show(new AiManagerView(user,services,assistant));markAdminTab("admin-nav-ai-manager");}
    private void markAdminTab(String selected){
        if(shell==null)return;
        if(adminWorkspace!=null){adminWorkspace.select(selected);return;}
        for(String id:List.of("admin-nav-overview","nav-inventory","admin-nav-orders","admin-nav-customers","admin-nav-ai-manager")){
            Node node=shell.lookup("#"+id);if(node==null)continue;
            node.getStyleClass().remove("admin-nav-active");if(id.equals(selected))node.getStyleClass().add("admin-nav-active");
        }
    }
    private void showSaved(){show(new SavedItemsView(user,cart,services.products(),saved,this::updateCart,this::showCatalog,this::showDetails,this::showToast));}
    private void showAccount(){if(user instanceof Guest){showLogin();return;}show(new AccountView(user,services.accounts(),this::showSaved,this::showOrders));}
    private void repeatOrder(edu.course.shop.model.Order order) {
        CartService carts=new CartService(services.products());
        Ui.async(shell,"Checking current availability…",()->carts.prepareRepeat(user,cart,order),plan->{
            StringBuilder message=new StringBuilder("Current catalog prices:\n");
            for(var line:plan.lines())message.append(line.quantity()).append(" × ").append(line.product().getName()).append(" · ").append(edu.course.shop.model.Money.format(line.product().getPrice())).append(" each\n");
            if(!plan.unavailable().isEmpty())message.append("\nNot added:\n").append(String.join("\n",plan.unavailable()));
            if(plan.lines().isEmpty()){Ui.info(message.toString());return;}
            message.append("\nAdd available items to your cart? Existing quantities combine at the shown price.");
            Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,message.toString(),ButtonType.YES,ButtonType.NO);confirm.initOwner(stage);confirm.setHeaderText("Review Buy again");
            if(confirm.showAndWait().orElse(ButtonType.NO)==ButtonType.YES)
                Ui.async(shell,"Adding available items…",()->{carts.applyRepeat(user,cart,plan);return true;},done->{updateCart();showCart();},Ui::error);
        },Ui::error);
    }
}
