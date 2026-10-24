package edu.course.shop.ui;

import javafx.animation.*;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.scene.*;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.*;
import javafx.util.Duration;
import java.util.*;

/** Shared admin animation lifecycle: decorative motion never continues in a hidden window. */
final class AdminMotion {
    private AdminMotion() { }
    static boolean showing(Node node){if(node.getScene()==null||node.getScene().getWindow()==null||!node.getScene().getWindow().isShowing())return false;Window w=node.getScene().getWindow();if(w instanceof Stage stage&&stage.isIconified())return false;for(Node p=node;p!=null;p=p.getParent())if(!p.isVisible())return false;return true;}
    static boolean animated(Node node){return showing(node)&&!Motion.isReduced();}
    static void observe(Node node,Runnable sync){new Watch(node,sync);}
    private static final class Watch {
        final Node node;final Runnable sync;final List<Node> ancestors=new ArrayList<>();Window window;
        final InvalidationListener update=o->sync();
        final ChangeListener<Window> windows=(o,a,b)->window(b);
        Watch(Node node,Runnable sync){this.node=node;this.sync=sync;node.getProperties().put(this,this);node.visibleProperty().addListener(update);node.parentProperty().addListener((o,a,b)->parents());node.sceneProperty().addListener((o,a,b)->{if(a!=null)a.windowProperty().removeListener(windows);if(b!=null)b.windowProperty().addListener(windows);parents();window(b==null?null:b.getWindow());});Motion.reducedProperty().addListener(new WeakInvalidationListener(update));parents();window(node.getScene()==null?null:node.getScene().getWindow());}
        void sync(){sync.run();}
        void parents(){ancestors.forEach(p->p.visibleProperty().removeListener(update));ancestors.clear();if(node.getScene()!=null)for(Node p=node.getParent();p!=null;p=p.getParent()){ancestors.add(p);p.visibleProperty().addListener(update);}sync();}
        void window(Window next){if(window!=null){window.showingProperty().removeListener(update);if(window instanceof Stage stage)stage.iconifiedProperty().removeListener(update);}window=next;if(next!=null){next.showingProperty().addListener(update);if(next instanceof Stage stage)stage.iconifiedProperty().addListener(update);}sync();}
    }
    static void disclose(Node node,boolean open){
        if(node.getProperties().putIfAbsent("admin.discloseWatch",true)==null)observe(node,()->{if(!animated(node)){Animation active=(Animation)node.getProperties().remove("admin.disclose");if(active!=null){active.stop();node.setOpacity(1);node.setTranslateY(0);node.setVisible(Boolean.TRUE.equals(node.getProperties().get("admin.discloseTarget")));}}});
        Object previous=node.getProperties().put("admin.discloseTarget",open);if(Objects.equals(previous,open))return;
        Animation old=(Animation)node.getProperties().remove("admin.disclose");if(old!=null)old.stop();
        Runnable finish=()->{node.setOpacity(1);node.setTranslateY(0);node.setVisible(open);node.getProperties().remove("admin.disclose");};
        if(Motion.isReduced()||node.getParent()==null||!showing(node.getParent())){finish.run();return;}
        if(open&&!node.isVisible()){node.setOpacity(0);node.setTranslateY(8);node.setVisible(true);}
        Timeline motion=new Timeline(new KeyFrame(Duration.millis(160),new KeyValue(node.opacityProperty(),open?1:0,Interpolator.EASE_BOTH),new KeyValue(node.translateYProperty(),open?0:6,Interpolator.EASE_BOTH)));motion.setOnFinished(e->finish.run());node.getProperties().put("admin.disclose",motion);motion.play();
    }
    static final class Dots extends HBox {
        private final Timeline pulse=new Timeline();private final List<Label> dots=new ArrayList<>();private boolean running;
        Dots(){super(6);setId("ai-manager-terminal-dots");for(int i=0;i<3;i++){Label dot=Ui.label("●","manager-terminal-dots");dot.setId("ai-manager-terminal-dot-"+i);dots.add(dot);getChildren().add(dot);double offset=i*180;
            pulse.getKeyFrames().addAll(new KeyFrame(Duration.ZERO,new KeyValue(dot.opacityProperty(),.4),new KeyValue(dot.translateYProperty(),0)),new KeyFrame(Duration.millis(offset+180),new KeyValue(dot.opacityProperty(),1,Interpolator.EASE_BOTH),new KeyValue(dot.translateYProperty(),-2.5,Interpolator.EASE_BOTH)),new KeyFrame(Duration.millis(offset+420),new KeyValue(dot.opacityProperty(),.4,Interpolator.EASE_BOTH),new KeyValue(dot.translateYProperty(),0,Interpolator.EASE_BOTH)),new KeyFrame(Duration.millis(1100),new KeyValue(dot.opacityProperty(),.4),new KeyValue(dot.translateYProperty(),0)));}
            pulse.setCycleCount(Animation.INDEFINITE);observe(this,this::sync);sync();}
        void setRunning(boolean value){running=value;setAccessibleText(value?"AI command in progress":"AI activity idle");sync();}
        void sync(){if(running&&animated(this)){if(pulse.getStatus()!=Animation.Status.RUNNING)pulse.playFromStart();}else{pulse.stop();dots.forEach(dot->{dot.setOpacity(running?1:.55);dot.setTranslateY(0);});}}
        void dispose(){running=false;sync();}
    }
}