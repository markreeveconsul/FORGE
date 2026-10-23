package edu.course.shop.ui;

import javafx.animation.*;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/** The robot is the button: native hover tilt, a small click hop, keyboard access and no painted pill. */
public final class RobotLauncher extends Button {
    private final RobotAvatar robot=new RobotAvatar(96);
    private final Timeline gesture=new Timeline(),click=new Timeline();
    private final PauseTransition focusAttention=new PauseTransition(Duration.seconds(2.5));
    private final Runnable open;
    private boolean pointerInside,opening;
    private Window window;
    private final InvalidationListener lifecycle=o->{if(!available())reset();};
    private final InvalidationListener motion=o->{if(Motion.isReduced())reset();};
    private final ChangeListener<Window> windowChanged=(o,a,b)->watchWindow(b);

    public RobotLauncher(Runnable open){
        this.open=open;setId("assistant-launcher");getStyleClass().add("assistant-launcher");
        robot.setId("assistant-launcher-robot");robot.setSleepAfter(Duration.seconds(40));setGraphic(robot);setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        setMinSize(108,108);setPrefSize(108,108);setMaxSize(108,108);setFocusTraversable(true);
        setAccessibleText("Open FORGE AI assistant");setAccessibleHelp("Ask about products, your cart or your orders.");
        Tooltip tip=new Tooltip("Ask FORGE AI");tip.setShowDelay(Duration.millis(350));setTooltip(tip);
        focusAttention.setOnFinished(e->{if(!pointerInside)robot.setAttentive(false);});
        setOnMouseEntered(e->{pointerInside=true;attend();});
        setOnMouseExited(e->{pointerInside=false;if(!opening){robot.setAttentive(isFocused());if(isFocused())focusAttention.playFromStart();move(1,0,0,0,190);}});
        setOnMouseMoved(e->{if(!opening){double x=clamp((e.getX()-getWidth()/2)/Math.max(1,getWidth()/2)),y=clamp((e.getY()-getHeight()/2)/Math.max(1,getHeight()/2));move(1.08,x*2,-5+y*1.5,x*5,100);}});
        focusedProperty().addListener((o,a,focused)->{if(focused)attend();else if(!pointerInside&&!opening){robot.setAttentive(false);move(1,0,0,0,190);}});
        setOnAction(e->activate());
        visibleProperty().addListener(lifecycle);disabledProperty().addListener(lifecycle);
        sceneProperty().addListener((o,a,b)->{if(a!=null)a.windowProperty().removeListener(windowChanged);if(b!=null)b.windowProperty().addListener(windowChanged);watchWindow(b==null?null:b.getWindow());});
        Motion.reducedProperty().addListener(new WeakInvalidationListener(motion));
    }
    private void attend(){if(!available())return;robot.wake();robot.setAttentive(true);if(!pointerInside)focusAttention.playFromStart();if(!opening)move(1.08,0,-5,0,180);}
    private void activate(){
        if(opening||isDisabled()||!isVisible())return;
        robot.wake();robot.react(RobotAvatar.State.HAPPY);
        if(Motion.isReduced()){open.run();return;}
        opening=true;gesture.stop();click.stop();
        click.getKeyFrames().setAll(
            new KeyFrame(Duration.millis(75),new KeyValue(robot.translateYProperty(),-14,Interpolator.EASE_OUT),new KeyValue(robot.scaleXProperty(),1.12,Interpolator.EASE_OUT),new KeyValue(robot.scaleYProperty(),1.12,Interpolator.EASE_OUT),new KeyValue(robot.rotateProperty(),-7,Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(170),new KeyValue(robot.translateYProperty(),0,Interpolator.EASE_BOTH),new KeyValue(robot.translateXProperty(),0,Interpolator.EASE_BOTH),new KeyValue(robot.scaleXProperty(),1,Interpolator.EASE_BOTH),new KeyValue(robot.scaleYProperty(),1,Interpolator.EASE_BOTH),new KeyValue(robot.rotateProperty(),0,Interpolator.EASE_BOTH)));
        click.setOnFinished(e->{opening=false;open.run();});click.playFromStart();
    }
    private void move(double scale,double x,double y,double angle,int millis){
        gesture.stop();if(Motion.isReduced()||!available()){neutral();return;}
        gesture.getKeyFrames().setAll(new KeyFrame(Duration.millis(millis),new KeyValue(robot.scaleXProperty(),scale,Interpolator.EASE_OUT),new KeyValue(robot.scaleYProperty(),scale,Interpolator.EASE_OUT),new KeyValue(robot.translateXProperty(),x,Interpolator.EASE_OUT),new KeyValue(robot.translateYProperty(),y,Interpolator.EASE_OUT),new KeyValue(robot.rotateProperty(),angle,Interpolator.EASE_OUT)));gesture.playFromStart();
    }
    private static double clamp(double value){return Math.max(-1,Math.min(1,value));}
    private boolean available(){return getScene()!=null&&isVisible()&&!isDisabled()&&window!=null&&window.isShowing()&&!(window instanceof Stage stage&&stage.isIconified());}
    private void neutral(){robot.setScaleX(1);robot.setScaleY(1);robot.setTranslateX(0);robot.setTranslateY(0);robot.setRotate(0);}
    private void reset(){gesture.stop();click.stop();focusAttention.stop();opening=false;pointerInside=false;robot.setAttentive(false);robot.returnToRest();neutral();}
    private void watchWindow(Window next){
        if(window!=null){window.showingProperty().removeListener(lifecycle);if(window instanceof Stage stage)stage.iconifiedProperty().removeListener(lifecycle);}
        window=next;if(next!=null){next.showingProperty().addListener(lifecycle);if(next instanceof Stage stage)stage.iconifiedProperty().addListener(lifecycle);}if(!available())reset();
    }
}