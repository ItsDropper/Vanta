package dev.itsdropper.vanta.launcher.instance;
import java.util.Objects;
import java.util.function.Consumer;
/** Thread-scoped progress events from the real instance installation pipeline. */
public final class InstanceInstallationProgress {
 public enum Stage { RESOLVING_VERSION, PREPARING_INSTANCE, INSTALLING_GAME_FILES, INSTALLING_LOADER, FINALIZING }
 public record Update(Stage stage,String detail) {}
 @FunctionalInterface public interface InstallationAction { void run() throws Exception; }
 private static final ThreadLocal<Consumer<Update>> LISTENER=new ThreadLocal<>();
 private static final ThreadLocal<Stage> LAST_STAGE=new ThreadLocal<>();
 private InstanceInstallationProgress() {}
 public static void withListener(Consumer<Update> listener,InstallationAction action)throws Exception{
  Objects.requireNonNull(action,"action");LISTENER.set(listener);LAST_STAGE.remove();
  try{action.run();}finally{LISTENER.remove();LAST_STAGE.remove();}
 }
 public static void report(Stage stage,String detail){
  Consumer<Update> listener=LISTENER.get();
  if(listener==null||stage==null||stage==LAST_STAGE.get())return;
  LAST_STAGE.set(stage);
  try{listener.accept(new Update(stage,detail==null?"":detail));}
  catch(RuntimeException failure){System.err.println("Could not publish instance progress: "+failure.getMessage());}
 }
}