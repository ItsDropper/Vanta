package dev.itsdropper.vanta.ui.components;
import dev.itsdropper.vanta.launcher.instance.InstanceInstallationProgress;
import dev.itsdropper.vanta.launcher.instance.InstanceInstallationProgress.Stage;
import dev.itsdropper.vanta.ui.Localization;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.stage.Window;
import javafx.stage.StageStyle;
import java.util.EnumMap;
import java.util.List;
/** GitHub Actions-inspired stage UI driven by actual installer events. */
public final class InstanceCreationProgressDialog {
 private static final List<Stage> ORDER=List.of(Stage.RESOLVING_VERSION,Stage.PREPARING_INSTANCE,Stage.INSTALLING_GAME_FILES,Stage.INSTALLING_LOADER,Stage.FINALIZING);
 private final Dialog<Void> dialog=new Dialog<>();
 private final EnumMap<Stage,StageRow> rows=new EnumMap<>(Stage.class);
 private final Label headline=new Label(),detail=new Label(),currentStep=new Label();
 private final ProgressIndicator activity=new ProgressIndicator();
 private final Button actionButton;
 private final String loader;
 private boolean terminal,successful;
 public InstanceCreationProgressDialog(String name,String version,String loader,Runnable onSuccess){
  this(name,version,loader,onSuccess,null);
 }
 public InstanceCreationProgressDialog(String name,String version,String loader,Runnable onSuccess,Window owner){
  this.loader=loader==null?"Vanilla":loader;
  // Match Main: transparent native window + transparent JavaFX scene lets
  // the rounded DialogPane surface define the visible window boundary.
  dialog.initStyle(StageStyle.UNDECORATED);
  if(owner!=null)dialog.initOwner(owner);
  dialog.setOnShowing(event -> {Scene scene=dialog.getDialogPane().getScene();if(scene!=null)scene.setFill(Color.TRANSPARENT);});
  dialog.setTitle(Localization.text("instanceProgress.title"));dialog.setHeaderText(null);dialog.setResizable(false);
  dialog.getDialogPane().getStyleClass().add("instance-progress-dialog");
  dev.itsdropper.vanta.ui.Stylesheets.addTo(dialog.getDialogPane());
  dev.itsdropper.vanta.ui.ThemeManager.apply(dialog.getDialogPane(), dev.itsdropper.vanta.ui.ThemeManager.loadAccent());
  ButtonType closeType=new ButtonType(Localization.text("common.close"),ButtonBar.ButtonData.CANCEL_CLOSE);
  dialog.getDialogPane().getButtonTypes().setAll(closeType);
  actionButton=(Button)dialog.getDialogPane().lookupButton(closeType);actionButton.setDisable(true);
  actionButton.addEventFilter(javafx.event.ActionEvent.ACTION,event->{if(successful&&onSuccess!=null)onSuccess.run();});
  dialog.setOnCloseRequest(event->{if(!terminal)event.consume();});
  headline.setText(Localization.text("instanceProgress.title"));headline.getStyleClass().add("instance-progress-headline");
  Label subtitle=new Label(Localization.text("instanceProgress.subtitle"));subtitle.getStyleClass().add("instance-progress-subtitle");subtitle.setWrapText(true);
  Label identity=new Label(name+" · Minecraft "+version+" · "+this.loader);identity.getStyleClass().add("instance-progress-identity");identity.setWrapText(true);
  activity.setPrefSize(18,18);activity.setMaxSize(18,18);
  HBox heading=new HBox(12,activity,headline);
  heading.setAlignment(Pos.CENTER_LEFT);
  heading.getStyleClass().add("instance-progress-heading");
  VBox stageList=new VBox(5);
  stageList.getStyleClass().add("instance-progress-stage-list");
  for(Stage stage:ORDER){StageRow row=new StageRow(stageTitle(stage));rows.put(stage,row);stageList.getChildren().add(row.root);}
  currentStep.setText(Localization.text("instanceProgress.current"));currentStep.getStyleClass().add("instance-progress-current-label");
  detail.getStyleClass().add("instance-progress-detail");detail.setWrapText(true);
  VBox current=new VBox(6,currentStep,detail);current.getStyleClass().add("instance-progress-current");
  VBox header=new VBox(9,heading,subtitle,identity);
  VBox stages=new VBox(stageList);
  VBox content=new VBox(18,header,stages,current);
  content.getStyleClass().add("instance-progress-body");content.setPadding(new Insets(24));
  content.setPrefWidth(480);
  dialog.getDialogPane().setContent(content);
  dialog.getDialogPane().setPrefWidth(530);
  dialog.getDialogPane().setMinWidth(530);
  update(new InstanceInstallationProgress.Update(Stage.RESOLVING_VERSION,Localization.text("instanceProgress.resolving")));
 }
 public void show(){dialog.show();}
 /** Closes a screenshot-only dialog after its owning window has been captured. */
 public void closeForScreenshot(){terminal=true;dialog.close();}
 public void update(InstanceInstallationProgress.Update update){
  if(terminal||update==null)return;int active=ORDER.indexOf(update.stage());if(active<0)return;
  for(int i=0;i<ORDER.size();i++){Stage stage=ORDER.get(i);StageRow row=rows.get(stage);
   if(i<active)row.setState(State.COMPLETE);else if(i==active){row.setState(State.ACTIVE);animate(row.root);}
   else if(stage==Stage.INSTALLING_LOADER&&"Vanilla".equalsIgnoreCase(loader)&&update.stage()==Stage.FINALIZING)row.setState(State.SKIPPED);
   else row.setState(State.PENDING);
  }
  headline.setText(stageTitle(update.stage()));detail.setText(update.detail().isBlank()?Localization.text("instanceProgress.detail"):update.detail());
  currentStep.setText(Localization.text("instanceProgress.current")+" · "+(active+1)+"/"+ORDER.size());
 }
 public void complete(){
  if(terminal)return;terminal=true;successful=true;
  for(Stage stage:ORDER)rows.get(stage).setState(stage==Stage.INSTALLING_LOADER&&"Vanilla".equalsIgnoreCase(loader)?State.SKIPPED:State.COMPLETE);
  activity.setVisible(false);headline.setText(Localization.text("instanceProgress.ready"));detail.setText(Localization.text("createInstance.created"));
  currentStep.setText("");actionButton.setText(Localization.text("common.open"));actionButton.setDisable(false);
 }
 public void fail(String message){
  if(terminal)return;terminal=true;activity.setVisible(false);headline.setText(Localization.text("instanceProgress.failed"));
  detail.setText(message==null||message.isBlank()?Localization.text("createInstance.createFailed"):message);
  detail.getStyleClass().add("instance-progress-error");actionButton.setText(Localization.text("common.close"));actionButton.setDisable(false);
  boolean marked=false;for(Stage stage:ORDER){StageRow row=rows.get(stage);if(!marked&&(row.state==State.ACTIVE||row.state==State.PENDING)){row.setState(State.FAILED);marked=true;}}
 }
 private String stageTitle(Stage stage){return switch(stage){
  case RESOLVING_VERSION->Localization.text("instanceProgress.resolving");
  case PREPARING_INSTANCE->Localization.text("instanceProgress.preparing");
  case INSTALLING_GAME_FILES->Localization.text("instanceProgress.gameFiles");
  case INSTALLING_LOADER->"Vanilla".equalsIgnoreCase(loader)
          ?Localization.text("instanceProgress.vanilla")
          :Localization.text("instanceProgress.loader")+": "+loader;
  case FINALIZING->Localization.text("instanceProgress.finalizing");
 };}
 private static void animate(Node node){
  FadeTransition fade=new FadeTransition(Duration.millis(180),node);fade.setFromValue(0.72);fade.setToValue(1);
  TranslateTransition slide=new TranslateTransition(Duration.millis(180),node);slide.setFromY(4);slide.setToY(0);
  new ParallelTransition(fade,slide).playFromStart();
 }
 private enum State{PENDING,ACTIVE,COMPLETE,SKIPPED,FAILED}
 private static final class StageRow{
  private final HBox root=new HBox(12);private final Label marker=new Label(),title=new Label();private State state=State.PENDING;
  private StageRow(String text){
   root.getStyleClass().add("instance-progress-stage");marker.getStyleClass().add("instance-progress-marker");marker.setMinWidth(20);marker.setAlignment(Pos.CENTER);
   title.setText(text);title.getStyleClass().add("instance-progress-stage-title");HBox.setHgrow(title,Priority.ALWAYS);
   root.setAlignment(Pos.CENTER_LEFT);root.setPadding(new Insets(10,12,10,12));root.getChildren().addAll(marker,title);setState(State.PENDING);
  }
  private void setState(State next){
   state=next;root.getStyleClass().removeAll("progress-pending","progress-active","progress-complete","progress-skipped","progress-failed");
   root.getStyleClass().add(switch(next){case PENDING->"progress-pending";case ACTIVE->"progress-active";case COMPLETE->"progress-complete";case SKIPPED->"progress-skipped";case FAILED->"progress-failed";});
   marker.setText(switch(next){case PENDING->"○";case ACTIVE->"●";case COMPLETE->"✓";case SKIPPED->"—";case FAILED->"!";});
  }
 }
}