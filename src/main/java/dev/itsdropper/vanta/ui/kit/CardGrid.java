package dev.itsdropper.vanta.ui.kit;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;

/**
 * Responsive card grid: up to {@code maxColumns} equal columns, each at least
 * {@code minCard} wide. In list mode every card takes a full row.
 */
public final class CardGrid extends FlowPane {

    private final double gap;
    private final double minCard;
    private final int maxColumns;
    private boolean grid = true;

    public CardGrid(double gap, double minCard, int maxColumns) {
        super(gap, gap);
        this.gap = gap;
        this.minCard = minCard;
        this.maxColumns = maxColumns;
        setAlignment(Pos.TOP_LEFT);
    }

    public void setGrid(boolean grid) {
        this.grid = grid;
        requestLayout();
    }

    @Override
    protected double computePrefHeight(double width) {
        applyWidths(width);
        return super.computePrefHeight(width);
    }

    @Override
    protected void layoutChildren() {
        applyWidths(getWidth());
        super.layoutChildren();
    }

    private void applyWidths(double width) {
        if (width <= 0) {
            return;
        }
        Insets in = getInsets();
        double available = width - in.getLeft() - in.getRight();
        if (available <= 0) {
            return;
        }
        int columns = grid
                ? Math.max(1, Math.min(maxColumns, (int) ((available + gap) / (minCard + gap))))
                : 1;
        double cell = Math.floor((available - (columns - 1) * gap) / columns);
        for (Node child : getChildren()) {
            if (child instanceof Region region) {
                region.setMinWidth(0);
                region.setPrefWidth(cell);
                region.setMaxWidth(cell);
            }
        }
        setPrefWrapLength(available);
    }
}
