package edu.course.shop.ui;
import edu.course.shop.model.*;
import edu.course.shop.service.*;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.function.Consumer;
import java.util.List;

public final class ProductDetailView extends VBox {
    public ProductDetailView(Product product,User user,Cart cart,CartService carts,ProductService products,
                             Runnable changed,Runnable back,Runnable openCart,Consumer<Product> openProduct) {
        this(product,user,cart,carts,products,changed,back,openCart,openProduct,null,null);
    }
    public ProductDetailView(Product product,User user,Cart cart,CartService carts,ProductService products,
                             Runnable changed,Runnable back,Runnable openCart,Consumer<Product> openProduct,SavedItems saved,Consumer<String> toast) {
        this(product,user,cart,carts,products,changed,back,openCart,openProduct,saved,toast,null,null);
    }
    public ProductDetailView(Product product,User user,Cart cart,CartService carts,ProductService products,
                             Runnable changed,Runnable back,Runnable openCart,Consumer<Product> openProduct,SavedItems saved,Consumer<String> toast,
                             ProductReviewService reviews,GeminiAssistant gemini) {
        setId("product-detail");getStyleClass().add("page");setSpacing(18);
        Label feedback=Ui.feedback("detail-feedback");
        QuantityControl quantity=new QuantityControl(product.getStock());quantity.setId("detail-quantity");
        quantity.setAccessibleText("Quantity");quantity.setPrefWidth(150);
        // One dominant purchase action beside the price and stock status.
        Button add=Ui.primary(product.getStock()==0?"Sold out":"Add to cart",()->Ui.async(this,"Adding to cart…",()->{
            carts.add(user,cart,product.getId(),quantity.getValue());return true;
        },done->{changed.run();Ui.success(feedback,"✓ Added to your cart.");if(toast!=null)toast.accept("✓ "+product.getName()+" added to cart");},error->Ui.failure(feedback,error)));
        add.setId("detail-add");add.disableProperty().bind(quantity.validProperty().not().or(new javafx.beans.property.SimpleBooleanProperty(!product.isActive()||product.getStock()==0)));add.setMaxWidth(Double.MAX_VALUE);
        add.getStyleClass().addAll("cta","purchase-action");
        quantity.setDisable(product.getStock()==0);
        Label quantityError=Ui.feedback("detail-quantity-error");quantityError.getStyleClass().add("error-feedback");
        quantityError.textProperty().bind(javafx.beans.binding.Bindings.when(quantity.validProperty()).then("").otherwise("Only "+product.getStock()+" available. Enter a quantity from 1 to "+product.getStock()+"."));
        if(product.getStock()>0)add.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(()->"Add to cart · "+Money.format(product.getPrice().multiply(java.math.BigDecimal.valueOf(quantity.getValue()))),quantity.valueProperty()));
        TitledPane delivery=section("Delivery & payment","Shipping is free in this course demo. Choose cash on delivery or a simulated wallet at checkout. No real payment or delivery is processed.");
        TitledPane stock=section("Availability",product.getStock()+" units currently available. Items in a cart are not reserved; stock is checked again at checkout.");

        boolean soldOut=product.getStock()==0,low=product.getStock()<=5;
        Label availability=Ui.label(soldOut?"Sold out":low?"Only "+product.getStock()+" left":"In stock · "+product.getStock()+" left",soldOut?"stock-out":low?"stock-low":"stock");
        availability.setWrapText(false);availability.setMaxWidth(Region.USE_PREF_SIZE);
        FlowPane priceRow=new FlowPane(12,6,Ui.label(Money.format(product.getPrice()),"total"),availability);priceRow.setAlignment(Pos.CENTER_LEFT);
        HBox quantityRow=new HBox(12,Ui.label("Quantity","field-label"),quantity);quantityRow.setAlignment(Pos.CENTER_LEFT);
        Button viewCart=Ui.link("View cart",openCart);viewCart.setId("detail-view-cart");

        // Reading order: category, name, price + stock, quantity, Add to cart, then the fine print.
        String details=product.getDescription();
        Label preview=Ui.label(details,"product-description");preview.setId("product-description");
        preview.setVisible(!details.isBlank());preview.managedProperty().bind(preview.visibleProperty());
        Region heartSpacer=new Region();HBox.setHgrow(heartSpacer,Priority.ALWAYS);
        HBox heading=new HBox(12,Ui.label(Ui.category(product.getCategory())+" · #"+product.getId(),"eyebrow"),heartSpacer);
        heading.setAlignment(Pos.CENTER_LEFT);if(saved!=null)heading.getChildren().add(saved.heart(product));
        VBox info=new VBox(14,heading,
            Ui.label(product.getName(),"page-title"),priceRow,preview,quantityRow,quantityError,add,feedback,viewCart,specifications(product),delivery,stock);
        info.getStyleClass().add("detail-info");info.setMinWidth(300);info.setPrefWidth(420);info.setMaxWidth(460);

