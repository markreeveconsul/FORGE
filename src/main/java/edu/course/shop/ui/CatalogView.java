package edu.course.shop.ui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import edu.course.shop.model.Cart;
import edu.course.shop.model.Category;
import edu.course.shop.model.Product;
import edu.course.shop.model.User;
import edu.course.shop.service.CartService;
import edu.course.shop.service.ProductService;
import edu.course.shop.service.GeminiAssistant;
import edu.course.shop.service.AssistantPreferences;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

public final class CatalogView extends VBox {
    private record ImageSearchData(List<Product> catalog, GeminiAssistant.ImageSearchResult response) { }
    private boolean clearing, restoring;
    private final javafx.beans.property.BooleanProperty rangeApplied=new javafx.beans.property.SimpleBooleanProperty();
    private final Button toggle=Ui.button("Filters +",()->{});
    private Button imageSearch;
    private Task<?> imageSearchTask;
    private final GeminiAssistant gemini;
    private final AssistantPreferences preferences;
    private final FlowPane filters=new FlowPane(20,18);
    private final TilePane grid=new TilePane(20,24);
    private final TextField search;
    private final TextField minimum=new TextField(),maximum=new TextField();
    private SavedItems saved;
    public void enableSavedItems(SavedItems saved){this.saved=saved;}
    private final CatalogState state;
    private final Consumer<Product> details;
    private final ComboBox<String> category=new ComboBox<>(),sort=new ComboBox<>(),budget=new ComboBox<>();
    private final CheckBox available=new CheckBox("In stock only");
    // Sentence case reads calmer and more modern than ALL CAPS; the hero stays uppercase as the one brand moment.
    private final Label feedback=Ui.feedback("catalog-feedback"),result=Ui.label("","muted"),title=Ui.label("Fresh from the forge","collection-title");
    private final FlowPane chips=new FlowPane(8,8);
    private final VBox results=new VBox(16);
    private VBox collectionSection;
    private final ScrollPane scroll;
    private final EditorialHeader hero;
    private final HBox searchBar;
    private final SearchSuggestions suggestions;
    public void stopSearchSuggestions(){suggestions.close();}
    private HBox navigation;
    private javafx.scene.layout.StackPane campaign;
    public void useHeaderNavigation(HBox host) {
        Runnable place=()->{
            javafx.scene.layout.Pane target=getWidth()>=1200?host:campaign;
            if(navigation.getParent()!=target){if(navigation.getParent() instanceof javafx.scene.layout.Pane old)old.getChildren().remove(navigation);target.getChildren().add(navigation);}
            host.setManaged(target==host);host.setVisible(target==host);
        };
        widthProperty().addListener((o,a,b)->place.run());place.run();
    }
    public HBox searchBar() { return searchBar; }
    private final User user;private final Cart cart;private final ProductService products;private final CartService carts;
    private final Runnable changed;
    private final Consumer<String> toast; // optional: shows a floating "added to cart" message (null = old inline message)
    private final Runnable navSync;       // optional: lets the header highlight the active category (null = nothing)

    /** Original signature kept, so any existing caller or test still compiles. */
    public CatalogView(User user,Cart cart,ProductService products,CartService carts,Runnable changed,Runnable openCart,
                       Consumer<Product> details,TextField search,CatalogState state) {
        this(user,cart,products,carts,changed,openCart,details,search,state,null,null,new GeminiAssistant(),new AssistantPreferences());
    }

    public CatalogView(User user,Cart cart,ProductService products,CartService carts,Runnable changed,Runnable openCart,
                       Consumer<Product> details,TextField search,CatalogState state,Consumer<String> toast,Runnable navSync) {
        this(user,cart,products,carts,changed,openCart,details,search,state,toast,navSync,new GeminiAssistant(),new AssistantPreferences());
    }

