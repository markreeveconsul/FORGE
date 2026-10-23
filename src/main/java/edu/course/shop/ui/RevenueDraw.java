package edu.course.shop.ui;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.InvalidationListener;
import javafx.scene.control.ScrollPane;
import java.util.*;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/** Reveals real chart geometry from left to right; data values and axes never interpolate. */
final class RevenueDraw {
    private final AreaChart<Number,Number> chart;
    private final Timeline sweep=new Timeline();
    private final DoubleProperty progress=new SimpleDoubleProperty(1);
    private final List<ScrollPane> scrolls=new ArrayList<>();
    private final InvalidationListener viewport=o->{if(this.requested)queue();};
    private Node content,previousClip;private Rectangle clip;private Bounds bounds;private boolean requested,queued;
    RevenueDraw(AreaChart<Number,Number> chart){
        this.chart=chart;progress.addListener((o,a,b)->{if(clip!=null)clip.setWidth((bounds.getWidth()+16)*b.doubleValue());});
        chart.widthProperty().addListener((o,a,b)->finish());chart.heightProperty().addListener((o,a,b)->finish());
        chart.sceneProperty().addListener((o,a,b)->followScrolls());chart.parentProperty().addListener((o,a,b)->followScrolls());chart.localToSceneTransformProperty().addListener(viewport);
        AdminMotion.observe(chart,()->{if(!AdminMotion.animated(chart))finish();else if(requested)queue();});
    }
    void complete(){requested=false;finish();}
    void request(){finish();requested=true;queue();}
    private void queue(){if(queued||!viewable())return;queued=true;Platform.runLater(()->{queued=false;if(requested&&viewable())begin();});}
    private void followScrolls(){
        for(ScrollPane scroll:scrolls){scroll.vvalueProperty().removeListener(viewport);scroll.hvalueProperty().removeListener(viewport);scroll.viewportBoundsProperty().removeListener(viewport);}scrolls.clear();
        if(chart.getScene()!=null)for(Node p=chart.getParent();p!=null;p=p.getParent())if(p instanceof ScrollPane scroll){scrolls.add(scroll);scroll.vvalueProperty().addListener(viewport);scroll.hvalueProperty().addListener(viewport);scroll.viewportBoundsProperty().addListener(viewport);}
        if(requested)queue();
    }
    private boolean viewable(){
        if(!AdminMotion.animated(chart))return false;Bounds drawn=chart.localToScene(chart.getLayoutBounds());if(drawn.getWidth()<=0||drawn.getHeight()<=0)return false;
        for(ScrollPane scroll:scrolls){Node viewport=scroll.lookup(".viewport");if(viewport==null)continue;Bounds visible=viewport.localToScene(viewport.getLayoutBounds());if(visible.getHeight()<=0)return false;double fraction=(Math.min(drawn.getMaxY(),visible.getMaxY())-Math.max(drawn.getMinY(),visible.getMinY()))/Math.min(drawn.getHeight(),visible.getHeight());if(fraction<.98)return false;}
        return true;
    }
    private void begin(){
        requested=false;chart.applyCss();chart.layout();if(chart.getData().isEmpty())return;
        Node series=chart.getData().getFirst().getNode();if(series==null||series.getParent()==null)return;
        content=series.getParent();bounds=content.getLayoutBounds();if(bounds.getWidth()<=0||bounds.getHeight()<=0){content=null;return;}
        previousClip=content.getClip();clip=new Rectangle(bounds.getMinX()-8,bounds.getMinY()-8,0,bounds.getHeight()+16);clip.setId("revenue-draw-clip");content.setClip(clip);
        progress.set(0);chart.getProperties().put("forge.revenueDrawing",true);
        sweep.getKeyFrames().setAll(new KeyFrame(Duration.ZERO,new KeyValue(progress,0)),new KeyFrame(Duration.millis(900),new KeyValue(progress,1,Interpolator.EASE_BOTH)));sweep.setOnFinished(e->finish());sweep.playFromStart();
    }
    private void finish(){sweep.stop();if(content!=null&&content.getClip()==clip)content.setClip(previousClip);content=null;clip=null;previousClip=null;progress.set(1);chart.getProperties().put("forge.revenueDrawing",false);}
}