package edu.course.shop.ui;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.PopupWindow;
import javafx.stage.Window;
import javafx.util.Duration;

/** Shared interaction polish; native menus retain their keyboard and dismissal behavior. */
public final class Interactions {
    private static final String CSS=Interactions.class.getResource("/interactions.css").toExternalForm();
    private Interactions() { }
    public static void install(Parent root) {
        if(!root.getStylesheets().contains(CSS))root.getStylesheets().add(CSS);
        enhance(root);
    }
    private static void enhance(Node node) {
        if(node.getProperties().putIfAbsent("forge.interactions",Boolean.TRUE)!=null)return;
        if(node instanceof Button button)Motion.press(button);
        if(node instanceof MenuButton menu)menu.showingProperty().addListener((o,a,open)->{
            rotateArrow(menu,open);
            if(open)Platform.runLater(()->{
                if(!menu.isShowing())return;
                for(Window window:Window.getWindows())if(window.isShowing()&&window instanceof PopupWindow popup) {
                    for(Node ancestor=popup.getOwnerNode();ancestor!=null;ancestor=ancestor.getParent())if(ancestor==menu){
                        if(window.getScene().getProperties().putIfAbsent("forge.menuEscape",true)==null)window.getScene().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,event->{if(event.getCode()==javafx.scene.input.KeyCode.ESCAPE){menu.hide();menu.requestFocus();event.consume();}});
                        popup(window.getScene().getRoot(),menu);return;}
                }
            });
        });
        if(node instanceof ComboBox<?> combo)combo.showingProperty().addListener((o,a,open)->{
            rotateArrow(combo,open);
            if(open)Platform.runLater(()->{
                if(combo.isShowing()&&combo.getSkin() instanceof ComboBoxListViewSkin<?> skin)popup((Parent)skin.getPopupContent(),combo);
            });
        });
        // Also enhance asynchronously loaded pages and newly created filter controls.
        if(node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(Interactions::enhance);
            parent.getChildrenUnmodifiable().addListener((ListChangeListener<Node>)change->{
                while(change.next())if(change.wasAdded())change.getAddedSubList().forEach(Interactions::enhance);
            });
        }
    }
    private static void rotateArrow(Node owner,boolean open) {
        Node arrow=owner.lookup(".arrow");if(arrow==null)return;
        if(!arrow.getProperties().containsKey("forge.arrowPreference")){InvalidationListener reset=o->{if(Motion.isReduced()){Animation active=(Animation)arrow.getProperties().get("forge.arrowAnimation");if(active!=null)active.stop();boolean showing=owner instanceof MenuButton m?m.isShowing():owner instanceof ComboBox<?> c&&c.isShowing();arrow.setRotate(showing?180:0);}};Motion.reducedProperty().addListener(new WeakInvalidationListener(reset));arrow.getProperties().put("forge.arrowPreference",reset);}
        Animation previous=(Animation)arrow.getProperties().remove("forge.arrowAnimation");if(previous!=null)previous.stop();
        RotateTransition turn=new RotateTransition(Duration.millis(150),arrow);turn.setToAngle(open?180:0);turn.setInterpolator(Interpolator.EASE_OUT);
        arrow.getProperties().put("forge.arrowAnimation",turn);
        if(Motion.isReduced())arrow.setRotate(open?180:0);else turn.play();
    }
    private static void popup(Parent content,Node owner) {
        // Style the painted content, not PopupControl's non-rendering CSS bridge.
        Node option=content.lookup(".menu-item");
        if(option!=null)for(Parent ancestor=option.getParent();ancestor!=null;ancestor=ancestor.getParent())
            if(ancestor.getStyleClass().contains("context-menu")){content=ancestor;break;}
        if(!content.getStylesheets().contains(CSS))content.getStylesheets().add(CSS);
        if(!content.getStyleClass().contains("forge-options"))content.getStyleClass().add("forge-options");
        boolean dark=false;for(Node ancestor=owner;ancestor!=null;ancestor=ancestor.getParent())
            if(ancestor.getStyleClass().contains("store-dark")||ancestor.getStyleClass().contains("admin-dark"))dark=true;
        content.getStyleClass().remove("forge-options-dark");if(dark)content.getStyleClass().add("forge-options-dark");
        Motion.popup(content);
    }
    /** A filter disclosure that does not reserve space or keyboard stops when closed. */
    static VBox section(String title,Node content) {
        Button header=Ui.button(title,()->{});header.getStyleClass().add("disclosure-toggle");header.setMaxWidth(Double.MAX_VALUE);
        Label chevron=Ui.label("⌃","disclosure-chevron");header.setGraphic(chevron);header.setContentDisplay(ContentDisplay.RIGHT);
        VBox body=new VBox(content);body.setMinHeight(0);body.getStyleClass().add("disclosure-body");
        Rectangle clip=new Rectangle();clip.widthProperty().bind(body.widthProperty());clip.heightProperty().bind(body.heightProperty());body.setClip(clip);
        boolean[] expanded={true};Timeline[] animation={null};
        Runnable finish=()->{if(animation[0]!=null)animation[0].stop();body.setVisible(expanded[0]);body.setManaged(expanded[0]);body.setPrefHeight(Region.USE_COMPUTED_SIZE);body.setMaxHeight(Region.USE_COMPUTED_SIZE);chevron.setRotate(expanded[0]?0:180);};
        header.setAccessibleText(title+", expanded");
        header.setOnAction(event->{
            if(animation[0]!=null)animation[0].stop();double current=body.isManaged()?body.getHeight():0;expanded[0]=!expanded[0];header.setAccessibleText(title+(expanded[0]?", expanded":", collapsed"));
            if(Motion.isReduced()){finish.run();return;}
            body.setVisible(true);body.setManaged(true);body.setPrefHeight(Region.USE_COMPUTED_SIZE);body.setMaxHeight(Region.USE_COMPUTED_SIZE);
            double target=expanded[0]?body.prefHeight(body.getWidth()):0;body.setPrefHeight(current);body.setMaxHeight(current);
            animation[0]=new Timeline(new KeyFrame(Duration.millis(180),new KeyValue(body.prefHeightProperty(),target,Interpolator.EASE_OUT),new KeyValue(body.maxHeightProperty(),target,Interpolator.EASE_OUT),new KeyValue(chevron.rotateProperty(),expanded[0]?0:180,Interpolator.EASE_OUT)));
            animation[0].setOnFinished(e->finish.run());animation[0].play();
        });
        InvalidationListener cancel=o->{if(Motion.isReduced()||body.getScene()==null)finish.run();};
        Motion.reducedProperty().addListener(new WeakInvalidationListener(cancel));body.sceneProperty().addListener(cancel);body.getProperties().put("forge.disclosure",cancel);
        VBox section=new VBox(8,header,body);section.getStyleClass().add("filter-section");return section;
    }
    static void categoryIndicator(HBox row,ObservableValue<?> selection) {
        row.getStyleClass().add("animated-category-nav");Region line=new Region();line.setManaged(false);line.setMouseTransparent(true);line.setId("category-active-indicator");line.getStyleClass().add("category-indicator");row.getChildren().add(line);
        Timeline[] animation={null};boolean[] positioned={false};
        java.util.function.Consumer<Boolean> move=animate->{
            Node selected=row.getChildren().stream().filter(n->n.isVisible()&&n.getStyleClass().contains("category-active")).findFirst().orElse(null);
            if(selected==null){line.setVisible(false);return;}line.setVisible(true);double x=selected.getLayoutX(),width=selected.getLayoutBounds().getWidth();
            if(width<=0)return;if(animation[0]!=null)animation[0].stop();line.setLayoutY(Math.max(0,row.getHeight()-2));line.resize(line.getWidth(),2);
            if(!animate||!positioned[0]||Motion.isReduced()){line.setTranslateX(x);line.setPrefWidth(width);line.resize(width,2);}
            else {animation[0]=new Timeline(new KeyFrame(Duration.millis(160),new KeyValue(line.translateXProperty(),x,Interpolator.EASE_OUT),new KeyValue(line.prefWidthProperty(),width,Interpolator.EASE_OUT)));animation[0].play();}positioned[0]=true;
        };
        line.prefWidthProperty().addListener((o,a,b)->line.resize(b.doubleValue(),2));
        row.layoutBoundsProperty().addListener((o,a,b)->Platform.runLater(()->move.accept(false)));
        selection.addListener((o,a,b)->Platform.runLater(()->move.accept(true)));
        InvalidationListener reset=o->{if(Motion.isReduced()||row.getScene()==null){if(animation[0]!=null)animation[0].stop();move.accept(false);}};
        row.sceneProperty().addListener(reset);Motion.reducedProperty().addListener(new WeakInvalidationListener(reset));row.getProperties().put("forge.indicator",reset);Platform.runLater(()->move.accept(false));
    }
}