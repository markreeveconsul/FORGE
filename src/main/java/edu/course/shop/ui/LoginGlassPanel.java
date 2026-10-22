package edu.course.shop.ui;

import javafx.animation.*;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/** Glass reflection and orange light travelling around a rounded card. */
final class LoginGlassPanel extends StackPane {
    private final Pane reflection=new Pane(),lights=new Pane();
    private final Polygon sheen=new Polygon();private final Rectangle mask=new Rectangle();
    private final Rectangle[] trails=new Rectangle[4];
    private final DoubleProperty phase=new SimpleDoubleProperty(.14);
    private final Timeline flow=new Timeline(30,new KeyFrame(Duration.ZERO,new KeyValue(phase,0)),new KeyFrame(Duration.seconds(16),new KeyValue(phase,1,Interpolator.LINEAR)));
    private final InvalidationListener lifecycle=o->syncMotion();
    private final ChangeListener<Window> windowListener=(o,a,b)->watch(b);
    private Window window;
    LoginGlassPanel(VBox form) {
        setId("login-glass-panel");getStyleClass().add("login-glass-panel");setMinWidth(0);setMaxHeight(USE_PREF_SIZE);
        reflection.setManaged(false);reflection.setMouseTransparent(true);lights.setManaged(false);lights.setMouseTransparent(true);
        sheen.setFill(new LinearGradient(0,0,.8,1,true,CycleMethod.NO_CYCLE,new Stop(0,Color.web("#FFFFFF",.13)),new Stop(.48,Color.web("#FFFFFF",.045)),new Stop(1,Color.web("#FFFFFF",.012))));
        sheen.setId("login-glass-reflection");reflection.getChildren().add(sheen);mask.setArcWidth(28);mask.setArcHeight(28);reflection.setClip(mask);
        for(int i=0;i<trails.length;i++){
            Rectangle trail=new Rectangle();trail.setFill(null);trail.setArcWidth(28);trail.setArcHeight(28);trail.setStrokeLineCap(StrokeLineCap.ROUND);
            boolean halo=i%2==0;trail.setStroke(Color.web(halo?"#FF591E":"#FF9164",halo?.50:.70));trail.setStrokeWidth(halo?5:1.2);
            if(halo)trail.setEffect(new GaussianBlur(9));trail.setMouseTransparent(true);trails[i]=trail;lights.getChildren().add(trail);
        }
        getChildren().addAll(reflection,form,lights);flow.setCycleCount(Animation.INDEFINITE);
        phase.addListener((o,a,b)->positionLights());widthProperty().addListener(o->geometry());heightProperty().addListener(o->geometry());
        Motion.reducedProperty().addListener(new WeakInvalidationListener(lifecycle));visibleProperty().addListener(lifecycle);
        sceneProperty().addListener((o,a,b)->{if(a!=null)a.windowProperty().removeListener(windowListener);if(b!=null)b.windowProperty().addListener(windowListener);watch(b==null?null:b.getWindow());});
    }
    private void geometry(){
        double w=getWidth(),h=getHeight();reflection.resize(w,h);lights.resize(w,h);mask.setWidth(w);mask.setHeight(h);
        sheen.getPoints().setAll(0d,0d,w*.79,0d,w*.30,h,0d,h);
        double width=Math.max(0,w-2),height=Math.max(0,h-2),perimeter=Math.max(1,2*(width+height)-112+28*Math.PI);
        for(int i=0;i<trails.length;i++){Rectangle trail=trails[i];trail.setX(1);trail.setY(1);trail.setWidth(width);trail.setHeight(height);double length=Math.min(150,perimeter*(i%2==0?.10:.065));trail.getStrokeDashArray().setAll(length,Math.max(1,perimeter-length));}
        positionLights();
    }
    private void positionLights(){
        double perimeter=Math.max(1,2*(Math.max(0,getWidth()-2)+Math.max(0,getHeight()-2))-112+28*Math.PI);
        for(int i=0;i<trails.length;i++)trails[i].setStrokeDashOffset(-perimeter*(phase.get()+(i>=2?.5:0)));
    }
    private void watch(Window next){
        if(window!=null){window.showingProperty().removeListener(lifecycle);if(window instanceof Stage stage)stage.iconifiedProperty().removeListener(lifecycle);}
        window=next;if(window!=null){window.showingProperty().addListener(lifecycle);if(window instanceof Stage stage)stage.iconifiedProperty().addListener(lifecycle);}syncMotion();
    }
    private void syncMotion(){
        boolean active=getScene()!=null&&isVisible()&&window!=null&&window.isShowing()&&(!(window instanceof Stage stage)||!stage.isIconified())&&!Motion.isReduced();
        if(active){if(flow.getStatus()!=Animation.Status.RUNNING)flow.play();}
        else{flow.pause();if(Motion.isReduced())phase.set(.14);}
    }
    boolean glowRunning(){return flow.getStatus()==Animation.Status.RUNNING;}
    double glowPhase(){return phase.get();}
}