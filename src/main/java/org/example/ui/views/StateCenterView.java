package org.example.ui.views;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.example.launcher.instance.InstanceManager;
import org.example.launcher.model.Instance;
import org.example.launcher.state.InstanceState;
import org.example.launcher.state.InstanceStateEngine;
import org.example.ui.components.IconView;
import java.util.ArrayList;
import java.util.List;

public class StateCenterView extends VBox {
    private final VBox instanceList=new VBox(10);
    private final Label overallTitle=new Label("SCANNING...");
    private final Label overallSubtitle=new Label("Vanta is checking your installed instances.");
    private final ProgressBar progress=new ProgressBar();
    private final Button refreshButton=new Button("REFRESH",IconView.create(IconView.Type.REFRESH,15));

    public StateCenterView(){
        getStyleClass().add("state-center"); setPadding(new Insets(36)); setSpacing(22);
        Label title=new Label("State"); title.getStyleClass().add("page-title");
        Label subtitle=new Label("A live view of the health of your Vanta environments."); subtitle.getStyleClass().add("page-subtitle");
        VBox heading=new VBox(5,title,subtitle);

        StackPane hero=new StackPane(); hero.getStyleClass().add("state-hero"); hero.setPadding(new Insets(22));
        VBox heroText=new VBox(5,overallTitle,overallSubtitle);
        overallTitle.getStyleClass().add("state-hero-title"); overallSubtitle.getStyleClass().add("state-hero-subtitle");
        progress.setPrefWidth(260); progress.setProgress(-1); progress.getStyleClass().add("state-progress");
        HBox heroRow=new HBox(18,heroText,new VBox(8,progress)); heroRow.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(heroText,Priority.ALWAYS);
        refreshButton.getStyleClass().add("state-refresh-button"); refreshButton.setFocusTraversable(false); refreshButton.setOnAction(e->refresh());
        hero.getChildren().add(heroRow);

        HBox section=new HBox(10,new Label("ENVIRONMENTS"),refreshButton); section.getChildren().get(0).getStyleClass().add("state-section-title");
        VBox listCard=new VBox(instanceList); listCard.getStyleClass().add("state-list-card"); listCard.setPadding(new Insets(12)); VBox.setVgrow(listCard,Priority.ALWAYS);
        getChildren().addAll(heading,hero,section,listCard); refresh();
    }

    public void refresh(){
        overallTitle.setText("SCANNING..."); overallSubtitle.setText("Checking files, metadata and instance structure."); progress.setProgress(-1); instanceList.getChildren().clear();
        Thread thread=new Thread(()->{
            List<Instance> instances;
            try{instances=InstanceManager.discoverInstances();}catch(Throwable ex){Platform.runLater(()->{overallTitle.setText("SCAN FAILED");overallSubtitle.setText("Vanta could not inspect the installed instances.");});return;}
            List<InstanceState> states=new ArrayList<>();
            for(Instance instance:instances) states.add(InstanceStateEngine.inspect(instance));
            Platform.runLater(()->render(instances,states));
        });
        thread.setDaemon(true); thread.setName("Vanta-State-Engine"); thread.start();
    }

    private void render(List<Instance> instances,List<InstanceState> states){
        instanceList.getChildren().clear();
        int healthy=(int)states.stream().filter(InstanceState::isHealthy).count();
        int attention=(int)states.stream().filter(s->s.getLevel()==InstanceState.Level.ATTENTION).count();
        int broken=states.size()-healthy-attention;
        int affected=attention+broken;
        overallTitle.setText(states.isEmpty()?"NO ENVIRONMENTS":affected==0?"ALL SYSTEMS HEALTHY":affected+" ENVIRONMENT"+(affected==1?"":"S")+" NEED ATTENTION");
        overallSubtitle.setText(states.isEmpty()?"Create an instance and Vanta will start tracking it here.":healthy+" healthy  •  "+attention+" attention  •  "+broken+" broken");
        progress.setProgress(states.isEmpty()?0:(double)healthy/states.size());
        for(int i=0;i<instances.size();i++) addStateCard(instances.get(i),states.get(i),i);
    }

    private void addStateCard(Instance instance,InstanceState state,int index){
        HBox card=new HBox(16); card.getStyleClass().add("state-card"); card.setAlignment(Pos.CENTER_LEFT); card.setPadding(new Insets(15));
        StackPane icon=new StackPane(IconView.create(state.isHealthy()?IconView.Type.SHIELD:IconView.Type.PACKAGE,22)); icon.getStyleClass().add("state-icon-"+state.getLevel().name().toLowerCase());
        VBox text=new VBox(4); Label name=new Label(instance.getName()); name.getStyleClass().add("state-instance-name");
        Label detail=new Label(instance.getMinecraftVersion()+"  •  "+instance.getDisplayLoader()+"  •  "+state.getSummary()); detail.getStyleClass().add("state-instance-detail");
        Label fingerprint=new Label("STATE "+state.getFingerprint()); fingerprint.getStyleClass().add("state-fingerprint");
        text.getChildren().addAll(name,detail,fingerprint); HBox.setHgrow(text,Priority.ALWAYS);
        Label status=new Label(state.getTitle()); status.getStyleClass().add("state-status-"+state.getLevel().name().toLowerCase());
        Label stats=new Label(state.getChecksPassed()+"/"+state.getChecksTotal()+" checks  •  "+state.getMods()+" mods  •  "+state.getConfigs()+" configs"); stats.getStyleClass().add("state-stats");
        VBox right=new VBox(4,status,stats); right.setAlignment(Pos.CENTER_RIGHT); card.getChildren().addAll(icon,text,right); instanceList.getChildren().add(card);
        card.setOpacity(0); card.setScaleX(.985); card.setScaleY(.985);
        FadeTransition fade=new FadeTransition(Duration.millis(240+index*25),card); fade.setFromValue(0); fade.setToValue(1); fade.play();
        ScaleTransition scale=new ScaleTransition(Duration.millis(280+index*25),card); scale.setFromX(.985); scale.setFromY(.985); scale.setToX(1); scale.setToY(1); scale.play();
    }
}