package dev.itsdropper.vanta.ui.kit;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/** On/off switch for settings that apply immediately. */
public final class Switch extends StackPane {

    private static final PseudoClass ON = PseudoClass.getPseudoClass("on");
    private final BooleanProperty selected = new SimpleBooleanProperty();
    private final Region thumb = new Region();
    private final TranslateTransition slide = new TranslateTransition(Duration.millis(110), thumb);

    public Switch(boolean initial) {
        getStyleClass().add("switch");
        thumb.getStyleClass().add("switch-thumb");
        thumb.setMouseTransparent(true);
        setMinSize(36, 20);
        setPrefSize(36, 20);
        setMaxSize(36, 20);
        setAlignmentToStart();
        getChildren().add(thumb);
        setFocusTraversable(true);
        setAccessibleRole(javafx.scene.AccessibleRole.TOGGLE_BUTTON);
        slide.setInterpolator(Interpolator.EASE_BOTH);

        selected.addListener((observable, was, is) -> render(true));
        selected.set(initial);
        render(false);

        setOnMouseClicked(event -> selected.set(!selected.get()));
        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SPACE || event.getCode() == KeyCode.ENTER) {
                selected.set(!selected.get());
                event.consume();
            }
        });
    }

    private void setAlignmentToStart() {
        setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    }

    private void render(boolean animate) {
        boolean on = selected.get();
        pseudoClassStateChanged(ON, on);
        double target = on ? 16 : 0;
        slide.stop();
        if (animate) {
            slide.setToX(target);
            slide.play();
        } else {
            thumb.setTranslateX(target);
        }
        setAccessibleText(on ? "On" : "Off");
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean value) {
        selected.set(value);
    }
}
