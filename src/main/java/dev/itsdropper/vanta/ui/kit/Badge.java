package dev.itsdropper.vanta.ui.kit;

import javafx.scene.control.Label;
import javafx.scene.shape.Circle;

import java.util.Locale;

/** Small inline status or metadata label. */
public final class Badge extends Label {

    public enum Tone { NEUTRAL, ACCENT, SUCCESS, WARNING, DANGER, INFO }

    public Badge(String text) {
        this(text, Tone.NEUTRAL);
    }

    public Badge(String text, Tone tone) {
        super(text == null ? "" : text);
        getStyleClass().addAll("badge", "badge-" + tone.name().toLowerCase(Locale.ROOT));
    }

    /** Badge for values such as versions or sizes, set in the mono face. */
    public static Badge mono(String text) {
        Badge badge = new Badge(text, Tone.NEUTRAL);
        badge.getStyleClass().add("badge-mono");
        return badge;
    }

    /** Loader badge with its identifying hue. */
    public static Badge loader(String loader) {
        Badge badge = new Badge(LoaderTheme.label(loader), Tone.NEUTRAL);
        badge.getStyleClass().addAll("badge-loader", "badge-loader-" + LoaderTheme.key(loader));
        if (LoaderTheme.isVanta(loader)) {
            badge.setGraphic(VantaLogo.clientMark(13));
        } else {
            Circle dot = new Circle(3);
            dot.getStyleClass().add("badge-dot");
            badge.setGraphic(dot);
        }
        badge.setGraphicTextGap(6);
        return badge;
    }

    /** Status badge led by a coloured dot. */
    public static Badge status(String text, Tone tone) {
        Badge badge = new Badge(text, tone);
        Circle dot = new Circle(3);
        dot.getStyleClass().add("badge-dot");
        badge.setGraphic(dot);
        badge.setGraphicTextGap(6);
        return badge;
    }
}
