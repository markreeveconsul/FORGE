package edu.course.shop.ui;
import edu.course.shop.model.*;
import edu.course.shop.service.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.function.Consumer;
public final class SavedItemsView extends VBox {
    public SavedItemsView(User user,Cart cart,ProductService products,SavedItems saved,Runnable changed,Runnable browse,Consumer<Product> details,Consumer<String> toast) {
        setId("saved-items-page");getStyleClass().add("page");setSpacing(20);Label feedback=Ui.feedback("saved-feedback");Label count=Ui.label("","muted");count.setId("saved-count");TilePane grid=new TilePane(20,24);grid.setPrefColumns(3);grid.setPrefTileWidth(260);grid.setId("saved-grid");
        getChildren().addAll(Ui.label("Saved items","page-title"),Ui.label(user instanceof Guest?"Your favorites stay here during this visit. Sign in to save them to your account.":"Keep products here while you compare. Saving an item does not reserve stock.","muted"),count,feedback,Ui.scroll(grid));
        boolean[] loaded={false};grid.getChildren().setAll(Ui.skeletonProductGrid(3));
        saved.observe(this,()->{
            if(!loaded[0])return;
            grid.getChildren().removeIf(node->node instanceof ProductCard&& !saved.snapshot().contains(Long.parseLong(node.getId().substring("product-".length()))));
            long visible=grid.getChildren().stream().filter(ProductCard.class::isInstance).count();count.setText(visible+(visible==1?" product saved":" products saved"));count.setVisible(visible>0);count.setManaged(visible>0);
            if(grid.getChildren().isEmpty())grid.getChildren().add(Ui.empty("No saved items available","Save a product with its heart button. Unavailable products are hidden.","Explore products",browse));
        });
        Platform.runLater(()->Ui.async(this,"Loading saved items…",()->products.search(user,"",null),rows->{
            loaded[0]=true;grid.getChildren().clear();
            for(Product p:rows)if(saved.snapshot().contains(p.getId())) {
                ProductCard card=new ProductCard(p,()->Ui.async(this,"Adding to cart…",()->{new CartService(products).add(user,cart,p.getId(),1);return true;},done->{changed.run();if(grid.lookup("#product-"+p.getId()) instanceof ProductCard c)c.confirmAdded();toast.accept("✓ "+p.getName()+" added to cart");},error->Ui.failure(feedback,error)),()->details.accept(p));card.enableSavedItems(saved,true);card.setPrefWidth(260);grid.getChildren().add(card);
            }
            long visible=grid.getChildren().stream().filter(ProductCard.class::isInstance).count();count.setText(visible+(visible==1?" product saved":" products saved"));count.setVisible(visible>0);count.setManaged(visible>0);
            if(grid.getChildren().isEmpty())grid.getChildren().add(Ui.empty("No saved items available","Save a product with its heart button. Unavailable products are hidden.","Explore products",browse));
        },error->{loaded[0]=true;grid.getChildren().setAll(Ui.empty("Could not load saved items","Please try again later or browse the catalog.","Explore products",browse));Ui.failure(feedback,error);}));
    }
}
