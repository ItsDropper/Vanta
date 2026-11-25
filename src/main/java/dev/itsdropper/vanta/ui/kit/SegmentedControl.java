package dev.itsdropper.vanta.ui.kit;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;

import java.util.List;
import java.util.function.Function;

/** Mutually exclusive option strip. Exactly one option stays selected. */
public final class SegmentedControl<T> extends HBox {

    private final ToggleGroup group = new ToggleGroup();
    private final ObjectProperty<T> value = new SimpleObjectProperty<>();
    private boolean syncing;

    public SegmentedControl(List<T> options, Function<T, String> labels, T initial) {
        getStyleClass().add("segmented");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(2);
        for (T option : options) {
            ToggleButton button = new ToggleButton(labels.apply(option));
            button.setUserData(option);
            button.setToggleGroup(group);
            button.setFocusTraversable(true);
            getChildren().add(button);
        }
        group.selectedToggleProperty().addListener((observable, previous, current) -> {
            if (current == null) {
                // Keep one segment selected: re-select what was active.
                if (previous != null) {
                    group.selectToggle(previous);
                }
                return;
            }
            @SuppressWarnings("unchecked")
            T selected = (T) current.getUserData();
            syncing = true;
            value.set(selected);
            syncing = false;
        });
        value.addListener((observable, previous, current) -> {
            if (syncing) {
                return;
            }
            for (var toggle : group.getToggles()) {
                if (java.util.Objects.equals(toggle.getUserData(), current)) {
                    group.selectToggle(toggle);
                }
            }
        });
        value.set(initial != null ? initial : (options.isEmpty() ? null : options.get(0)));
        for (var toggle : group.getToggles()) {
            if (java.util.Objects.equals(toggle.getUserData(), value.get())) {
                group.selectToggle(toggle);
            }
        }
    }

    public ObjectProperty<T> valueProperty() {
        return value;
    }

    public T getValue() {
        return value.get();
    }

    public void setValue(T next) {
        value.set(next);
    }
}
