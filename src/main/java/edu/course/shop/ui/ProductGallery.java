package edu.course.shop.ui;

import edu.course.shop.model.Product;
import edu.course.shop.model.Money;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.ArrayList;
import java.util.List;

/** Only real uploaded angles are offered; illustrations remain explicitly labeled. */
public final class ProductGallery extends VBox {
    private final Product product;
    private final Runnable addToCart;
    private final List<byte[]> photos=new ArrayList<>();
    private final List<Button> thumbnails=new ArrayList<>();
    private final Button main;
    private final Label caption=Ui.label("","muted");
    private int selected;
    private final javafx.beans.property.BooleanProperty compact=new javafx.beans.property.SimpleBooleanProperty();
    private final javafx.beans.property.DoubleProperty windowHeight=new javafx.beans.property.SimpleDoubleProperty(860);
    public void setCompact(boolean value){compact.set(value);}
    public ProductGallery(Product product) { this(product,null); }
    public ProductGallery(Product product,Runnable addToCart) {
        this.product=product;this.addToCart=addToCart;
        sceneProperty().addListener((o,a,b)->{windowHeight.unbind();if(b!=null)windowHeight.bind(b.heightProperty());});
        setId("product-gallery");setSpacing(14);setMinWidth(0);
        if(product.getPhoto()!=null)photos.add(product.getPhoto());photos.addAll(product.getAdditionalPhotos());
        main=Ui.button("",this::enlarge);main.setId("zoom-photo");main.getStyleClass().add("photo-button");
        main.setAccessibleText("Enlarge photo of "+product.getName());main.setMaxWidth(Double.MAX_VALUE);
        FlowPane strip=new FlowPane(10,10);strip.setId("gallery-thumbnails");
        for(int i=0;i<photos.size();i++) {
            int index=i;
            ProductArtwork preview=new ProductArtwork(photos.get(i),product.getName(),64);preview.setPrefWidth(76);
            Button thumb=Ui.button("",()->select(index));thumb.setGraphic(preview);
            thumb.setId("gallery-photo-"+i);thumb.setAccessibleText("Show photo "+(i+1)+" of "+product.getName());
            thumb.getStyleClass().add("gallery-thumbnail");thumbnails.add(thumb);strip.getChildren().add(thumb);
        }
        strip.setVisible(photos.size()>1);strip.managedProperty().bind(strip.visibleProperty());
        getChildren().addAll(main,strip,caption);select(0);
    }
    private void select(int index) {
        selected=index;
        ProductArtwork art=photos.isEmpty()?new ProductArtwork(product,520):
            new ProductArtwork(photos.get(index),product.getName(),520,false,edu.course.shop.model.PhotoFrame.AUTO,true);
        art.setId("gallery-main");art.getStyleClass().add(product.getCategory().name().toLowerCase(java.util.Locale.ROOT));
        var height=javafx.beans.binding.Bindings.createDoubleBinding(()->{
            if(photos.isEmpty())return compact.get()?Math.min(280,Math.max(210,windowHeight.get()*.32)):Math.min(390,Math.max(280,getWidth()*.58));
            return compact.get()?Math.min(360,Math.max(240,windowHeight.get()*.42)):Math.min(560,Math.max(320,getWidth()*.9));
        },widthProperty(),compact,windowHeight);
        art.minHeightProperty().bind(height);art.prefHeightProperty().bind(height);art.maxHeightProperty().bind(height);
        // Full photograph stays visible; the catalog crop is not applied to the detail gallery.
        if(art.lookup(".product-photo") instanceof javafx.scene.image.ImageView image)image.fitHeightProperty().bind(height.subtract(32));
        art.prefWidthProperty().bind(main.widthProperty().subtract(4));main.setGraphic(art);Motion.reveal(art);
        for(int i=0;i<thumbnails.size();i++) {
            thumbnails.get(i).getStyleClass().remove("gallery-selected");
            if(i==index)thumbnails.get(i).getStyleClass().add("gallery-selected");
        }
        caption.setText(photos.isEmpty()?(CollectionArtwork.supports(product.getName())?"Product illustration · Actual product may vary.":"Photo not available yet."):
            "Photo "+(index+1)+" of "+photos.size()+" · Select image to enlarge");
    }
    private void enlarge() {
        DialogPane panel=new DialogPane();panel.setId("image-viewer-panel");
        panel.getStylesheets().add(ProductGallery.class.getResource("/shop.css").toExternalForm());
        panel.getStyleClass().addAll("image-viewer","glass-image-viewer");
        double sceneWidth=getScene()==null?1280:getScene().getWidth(),sceneHeight=getScene()==null?800:getScene().getHeight();
        double panelWidth=Math.min(1180,sceneWidth*.88),panelHeight=Math.min(800,sceneHeight*.76);
        panel.setMinSize(0,0);panel.prefWidthProperty().bind(javafx.beans.binding.Bindings.min(1180,getScene().widthProperty().multiply(.88)));panel.prefHeightProperty().bind(javafx.beans.binding.Bindings.min(800,getScene().heightProperty().multiply(.76)));panel.maxWidthProperty().bind(panel.prefWidthProperty());panel.maxHeightProperty().bind(panel.prefHeightProperty());
        StackPane surface=new StackPane();surface.setMinSize(0,0);surface.getStyleClass().add("image-viewer-surface");
        ScrollPane viewport=new ScrollPane(surface);viewport.setPannable(true);viewport.getStyleClass().add("image-viewer-viewport");viewport.setId("image-zoom-viewport");
        javafx.beans.binding.NumberBinding mediaWidth=javafx.beans.binding.Bindings.max(280,panel.prefWidthProperty().multiply(.58).subtract(42));
        viewport.prefWidthProperty().bind(mediaWidth);viewport.maxWidthProperty().bind(mediaWidth);viewport.prefViewportWidthProperty().bind(mediaWidth.subtract(4));viewport.prefHeightProperty().bind(panel.prefHeightProperty().subtract(172));viewport.minHeightProperty().bind(viewport.prefHeightProperty());viewport.maxHeightProperty().bind(viewport.prefHeightProperty());
        // Fixed content geometry breaks the viewport / scrollbar / preferred-size feedback loop.
        viewport.setFitToWidth(false);viewport.setFitToHeight(false);
        javafx.beans.property.DoubleProperty scale=new javafx.beans.property.SimpleDoubleProperty(1);
        Label caption=Ui.label("","muted");caption.setId("image-zoom-caption");
        int[] current={selected};double baseWidth=Math.max(220,panelWidth*.58-66),baseHeight=Math.max(200,panelHeight-316);
        javafx.scene.image.ImageView image=new javafx.scene.image.ImageView();image.setPreserveRatio(true);image.setSmooth(true);image.setId("image-zoom-photo");
        image.fitWidthProperty().bind(scale.multiply(mediaWidth.subtract(26)));image.fitHeightProperty().bind(scale.multiply(panel.prefHeightProperty().subtract(198)));
        surface.prefWidthProperty().bind(scale.multiply(mediaWidth.subtract(2)));surface.prefHeightProperty().bind(scale.multiply(panel.prefHeightProperty().subtract(174)));
        surface.setMinSize(Region.USE_PREF_SIZE,Region.USE_PREF_SIZE);surface.setMaxSize(Region.USE_PREF_SIZE,Region.USE_PREF_SIZE);
        viewport.setMinWidth(0);
        Runnable render=()->{
            scale.set(1);viewport.setHvalue(.5);viewport.setVvalue(.5);
            if(photos.isEmpty())surface.getChildren().setAll(new ProductArtwork(product,baseHeight));
            else {image.setImage(new javafx.scene.image.Image(new java.io.ByteArrayInputStream(photos.get(current[0]))));image.setAccessibleText("Photo "+(current[0]+1)+" of "+product.getName());surface.getChildren().setAll(image);}
            caption.setText(photos.isEmpty()?"Illustration / photo unavailable":"Photo "+(current[0]+1)+" of "+photos.size()+" · Drag to pan when zoomed");
        };
        Button previous=Ui.button("←",()->{current[0]=Math.floorMod(current[0]-1,photos.size());render.run();}),next=Ui.button("→",()->{current[0]=(current[0]+1)%photos.size();render.run();});
        previous.setId("image-previous");next.setId("image-next");previous.setAccessibleText("Previous photo");next.setAccessibleText("Next photo");previous.setDisable(photos.size()<2);next.setDisable(photos.size()<2);
        Button minus=Ui.button("−",()->scale.set(Math.max(1,scale.get()-.5))),plus=Ui.button("+",()->scale.set(Math.min(4,scale.get()+.5)));
        minus.setId("image-zoom-out");plus.setId("image-zoom-in");minus.setAccessibleText("Zoom out");plus.setAccessibleText("Zoom in");minus.disableProperty().bind(scale.lessThanOrEqualTo(1));plus.disableProperty().bind(scale.greaterThanOrEqualTo(4));
        if(photos.isEmpty()){minus.disableProperty().unbind();plus.disableProperty().unbind();minus.setDisable(true);plus.setDisable(true);}
        Button reset=Ui.button("Fit image",()->{scale.set(1);viewport.setHvalue(.5);viewport.setVvalue(.5);});reset.setId("image-zoom-reset");Label level=Ui.label("","field-label");level.textProperty().bind(javafx.beans.binding.Bindings.when(scale.lessThanOrEqualTo(1)).then("Fit").otherwise(scale.multiply(100).asString("%.0f%%")));
        level.setId("image-zoom-level");level.setAlignment(javafx.geometry.Pos.CENTER);
        HBox zoomControls=new HBox(10,minus,level,plus,reset);zoomControls.setAlignment(javafx.geometry.Pos.CENTER);zoomControls.setId("image-zoom-controls");zoomControls.getStyleClass().add("image-zoom-controls");
        BorderPane tools=new BorderPane();tools.setLeft(previous);tools.setCenter(zoomControls);tools.setRight(next);tools.setId("image-viewer-tools");tools.setPrefWidth(mediaWidth.doubleValue());tools.setMaxWidth(mediaWidth.doubleValue());tools.getStyleClass().add("image-viewer-tools");render.run();
        VBox media=new VBox(10,viewport,tools,caption);media.getStyleClass().add("viewer-media");media.setMinWidth(0);media.setPrefWidth(mediaWidth.doubleValue());media.setMaxWidth(mediaWidth.doubleValue());media.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        media.prefWidthProperty().bind(mediaWidth);media.maxWidthProperty().bind(mediaWidth);tools.prefWidthProperty().bind(mediaWidth);tools.maxWidthProperty().bind(mediaWidth);caption.setMaxWidth(Double.MAX_VALUE);caption.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label heading=Ui.label("Photo viewer / "+product.getName(),"viewer-title");heading.setMinWidth(0);heading.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(heading,Priority.ALWAYS);
        Button close=Ui.button("×",()->{});close.setId("image-close");close.setAccessibleText("Close image viewer");
        Label category=Ui.label(Ui.category(product.getCategory()).toUpperCase(java.util.Locale.ROOT)+" / PRODUCT DETAIL","viewer-category");
        Label productName=Ui.label(product.getName(),"viewer-product-name");productName.setWrapText(true);
        String description=product.getDescription().isBlank()?"A closer look at this item from the FORGE collection.":product.getDescription();
        Label productDescription=Ui.label(description,"viewer-description");productDescription.setWrapText(true);
        Label price=Ui.label(Money.format(product.getPrice()),"viewer-product-price");
        Label availability=Ui.label(product.getStock()==0?"Currently unavailable":product.getStock()<=5?"Only "+product.getStock()+" left in stock":"In stock · ready to add",product.getStock()==0?"stock-out":"viewer-stock");
        VBox details=new VBox(18,category,productName,productDescription,price,availability);details.getStyleClass().add("viewer-info");details.setMinWidth(230);details.prefWidthProperty().bind(javafx.beans.binding.Bindings.min(360,panel.prefWidthProperty().multiply(.34).subtract(24)));details.setMaxWidth(360);details.setMaxHeight(Region.USE_PREF_SIZE);
        Button add=null;
        if(addToCart!=null){add=Ui.primary("Add to cart",()->{});add.setId("image-viewer-add");add.getStyleClass().add("viewer-add");add.setMaxWidth(Double.MAX_VALUE);add.setDisable(!product.isActive()||product.getStock()==0);}
        for(javafx.scene.Node child:details.getChildren())if(child instanceof Label label){label.setWrapText(true);label.setMinHeight(Region.USE_PREF_SIZE);label.setMaxWidth(Double.MAX_VALUE);}
        // Scroll the descriptive text only; purchase controls remain visible.
        VBox detailText=new VBox(12,category,productName,productDescription);
        ScrollPane detailScroll=new ScrollPane(detailText);detailScroll.setFitToWidth(true);detailScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);detailScroll.setMinSize(0,0);detailScroll.getStyleClass().add("viewer-info-scroll");VBox.setVgrow(detailScroll,Priority.ALWAYS);
        VBox purchase=new VBox(10,price,availability);purchase.setMinHeight(Region.USE_PREF_SIZE);
        if(add!=null)purchase.getChildren().add(add);
        details.getChildren().setAll(detailScroll,purchase);details.setSpacing(16);details.setMinHeight(0);
        details.prefHeightProperty().bind(panel.prefHeightProperty().subtract(104));details.maxHeightProperty().bind(details.prefHeightProperty());
        VBox imageWell=new VBox(0,media);imageWell.getStyleClass().add("viewer-image-well");imageWell.setMinWidth(0);imageWell.prefWidthProperty().bind(mediaWidth);imageWell.maxWidthProperty().bind(mediaWidth);
        HBox columns=new HBox(14,imageWell,details);columns.getStyleClass().add("viewer-columns");columns.setMinHeight(0);columns.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        // Track the centered image column instead of using an unrelated title inset.
        Region titleInset=new Region();titleInset.minWidthProperty().bind(imageWell.layoutXProperty());titleInset.prefWidthProperty().bind(imageWell.layoutXProperty());titleInset.maxWidthProperty().bind(imageWell.layoutXProperty());
        HBox header=new HBox(0,titleInset,heading,close);HBox.setMargin(close,new javafx.geometry.Insets(0,0,0,16));header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox content=new VBox(14,header,columns);content.setMinHeight(0);panel.setContent(content);
        Runnable dismiss=ShopOverlay.show(this,panel,true,false,()->select(current[0]));
        close.setOnAction(e->dismiss.run());
        if(add!=null)add.setOnAction(e->{addToCart.run();dismiss.run();});
        panel.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,e->{if(e.getCode()==javafx.scene.input.KeyCode.LEFT){previous.fire();e.consume();}else if(e.getCode()==javafx.scene.input.KeyCode.RIGHT){next.fire();e.consume();}else if(e.getCode()==javafx.scene.input.KeyCode.ADD||e.getCode()==javafx.scene.input.KeyCode.PLUS||e.getCode()==javafx.scene.input.KeyCode.EQUALS){plus.fire();e.consume();}else if(e.getCode()==javafx.scene.input.KeyCode.SUBTRACT||e.getCode()==javafx.scene.input.KeyCode.MINUS){minus.fire();e.consume();}else if(e.getCode()==javafx.scene.input.KeyCode.DIGIT0){reset.fire();e.consume();}});

    }
}