    public CatalogView(User user,Cart cart,ProductService products,CartService carts,Runnable changed,Runnable openCart,
                       Consumer<Product> details,TextField search,CatalogState state,Consumer<String> toast,Runnable navSync,
                       GeminiAssistant gemini,AssistantPreferences preferences) {
        this.user=user;this.cart=cart;this.products=products;this.carts=carts;this.changed=changed;
        this.details=details;this.search=search;this.state=state;this.toast=toast;this.navSync=navSync;
        this.gemini=gemini;this.preferences=preferences;
        getStyleClass().add("page");setSpacing(16);
        category.getItems().add("All categories");for(Category c:Category.shopping())category.getItems().add(Ui.category(c));
        category.getSelectionModel().select(state.getCategoryIndex());category.setId("category");category.setAccessibleText("Filter by category");
        sort.getItems().addAll("Sort by: Name A–Z","Sort by: Price low–high","Sort by: Price high–low","Sort by: Name Z–A");sort.getSelectionModel().select(state.getSortIndex());
        sort.setId("sort");sort.setAccessibleText("Sort products");
        budget.getItems().addAll("Any price","Up to $25","Up to $50","Up to $100");budget.getSelectionModel().select(state.getBudgetIndex());
        budget.setId("budget");budget.setAccessibleText("Maximum price");available.setId("in-stock");available.setSelected(state.isInStockOnly());
        category.setOnAction(e->filterChanged());sort.setOnAction(e->filterChanged());budget.setOnAction(e->{if(clearing)return;minimum.clear();maximum.clear();applyFilters();});available.setOnAction(e->filterChanged());
        grid.setId("product-grid");
        grid.widthProperty().addListener((o,a,b)->{
            int columns=Math.max(1,Math.min(5,(int)((b.doubleValue()+20)/230)));
            grid.setPrefTileWidth(Math.max(180,Math.floor((b.doubleValue()-20*(columns-1))/columns)-2));
        });

        VBox collectionTitle=new VBox(4,title,Ui.label("Everything to forge your setup","muted"),result);
        collectionTitle.setMinWidth(0);
        Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        search.setPromptText("Search products…");
        search.setMinWidth(0);search.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(search,Priority.ALWAYS);
        if(!search.getStyleClass().contains("catalog-search-input"))search.getStyleClass().add("catalog-search-input");
        Button clearSearch=Ui.button("",()->{search.clear();state.setScrollPosition(0);refresh();search.requestFocus();});
        clearSearch.setGraphic(StoreIcon.of("close"));clearSearch.setTooltip(new javafx.scene.control.Tooltip("Clear search"));
        clearSearch.setId("clear-search");clearSearch.setAccessibleText("Clear search");
        clearSearch.getStyleClass().add("search-clear");
        clearSearch.visibleProperty().bind(search.textProperty().isNotEmpty());clearSearch.managedProperty().bind(clearSearch.visibleProperty());
        HBox searchInput=new HBox(12,StoreIcon.of("search"),search,clearSearch);searchInput.setAlignment(Pos.CENTER_LEFT);searchInput.setMinWidth(0);
        searchInput.getStyleClass().add("catalog-search-surface");HBox.setHgrow(searchInput,Priority.ALWAYS);
        Button find=Ui.primary("Search",()->{state.setScrollPosition(0);refresh();});find.setId("catalog-search");
        find.getStyleClass().add("purchase-action");find.setVisible(false);find.setManaged(false);
        search.setOnAction(e->find.fire());
        suggestions=new SearchSuggestions(search,user,products,details,()->{state.setScrollPosition(0);refresh();});
        find.setOnAction(e->{suggestions.dismiss();state.setScrollPosition(0);refresh();});
        HBox searchRow=new HBox(10,searchInput,find);searchRow.setAlignment(Pos.CENTER_LEFT);searchRow.setId("catalog-search-row");
        searchBar=searchRow;searchBar.setMinWidth(0);
        find.setAccessibleText("Search products");
        toggle.setId("toggle-filters");
        minimum.setText(state.getMinimumPrice());maximum.setText(state.getMaximumPrice());minimum.setPromptText("0.00");maximum.setPromptText("Any");minimum.setId("minimum-price");maximum.setId("maximum-price");minimum.setPrefWidth(100);maximum.setPrefWidth(100);minimum.setAccessibleText("Minimum price in USD");maximum.setAccessibleText("Maximum price in USD");
        minimum.textProperty().addListener((o,a,b)->Ui.flag(minimum,false));maximum.textProperty().addListener((o,a,b)->Ui.flag(maximum,false));
        rangeApplied.set(!minimum.getText().isBlank()||!maximum.getText().isBlank());
        filters.setVisible(false);
        syncToggle();
        toggle.setOnAction(e->openFilters());
        toggle.getStyleClass().add("catalog-outline-control");
        sort.getStyleClass().add("catalog-outline-control");
        sort.setButtonCell(new javafx.scene.control.ListCell<>(){ @Override protected void updateItem(String item,boolean empty){super.updateItem(item,empty);setText(empty?null:"Sort by");} });
        sort.setPrefWidth(105);
        imageSearch=Ui.button("Find by image",this::chooseImage);imageSearch.setId("catalog-image-search");imageSearch.setAccessibleText("Find visually similar products using an image search");imageSearch.getStyleClass().addAll("catalog-outline-control","catalog-image-search");
        HBox tools=new HBox(12,imageSearch,toggle,sort);tools.setAlignment(Pos.BOTTOM_RIGHT);
        HBox toolbar=new HBox(20,collectionTitle,spacer,tools);toolbar.setAlignment(Pos.BOTTOM_LEFT);
        toolbar.getStyleClass().add("catalog-toolbar");
        chips.setId("filter-chips");chips.managedProperty().bind(chips.visibleProperty());
        results.getChildren().add(grid);

        VBox collection=new VBox(16,toolbar,chips,feedback,results);collectionSection=collection;collection.getStyleClass().add("collection-section");
        VBox content=new VBox(0);
        hero=new EditorialHeader(()->{
            content.applyCss();content.layout();
            double range=content.getHeight()-scrollViewportHeight();
            scrollTo(range<=0?0:collection.getBoundsInParent().getMinY()/range);
        },()->browseCategory(Category.PERIPHERALS.shoppingIndex()),()->{
            clearing=true;category.getSelectionModel().select(Category.PERIPHERALS.shoppingIndex());search.setText("headphones");
            clearing=false;filterChanged();
        });
        hero.visibleProperty().bind(search.textProperty().isEmpty().and(category.getSelectionModel().selectedIndexProperty().isEqualTo(0))
            .and(budget.getSelectionModel().selectedIndexProperty().isEqualTo(0)).and(available.selectedProperty().not()).and(rangeApplied.not()));
        hero.managedProperty().bind(hero.visibleProperty());
        // The navigation shares the campaign canvas; it stays reachable when filters hide the campaign.
        navigation=categoryNavigation();
        CategoryBanner categoryBanner=new CategoryBanner();
        Runnable updateCategoryBanner=()->{int index=category.getSelectionModel().getSelectedIndex();if(index>0)categoryBanner.showCategory(Category.shopping().get(index-1));};
        category.getSelectionModel().selectedIndexProperty().addListener((o,a,b)->updateCategoryBanner.run());updateCategoryBanner.run();
        categoryBanner.visibleProperty().bind(search.textProperty().isEmpty().and(category.getSelectionModel().selectedIndexProperty().greaterThan(0))
            .and(budget.getSelectionModel().selectedIndexProperty().isEqualTo(0)).and(available.selectedProperty().not()).and(rangeApplied.not()));
        categoryBanner.managedProperty().bind(categoryBanner.visibleProperty());
        campaign=new javafx.scene.layout.StackPane(hero,categoryBanner,navigation);
        campaign.setId("catalog-campaign");campaign.getStyleClass().add("catalog-campaign");
        campaign.setViewOrder(-1); // Draw the headset overlap above the following white section.
        javafx.scene.layout.StackPane.setAlignment(navigation,Pos.TOP_CENTER);
        navigation.setMaxHeight(Region.USE_PREF_SIZE);
        VBox discovery=collections(),end=closing();
        var unfilteredShopAll=search.textProperty().isEmpty()
            .and(category.getSelectionModel().selectedIndexProperty().isEqualTo(0))
            .and(budget.getSelectionModel().selectedIndexProperty().isEqualTo(0))
            .and(available.selectedProperty().not()).and(rangeApplied.not());
        discovery.visibleProperty().bind(unfilteredShopAll);discovery.managedProperty().bind(discovery.visibleProperty());
        end.visibleProperty().bind(unfilteredShopAll);end.managedProperty().bind(end.visibleProperty());
        content.getChildren().addAll(campaign,collection,discovery,end);
        scroll=Ui.scroll(content);scroll.setId("catalog-scroll");getStyleClass().add("catalog-page");getChildren().add(scroll);
        scroll.vvalueProperty().addListener((o,a,b)->{if(!restoring)state.setScrollPosition(b.doubleValue());updateHeroViewport();});
        scroll.viewportBoundsProperty().addListener((o,a,b)->updateHeroViewport());
        content.layoutBoundsProperty().addListener((o,a,b)->updateHeroViewport());
        Platform.runLater(this::refresh);
    }
    private HBox categoryNavigation() {
        HBox row=new HBox(6);row.setId("tech-category-nav");row.setAlignment(Pos.CENTER);
        row.getStyleClass().add("hero-category-nav");
        for(int index=0;index<=Category.shopping().size();index++) {
            final int selection=index;
            Button tab=Ui.button(index==0?"Shop all":Ui.category(Category.shopping().get(index-1)),()->browseCategory(selection));
            tab.setId("hero-category-"+index);
            Runnable highlight=()->{tab.getStyleClass().remove("category-active");if(category.getSelectionModel().getSelectedIndex()==selection)tab.getStyleClass().add("category-active");};
            category.getSelectionModel().selectedIndexProperty().addListener((o,a,b)->highlight.run());highlight.run();
            if(index>3){tab.visibleProperty().bind(widthProperty().greaterThanOrEqualTo(1200));tab.managedProperty().bind(tab.visibleProperty());}
            row.getChildren().add(tab);
        }
        javafx.scene.control.MenuButton more=new javafx.scene.control.MenuButton("More");more.setId("hero-category-more");
        for(int index=4;index<=Category.shopping().size();index++) {
            final int selection=index;
            javafx.scene.control.MenuItem item=new javafx.scene.control.MenuItem(Ui.category(Category.shopping().get(index-1)));
            item.setOnAction(e->browseCategory(selection));more.getItems().add(item);
        }
        Runnable markMore=()->{more.getStyleClass().remove("category-active");if(category.getSelectionModel().getSelectedIndex()>3)more.getStyleClass().add("category-active");};
        category.getSelectionModel().selectedIndexProperty().addListener((o,a,b)->markMore.run());markMore.run();
        more.visibleProperty().bind(widthProperty().lessThan(1200));more.managedProperty().bind(more.visibleProperty());row.getChildren().add(more);
        return row;
    }
    private static VBox labeled(String title,javafx.scene.control.Control input) {
        Label label=Ui.label(title,"field-label");label.setLabelFor(input);
        VBox field=new VBox(7,label,input);field.setMinWidth(0);return field;
    }
    private void syncToggle() {
        int count=(state.getCategoryIndex()>0?1:0)+(state.getBudgetIndex()>0?1:0)+(state.isInStockOnly()?1:0)
            +(!state.getMinimumPrice().isBlank()||!state.getMaximumPrice().isBlank()?1:0);
        toggle.setText("Filters"+(count>0?" · "+count:"")+(filters.isVisible()?"  ⌃":"  ⌄"));
        toggle.setAccessibleText((filters.isVisible()?"Hide":"Show")+" product filters; "+count+" active");
    }
    private void applyFilters() { state.setScrollPosition(0);refresh(); }
    private void openFilters() {
        suggestions.dismiss();
        Ui.async(this,"Loading filters…",()->products.search(user,"",null), rows->{
            double ceiling=rows.stream().map(Product::getPrice).max(BigDecimal::compareTo).orElse(BigDecimal.ONE).doubleValue();
            String query=search.getText().trim().toLowerCase(java.util.Locale.ROOT);
            List<Product> matches=rows.stream().filter(p->p.getName().toLowerCase(java.util.Locale.ROOT).contains(query)).toList();
            FilterDrawer.show(this,state,ceiling,matches,draft->{
                clearing=true;
                category.getSelectionModel().select(draft.category());available.setSelected(draft.inStock());
                budget.getSelectionModel().selectFirst();minimum.setText(draft.minimum());maximum.setText(draft.maximum());
                clearing=false;filterChanged();
            });
        },error->Ui.failure(feedback,error));
    }
    private boolean validRange() {
        boolean valid=true;BigDecimal lower=null,upper=null;
        try{lower=parseRange(minimum.getText());Ui.flag(minimum,false);}catch(RuntimeException e){Ui.flag(minimum,true);valid=false;}
        try{upper=parseRange(maximum.getText());Ui.flag(maximum,false);}catch(RuntimeException e){Ui.flag(maximum,true);valid=false;}
        if(valid&&lower!=null&&upper!=null&&lower.compareTo(upper)>0){Ui.flag(minimum,true);Ui.flag(maximum,true);valid=false;}
        if(!valid){Ui.failure(feedback,new edu.course.shop.exception.ValidationException("Enter a valid price range (minimum cannot exceed maximum)."));
            if(minimum.getStyleClass().contains("invalid"))minimum.requestFocus();else maximum.requestFocus();}
        return valid;
    }
    private double scrollViewportHeight() { return scroll.getViewportBounds().getHeight(); }
    private void updateHeroViewport() {
        double height=scrollViewportHeight();
        double top=Math.max(0,scroll.getContent().getLayoutBounds().getHeight()-height)*scroll.getVvalue();
        hero.setInViewport(height>0&&top<hero.getBoundsInParent().getMaxY()&&top+height>hero.getBoundsInParent().getMinY());
    }
    private void scrollTo(double value) { scroll.setVvalue(Math.max(0,Math.min(1,value))); }
    private VBox collections() {
        FlowPane tiles=new FlowPane(18,18);tiles.setId("collection-links");
        for(Category c:Category.shopping()) {
            int i=c.shoppingIndex()-1;
            String subject=switch(c){case PC_SETS->"PC set";case PC_PARTS->"PC parts";case LAPTOPS->"Laptop";case PHONES_TABLETS->"Phone and tablet";case PERIPHERALS->"Mechanical keyboard";case ELECTRONICS->"Desktop speaker";default->throw new IllegalStateException("Retired category");};
            String headline=switch(c){case PC_SETS->"Power your setup.";case PC_PARTS->"Build it your way.";case LAPTOPS->"Take power with you.";case PHONES_TABLETS->"Stay connected.";case PERIPHERALS->"Complete your desk.";case ELECTRONICS->"Discover more tech.";default->throw new IllegalStateException("Retired category");};
            CollectionArtwork art=new CollectionArtwork(subject);art.setPrefSize(110,135);art.setMaxWidth(110);
            VBox copy=new VBox(8,Ui.label("0"+(i+1)+" / "+Ui.category(c).toUpperCase(java.util.Locale.ROOT),"eyebrow"),
                Ui.label(headline,"section-title"),Ui.label("Shop "+Ui.category(c).toLowerCase(java.util.Locale.ROOT)+"  →","collection-link"));
            copy.setMinWidth(0);HBox.setHgrow(copy,Priority.ALWAYS);
            HBox graphic=new HBox(8,copy,art);graphic.setAlignment(Pos.CENTER_LEFT);graphic.getStyleClass().add("collection-tile-content");
            graphic.setMinHeight(0);graphic.setPrefHeight(135);graphic.setMaxHeight(135);
            Button tile=Ui.button("",()->browseCategory(i+1));
            tile.setId("collection-"+c.name().toLowerCase(java.util.Locale.ROOT));tile.setGraphic(graphic);
            tile.setAccessibleText("Shop "+Ui.category(c));tile.getStyleClass().add("collection-tile");
            tile.prefWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(()->{
                int columns=Math.max(1,Math.min(3,(int)((tiles.getWidth()+18)/330)));
                return Math.max(260,Math.floor((tiles.getWidth()-18*(columns-1))/columns));
            },tiles.widthProperty()));graphic.prefWidthProperty().bind(tile.widthProperty().subtract(36));
            tile.setMinHeight(171);tile.setPrefHeight(171);tile.setMaxHeight(171);
            tile.setMinWidth(260);tiles.getChildren().add(tile);
        }
        VBox section=new VBox(22,Ui.label("FIND YOUR NEXT UPGRADE.","collection-title"),Ui.label("From complete setups to the parts that make them yours.","muted"),tiles);
        section.getStyleClass().add("discovery-section");return section;
    }
    private VBox closing() {
        VBox end=new VBox(12,Ui.label("GOOD THINGS.\nEVERY DAY.","closing-title"),
            Ui.label("For the way you work, play, and connect.","closing-sub"));
        end.setAlignment(Pos.CENTER);end.getStyleClass().add("closing-section");return end;
    }
    private void filterChanged() { if(clearing)return;state.setScrollPosition(0);refresh(); }
    private void browseCategory(int index) {
        clearing=true;search.clear();category.getSelectionModel().select(index);clearing=false;filterChanged();
    }
    private void clear() {
        clearing=true;category.getSelectionModel().selectFirst();
        budget.getSelectionModel().selectFirst();minimum.clear();maximum.clear();state.setPriceRange("","");available.setSelected(false);clearing=false;filterChanged();
    }
    private void refresh() {
        if(clearing||(getScene()!=null&&getScene().getRoot().isDisabled()))return;
        String query=search.getText();int index=category.getSelectionModel().getSelectedIndex(),ordering=sort.getSelectionModel().getSelectedIndex(),cap=budget.getSelectionModel().getSelectedIndex();
        Category group=index==0?null:Category.shopping().get(index-1);
        BigDecimal capPrice=cap==0?null:BigDecimal.valueOf(new int[]{0,25,50,100}[cap]);
        if(!validRange())return;
        BigDecimal lower=parseRange(minimum.getText()),upper=parseRange(maximum.getText());
        state.setPriceRange(minimum.getText(),maximum.getText());rangeApplied.set(lower!=null||upper!=null);
        boolean inStock=available.isSelected();double previousScroll=state.getScrollPosition();
        state.setCategoryIndex(index);state.setSortIndex(ordering);state.setBudgetIndex(cap);state.setInStockOnly(inStock);
        syncToggle();
        if(navSync!=null)navSync.run(); // header highlights the category chosen via the filter dropdown too
        javafx.animation.PauseTransition loadingDelay=new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));
        loadingDelay.setOnFinished(e->results.getChildren().setAll(Ui.skeleton()));loadingDelay.play();
        sceneProperty().addListener((o,a,b)->{if(b==null)loadingDelay.stop();});
        Ui.async(this,"Finding products…",()->products.search(user,query,group,inStock,capPrice).stream().filter(p->lower==null||p.getPrice().compareTo(lower)>=0).filter(p->upper==null||p.getPrice().compareTo(upper)<=0).toList(),rows->{
            loadingDelay.stop();List<Product> sorted=new ArrayList<>(rows);
            sorted.sort(Comparator.comparing(Product::getName,String.CASE_INSENSITIVE_ORDER).thenComparingLong(Product::getId));
            if(ordering==3)sorted.sort(Comparator.comparing(Product::getName,String.CASE_INSENSITIVE_ORDER).reversed().thenComparingLong(Product::getId));
            if(ordering==1)sorted.sort(Comparator.comparing(Product::getPrice));
            if(ordering==2)sorted.sort(Comparator.comparing(Product::getPrice).reversed());
            restoring=true;grid.getChildren().clear();
            for(Product p:sorted){ProductCard card=new ProductCard(p,()->add(p),()->details.accept(p));if(saved!=null)card.enableSavedItems(saved);grid.getChildren().add(card);}
            title.setText(!query.isBlank()?"Search results":index==0?"The tech edit":Ui.category(group));
            result.setText(sorted.size()+(sorted.size()==1?" item":" items")+" · USD");feedback.setText("");
            results.getChildren().setAll(sorted.isEmpty()?Ui.empty("No products found","Try a different search or remove a filter.","Clear filters",this::clear):grid);
            chips.getChildren().clear();
            if(!query.isBlank())chip("Search: "+query,()->{search.clear();filterChanged();});
            if(index>0)chip(Ui.category(group),()->category.getSelectionModel().selectFirst());
            if(cap>0)chip(budget.getValue(),()->budget.getSelectionModel().selectFirst());
            if(lower!=null||upper!=null)chip("Price: $"+(lower==null?"0":lower)+"–"+(upper==null?"any":"$"+upper),()->{minimum.clear();maximum.clear();filterChanged();});
            if(inStock)chip("In stock",()->{available.setSelected(false);filterChanged();});
            if(!chips.getChildren().isEmpty()) {
                Button clear=Ui.link("Clear filters",this::clear);clear.setId("clear-active-filters");chips.getChildren().add(clear);
            }
            chips.setVisible(!chips.getChildren().isEmpty());
            Platform.runLater(()->{scroll.setVvalue(previousScroll);restoring=false;});
        },error->{loadingDelay.stop();results.getChildren().setAll(Ui.empty("Could not load products","Your search is kept. Try again.","Retry",this::refresh));Ui.failure(feedback,error);});
    }
    private void chooseImage() {
        if (getScene()==null || getScene().getWindow()==null) return;
        if(imageSearchTask!=null)return;
        if(!gemini.configurationProblem().isBlank()){Ui.failure(feedback,new IllegalStateException(gemini.configurationProblem()));return;}
        FileChooser chooser=new FileChooser();chooser.setTitle("Find similar products from an image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images (PNG, JPEG)","*.png","*.jpg","*.jpeg"));
        java.io.File file=chooser.showOpenDialog(getScene().getWindow());
        if(file==null)return;
        if(!preferences.hasGeminiImageConsent(user.getId())&&!confirmImageSharing())return;
        suggestions.dismiss();clearing=true;search.clear();category.getSelectionModel().selectFirst();budget.getSelectionModel().selectFirst();available.setSelected(false);minimum.clear();maximum.clear();state.setPriceRange("","");rangeApplied.set(false);clearing=false;state.setCategoryIndex(0);state.setBudgetIndex(0);state.setInStockOnly(false);syncToggle();if(navSync!=null)navSync.run();
        title.setText("Finding visual matches");result.setText("Comparing your image with the catalog…");feedback.setText("");chips.getChildren().clear();chips.setVisible(false);
        results.setMinHeight(230);results.getChildren().setAll(imageSearchProgress());imageSearch.setDisable(true);
        Task<ImageSearchData> task=new Task<>() {
            @Override protected ImageSearchData call() {
            byte[] image=edu.course.shop.service.ProductPhotos.read(file.toPath());
            List<Product> catalog=products.search(user,"",null);
            return new ImageSearchData(catalog,gemini.searchByImage(image,catalog));
            }
        };
        imageSearchTask=task;
        task.setOnSucceeded(event->{
            imageSearchTask=null;imageSearch.setDisable(false);results.setMinHeight(0);
            ImageSearchData data=task.getValue();
            state.setScrollPosition(0);grid.getChildren().clear();
            List<Product> matches=data.response().matches().stream().map(match->data.catalog().stream().filter(p->p.getId()==match.productId()).findFirst().orElse(null)).filter(java.util.Objects::nonNull).toList();
            for(Product product:matches){ProductCard card=new ProductCard(product,()->add(product),()->details.accept(product));if(saved!=null)card.enableSavedItems(saved);grid.getChildren().add(card);}
            title.setText("Visual matches for "+data.response().identifiedItem());result.setText(matches.size()+(matches.size()==1?" close match":" close matches")+" · compared with Gemini Vision");
            Button imageChip=Ui.button("Image search · sent to Google  ×",this::refresh);imageChip.getStyleClass().add("filter-chip");chips.getChildren().setAll(imageChip);chips.setVisible(true);
            feedback.setText("");results.getChildren().setAll(matches.isEmpty()?Ui.empty("No close catalog matches","Gemini recognized "+data.response().identifiedItem()+" but did not find a sufficiently similar listed product.","Show all products",this::refresh):grid);
            Platform.runLater(this::scrollToCollection);
        });
        task.setOnFailed(event->{
            imageSearchTask=null;imageSearch.setDisable(false);results.setMinHeight(0);
            Ui.failure(feedback,new edu.course.shop.exception.ValidationException(imageSearchFailure(task.getException())));
            title.setText("Image search failed");
            result.setText("Try again or choose another photo.");
            results.getChildren().setAll(Ui.empty("Image search couldn't finish","Check the error above. You can retry or search with a different photo.","Choose another image",this::chooseImage));
            Platform.runLater(this::scrollToCollection);
        });
        Thread worker=new Thread(task,"forge-image-search");worker.setDaemon(true);worker.start();
    }
    private static String imageSearchFailure(Throwable error) {
        Throwable cause=error;
        while(cause!=null&&cause.getCause()!=null&&cause.getCause()!=cause)cause=cause.getCause();
        String message=cause==null?"":cause.getMessage();
        if(message!=null&&!message.isBlank()&&(cause instanceof IllegalStateException||cause instanceof IllegalArgumentException||cause instanceof edu.course.shop.exception.ValidationException)) {
            message=message.replaceAll("[\\r\\n\\t]+"," ").trim();
            return message.length()>280?message.substring(0,277).strip()+"…":message;
        }
        return "Visual search couldn't complete. Check your internet connection, Gemini model access and free-tier quota, then try again.";
    }
    private VBox imageSearchProgress() {
        ProgressIndicator spinner=new ProgressIndicator();spinner.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);spinner.setPrefSize(48,48);spinner.setMaxSize(48,48);spinner.setAccessibleText("Searching product images");spinner.setStyle("-fx-accent: #ef502b;");
        Label message=Ui.label("Comparing your image with FORGE products…","muted");message.setAccessibleText("Image search is in progress");
        VBox loading=new VBox(12,spinner,message);loading.setAlignment(Pos.CENTER);loading.setMinHeight(210);loading.getStyleClass().add("image-search-loading");return loading;
    }
    private void scrollToCollection() {
        if(scroll.getContent()==null)return;
        scroll.getContent().applyCss();if(scroll.getContent() instanceof javafx.scene.Parent parent)parent.layout();
        double range=scroll.getContent().getLayoutBounds().getHeight()-scroll.getViewportBounds().getHeight();
        scrollTo(range<=0?0:collectionSection.getBoundsInParent().getMinY()/range);
    }
    private boolean confirmImageSharing() {
        javafx.scene.control.CheckBox agree=new javafx.scene.control.CheckBox("I agree to send the selected image and FORGE catalog product photos to Google for visual matching.");
        agree.setWrapText(true);agree.setId("image-search-google-consent");
        javafx.scene.control.Alert dialog=new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        ButtonType send=new ButtonType("Agree and search",javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.setTitle("Image search privacy");dialog.setHeaderText("Compare images with Google Gemini?");
        double dialogWidth=Math.min(560,Math.max(400,getScene().getWindow().getWidth()-48));
        Label privacy=Ui.label("Gemini will receive the image you choose plus product photos, names, categories and specifications from FORGE. Google may use free-tier content to improve its products. Your photo is not saved by FORGE. This approval is stored on this device for this account and can be cleared from Assistant → Privacy.","muted");
        privacy.setWrapText(true);privacy.setPrefWidth(dialogWidth-82);privacy.setMaxWidth(dialogWidth-82);
        agree.setPrefWidth(dialogWidth-82);agree.setMaxWidth(dialogWidth-82);
        VBox content=new VBox(14,privacy,agree);content.setPrefWidth(dialogWidth-56);content.setMaxWidth(dialogWidth-56);
        dialog.initOwner(getScene().getWindow());dialog.setResizable(false);
        dialog.getDialogPane().setPrefWidth(dialogWidth);dialog.getDialogPane().setMinWidth(dialogWidth);dialog.getDialogPane().setMaxWidth(dialogWidth);
        dialog.getDialogPane().setContent(content);dialog.getButtonTypes().setAll(send,ButtonType.CANCEL);
        dialog.setOnShown(event->{javafx.scene.Node button=dialog.getDialogPane().lookupButton(send);button.disableProperty().bind(agree.selectedProperty().not());});
        if(dialog.showAndWait().orElse(ButtonType.CANCEL)!=send)return false;
        try{preferences.rememberGeminiImageConsent(user.getId());return true;}
        catch(RuntimeException error){Ui.failure(feedback,error);return false;}
    }
    private void chip(String text,Runnable remove) { Button b=Ui.button(text+"  ×",remove);b.getStyleClass().add("filter-chip");b.setAccessibleText("Remove "+text+" filter");chips.getChildren().add(b); }
    private void add(Product product) {
        Ui.async(this,"Adding to cart…",()->{carts.add(user,cart,product.getId(),1);return true;},done->{
            changed.run();
            if(grid.lookup("#product-"+product.getId()) instanceof ProductCard card)card.confirmAdded();
            // A floating toast is visible wherever the user has scrolled; the inline label can be off-screen.
            if(toast!=null)toast.accept("✓ "+product.getName()+" added to cart");
            else Ui.success(feedback,"✓ "+product.getName()+" added. Open your cart whenever you're ready.");
        },error->Ui.failure(feedback,error));
    }
    private static BigDecimal parseRange(String value) {
        if(value.isBlank())return null;BigDecimal price=new BigDecimal(value.trim());
        if(price.signum()<0||price.scale()>2||price.compareTo(new BigDecimal("1000000"))>0)throw new IllegalArgumentException();return price;
    }
}
