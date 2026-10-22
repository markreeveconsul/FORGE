package edu.course.shop.ui;

import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.util.Duration;

/** Session-wide motion preference and small, reversible hover effects. FX thread only. */
public final class Motion {
    private static final BooleanProperty reduced = new SimpleBooleanProperty(Boolean.getBoolean("shop.reduceMotion"));
    private Motion() { }
    public static BooleanProperty reducedProperty() { return reduced; }
    public static boolean isReduced() { return reduced.get(); }

    public static void lift(Node target) {
        TranslateTransition animation=new TranslateTransition(Duration.millis(160),target);
        animation.setInterpolator(Interpolator.EASE_OUT);
        InvalidationListener update=o->{
            animation.stop();
            if(target.getScene()==null||target.isDisabled()||isReduced()) { target.setTranslateY(0);return; }
            animation.setToY(target.isHover()?-2:0);animation.play();
        };
        target.hoverProperty().addListener(update);target.disabledProperty().addListener(update);target.sceneProperty().addListener(update);
        // The global preference must not retain discarded pages or their controls.
        reduced.addListener(new WeakInvalidationListener(update));
        target.getProperties().put("market.buttonMotion",update);
    }

    public static void zoom(Node trigger,Node image) {
        ScaleTransition animation=new ScaleTransition(Duration.millis(220),image);
        animation.setInterpolator(Interpolator.EASE_OUT);
        InvalidationListener update=o->{
            animation.stop();
            if(trigger.getScene()==null||trigger.isDisabled()||isReduced()) {
                image.setScaleX(1);image.setScaleY(1);return;
            }
            double scale=trigger.isHover()?1.035:1;
            animation.setToX(scale);animation.setToY(scale);animation.play();
        };
        trigger.hoverProperty().addListener(update);trigger.disabledProperty().addListener(update);trigger.sceneProperty().addListener(update);
        reduced.addListener(new WeakInvalidationListener(update));
        trigger.getProperties().put("market.imageMotion",update);
    }
    public static void reveal(Node target) { transientEntrance(target,0,160); }
    public static void dropIn(Node target) { transientEntrance(target,-18,200); }

    /** Stable buttons: press feedback never shifts their row or hit target. */
    public static void press(Node target) {
        if(target.getProperties().containsKey("forge.press"))return;
        ScaleTransition animation=new ScaleTransition(Duration.millis(100),target);
        animation.setInterpolator(Interpolator.EASE_OUT);
        InvalidationListener update=o->{
            animation.stop();
            if(target.getScene()==null||target.isDisabled()||isReduced()){target.setScaleX(1);target.setScaleY(1);return;}
            double scale=target.isPressed()?.98:1;animation.setToX(scale);animation.setToY(scale);animation.play();
        };
        target.pressedProperty().addListener(update);target.disabledProperty().addListener(update);target.sceneProperty().addListener(update);
        reduced.addListener(new WeakInvalidationListener(update));target.getProperties().put("forge.press",update);
    }
    /** Reusable popup entrance; hide/reopen always resets the previous transition. */
    public static void popup(Node target) { transientEntrance(target,-6,160); }
    public static void panel(Node target) { transientEntrance(target,-12,200); }
    private static void transientEntrance(Node target,double offset,int duration) {
        Runnable previous=(Runnable)target.getProperties().remove("forge.entrance");if(previous!=null)previous.run();
        if(isReduced()){target.setOpacity(1);target.setTranslateY(0);return;}
        javafx.animation.FadeTransition fade=new javafx.animation.FadeTransition(Duration.millis(duration),target);fade.setFromValue(0);fade.setToValue(1);
        TranslateTransition slide=new TranslateTransition(Duration.millis(duration),target);slide.setFromY(offset);slide.setToY(0);
        javafx.animation.ParallelTransition animation=new javafx.animation.ParallelTransition(fade,slide);animation.setInterpolator(Interpolator.EASE_OUT);
        InvalidationListener[] listener={null};
        Runnable reset=()->{animation.stop();target.setOpacity(1);target.setTranslateY(0);if(listener[0]!=null){target.sceneProperty().removeListener(listener[0]);target.visibleProperty().removeListener(listener[0]);reduced.removeListener(listener[0]);}target.getProperties().remove("forge.entrance");};
        listener[0]=o->{if(isReduced()||target.getScene()==null||!target.isVisible())reset.run();};
        target.sceneProperty().addListener(listener[0]);target.visibleProperty().addListener(listener[0]);reduced.addListener(listener[0]);
        target.getProperties().put("forge.entrance",reset);animation.setOnFinished(e->reset.run());animation.play();
    }
    /** A quiet skeleton pulse, stopped whenever its content leaves the scene. */
    public static void loading(Node target) {
        javafx.animation.FadeTransition pulse=new javafx.animation.FadeTransition(Duration.millis(850),target);pulse.setFromValue(.60);pulse.setToValue(1);pulse.setAutoReverse(true);pulse.setCycleCount(javafx.animation.Animation.INDEFINITE);
        InvalidationListener sync=o->{pulse.stop();target.setOpacity(1);if(target.getScene()!=null&&target.isVisible()&&!isReduced())pulse.playFromStart();};
        target.sceneProperty().addListener(sync);target.visibleProperty().addListener(sync);reduced.addListener(new WeakInvalidationListener(sync));target.getProperties().put("forge.loading",sync);sync.invalidated(null);
    }}