        ProductGallery gallery=new ProductGallery(product,add::fire);HBox.setHgrow(gallery,Priority.ALWAYS);
        info.setMaxWidth(Double.MAX_VALUE);
        VBox columns=Ui.responsiveColumns(gallery,info,1000,32);columns.setId("detail-columns");columns.widthProperty().addListener((o,a,b)->gallery.setCompact(b.doubleValue()<1000));

        // Related products reuse ProductCard, so they look and behave exactly like the storefront grid.
        FlowPane suggestions=new FlowPane(20,20);suggestions.setId("related-products");
        VBox related=new VBox(14,Ui.label("More from this category","section-title"),suggestions);related.setVisible(false);related.managedProperty().bind(related.visibleProperty());
        VBox content=new VBox(32,columns,related);
        if(reviews!=null&&gemini!=null)content.getChildren().add(new ProductReviewsView(product.getId(),product.getName(),user,reviews,gemini));
        Button backButton=Ui.link("← Back to results",back);backButton.setMaxWidth(Region.USE_PREF_SIZE);
        FlowPane navigation=new FlowPane(18,8,backButton,Ui.label("Shop / "+Ui.category(product.getCategory()),"breadcrumb"));navigation.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(navigation,Ui.scroll(content));
        Platform.runLater(()->Ui.readOnly(this,()->products.search(user,"",product.getCategory()),rows->{
            String brand=ProductSpecifications.value(product,"Brand"),model=ProductSpecifications.value(product,"Model");
            List<Product> variants=brand.isBlank()||model.isBlank()?List.of():rows.stream().filter(p->brand.equalsIgnoreCase(ProductSpecifications.value(p,"Brand"))&&model.equalsIgnoreCase(ProductSpecifications.value(p,"Model"))).toList();
            if(variants.size()>1){
                ComboBox<Product> configurations=new ComboBox<>(javafx.collections.FXCollections.observableArrayList(variants));configurations.setId("product-configurations");configurations.setMaxWidth(Double.MAX_VALUE);configurations.setAccessibleText("Choose product configuration");
                configurations.setConverter(new javafx.util.StringConverter<>(){
                    public String toString(Product p){if(p==null)return "";String variant=ProductSpecifications.value(p,"Variant");return (variant.isBlank()?p.getName():variant)+" · "+Money.format(p.getPrice())+(p.getStock()==0?" · Sold out":"");}
                    public Product fromString(String text){return null;}
                });configurations.setValue(variants.stream().filter(p->p.getId()==product.getId()).findFirst().orElse(product));
                configurations.setOnAction(e->{Product chosen=configurations.getValue();if(chosen!=null&&chosen.getId()!=product.getId())openProduct.accept(chosen);});
                info.getChildren().add(3,new VBox(6,Ui.label("Configuration","field-label"),configurations));
            }
            for(Product p:rows.stream().filter(p->p.getId()!=product.getId()).limit(4).toList()){
                ProductCard card=new ProductCard(p,()->Ui.async(this,"Adding to cart…",()->{
                    carts.add(user,cart,p.getId(),1);return true;
                },done->{changed.run();if(suggestions.lookup("#product-"+p.getId()) instanceof ProductCard added)added.confirmAdded();Ui.success(feedback,"✓ "+p.getName()+" added to your cart.");},error->Ui.failure(feedback,error)),
                    ()->openProduct.accept(p));
                if(saved!=null)card.enableSavedItems(saved);card.setPrefWidth(220);suggestions.getChildren().add(card);
            }
            related.setVisible(!suggestions.getChildren().isEmpty());
        },error->Ui.failure(feedback,error)));
    }
    private VBox specifications(Product product) {
        VBox values=new VBox(12),highlights=new VBox(10);highlights.setId("specification-highlights");
        int index=0;
        values.getChildren().addAll(Ui.row("Category",Ui.category(product.getCategory()),"muted","field-label"),
            Ui.row("Product ID",Long.toString(product.getId()),"muted","field-label"));
        for(String line:product.getSpecifications().lines().filter(s->!s.isBlank()).toList()) {
            int separator=line.indexOf(':');
            if(separator>0) {
                Label key=Ui.label(line.substring(0,separator).trim(),"muted"),value=Ui.label(line.substring(separator+1).trim(),"field-label");
                key.setMinWidth(0);value.setMinWidth(0);key.setPrefWidth(130);value.setPrefWidth(180);
                HBox.setHgrow(value,Priority.ALWAYS);(index++<2?highlights:values).getChildren().add(new HBox(16,key,value));
            } else values.getChildren().add(Ui.label(line,"product-description"));
        }
        TitledPane pane=new TitledPane("All specifications",values);pane.setId("product-specifications");pane.setAnimated(false);pane.setExpanded(false);VBox card=new VBox(10,highlights,pane);card.getStyleClass().add("card");card.setVisible(!product.getSpecifications().isBlank());card.managedProperty().bind(card.visibleProperty());return card;
    }
    private TitledPane section(String title,String text) {
        TitledPane pane=new TitledPane(title,Ui.label(text,"product-description"));pane.setExpanded(false);pane.setAnimated(false);return pane;
    }
}
