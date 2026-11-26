package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.launcher.instance.InstanceManager;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.service.InstanceUsageManager;
import dev.itsdropper.vanta.ui.LauncherViewLifecycle;
import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.async.UiExecutor;
import dev.itsdropper.vanta.ui.components.PageHeader;
import dev.itsdropper.vanta.ui.kit.Badge;
import dev.itsdropper.vanta.ui.kit.Card;
import dev.itsdropper.vanta.ui.kit.CoverArt;
import dev.itsdropper.vanta.ui.kit.StatTile;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Playtime overview: totals, when the player plays, the last two weeks, and
 * time per instance. History starts from the first session recorded after
 * this view shipped; older totals still count toward the per-instance figures.
 */
public final class StatisticsView extends StackPane implements LauncherViewLifecycle {

    private static final double CHART_HEIGHT = 120;

    private final VBox content = new VBox(20);
    private final AtomicLong refreshSequence = new AtomicLong();

    public StatisticsView() {
        getStyleClass().addAll("page", "stats-page");

        content.setFillWidth(true);
        content.setMaxWidth(Double.MAX_VALUE);
        content.setPadding(new Insets(24, 28, 28, 28));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("stats-scroll");
        getChildren().add(scroll);

        render(List.of());
    }

    @Override
    public void onShown() {
        long request = refreshSequence.incrementAndGet();
        UiExecutor.shared().execute(() -> {
            List<Instance> instances;
            try {
                instances = InstanceManager.discoverInstances();
            } catch (Throwable failure) {
                instances = List.of();
            }
            List<Instance> found = instances;
            UiExecutor.shared().dispatchToUi(() -> {
                if (request == refreshSequence.get()) {
                    render(found);
                }
            });
        });
    }

    @Override
    public void onHidden() {
        refreshSequence.incrementAndGet();
    }

    private void render(List<Instance> instances) {
        List<Instance> ranked = instances.stream()
                .filter(instance -> InstanceUsageManager.getPlaytimeSeconds(instance) > 0)
                .sorted(Comparator.comparingLong(InstanceUsageManager::getPlaytimeSeconds).reversed())
                .toList();

        long total = instances.stream().mapToLong(InstanceUsageManager::getPlaytimeSeconds).sum();
        long[] daily = InstanceUsageManager.getDailySeconds(14);
        long week = 0;
        for (int i = daily.length - 7; i < daily.length; i++) {
            week += daily[i];
        }

        content.getChildren().setAll(
                new PageHeader(
                        Localization.text("stats.title"),
                        Localization.text("stats.subtitle"),
                        "", ""),
                tiles(total, week, instances.size(), ranked),
                charts(daily),
                perInstance(ranked, total));
    }

    private Node tiles(long total, long week, int instanceCount, List<Instance> ranked) {
        String top = ranked.isEmpty() ? "None yet" : ranked.get(0).getName();
        String topHint = ranked.isEmpty()
                ? Localization.text("stats.noPlaytime")
                : InstanceUsageManager.formatPlaytime(InstanceUsageManager.getPlaytimeSeconds(ranked.get(0)));
        HBox row = new HBox(14,
                grow(new StatTile(Localization.text("stats.totalPlaytime"), InstanceUsageManager.formatPlaytime(total), "")),
                grow(new StatTile(Localization.text("stats.lastWeek"), InstanceUsageManager.formatPlaytime(week), "")),
                grow(new StatTile(Localization.text("stats.instances"), String.valueOf(instanceCount), "")),
                grow(new StatTile(Localization.text("stats.mostPlayed"), top, topHint)));
        row.setFillHeight(true);
        return row;
    }

    private Node charts(long[] daily) {
        long[] hourly = InstanceUsageManager.getHourlySeconds();

        String[] hourLabels = new String[24];
        String[] hourTips = new String[24];
        double[] hourValues = new double[24];
        for (int h = 0; h < 24; h++) {
            hourValues[h] = hourly[h];
            hourLabels[h] = h % 6 == 0 ? String.format(Locale.ROOT, "%02d", h) : "";
            hourTips[h] = String.format(Locale.ROOT, "%02d:00  %s", h, InstanceUsageManager.formatPlaytime(hourly[h]));
        }

        String[] dayLabels = new String[daily.length];
        String[] dayTips = new String[daily.length];
        double[] dayValues = new double[daily.length];
        LocalDate today = LocalDate.now();
        for (int i = 0; i < daily.length; i++) {
            LocalDate day = today.minusDays(daily.length - 1L - i);
            dayValues[i] = daily[i];
            dayLabels[i] = day.getDayOfWeek().getDisplayName(TextStyle.NARROW, Locale.getDefault());
            dayTips[i] = day + "  " + InstanceUsageManager.formatPlaytime(daily[i]);
        }

        Card when = Card.titled(Localization.text("stats.whenYouPlay"), "",
                bars(hourValues, hourLabels, hourTips));
        Card perDay = Card.titled(Localization.text("stats.dailyPlaytime"), "",
                bars(dayValues, dayLabels, dayTips));
        HBox row = new HBox(14, grow(when), grow(perDay));
        return row;
    }

    /** A bottom-aligned bar chart; every bar has a tooltip and a mono label under it. */
    private static Node bars(double[] values, String[] labels, String[] tips) {
        double max = 0;
        for (double value : values) {
            max = Math.max(max, value);
        }
        HBox bars = new HBox(4);
        bars.setAlignment(Pos.BOTTOM_CENTER);
        bars.setMinHeight(CHART_HEIGHT + 20);
        bars.setPrefHeight(CHART_HEIGHT + 20);
        for (int i = 0; i < values.length; i++) {
            Region bar = new Region();
            bar.getStyleClass().add(values[i] > 0 ? "stats-bar" : "stats-bar-empty");
            double h = max <= 0 ? 3 : Math.max(3, values[i] / max * CHART_HEIGHT);
            bar.setMinHeight(h);
            bar.setPrefHeight(h);
            bar.setMaxHeight(h);
            bar.setMaxWidth(Double.MAX_VALUE);

            Label label = new Label(labels[i]);
            label.getStyleClass().add("stats-axis");
            label.setMinHeight(16);
            label.setAlignment(Pos.CENTER);
            label.setMaxWidth(Double.MAX_VALUE);

            VBox column = new VBox(4, bar, label);
            column.setAlignment(Pos.BOTTOM_CENTER);
            HBox.setHgrow(column, Priority.ALWAYS);
            column.setMinWidth(0);
            Tooltip.install(column, new Tooltip(tips[i]));
            bars.getChildren().add(column);
        }
        return bars;
    }

    private Node perInstance(List<Instance> ranked, long total) {
        VBox rows = new VBox(6);
        if (ranked.isEmpty()) {
            Label empty = new Label(Localization.text("stats.noPlaytime"));
            empty.getStyleClass().add("stats-axis");
            rows.getChildren().add(empty);
        }
        long top = ranked.isEmpty() ? 1 : InstanceUsageManager.getPlaytimeSeconds(ranked.get(0));
        for (Instance instance : ranked.stream().limit(8).toList()) {
            long seconds = InstanceUsageManager.getPlaytimeSeconds(instance);

            CoverArt thumb = new CoverArt(instance.getName(), instance.getLoader()).rounded(6).sized(64, 36);
            Label name = new Label(instance.getName());
            name.getStyleClass().add("stats-row-name");
            name.setMinWidth(0);

            Region track = new Region();
            track.getStyleClass().add("stats-track");
            Region fill = new Region();
            fill.getStyleClass().add("stats-fill");
            StackPane meter = new StackPane(track, fill);
            meter.setMinHeight(6);
            meter.setPrefHeight(6);
            meter.setMaxHeight(6);
            StackPane.setAlignment(fill, Pos.CENTER_LEFT);
            fill.maxWidthProperty().bind(meter.widthProperty().multiply(Math.max(0.02, seconds / (double) top)));
            fill.prefWidthProperty().bind(fill.maxWidthProperty());
            fill.setMinWidth(0);

            VBox info = new VBox(6, name, meter);
            info.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(info, Priority.ALWAYS);
            info.setMinWidth(0);

            Label time = new Label(InstanceUsageManager.formatPlaytime(seconds));
            time.getStyleClass().add("value-mono-strong");
            time.setMinWidth(64);
            time.setAlignment(Pos.CENTER_RIGHT);

            HBox row = new HBox(14, thumb, info, Badge.loader(instance.getLoader()), time);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("stats-row");
            rows.getChildren().add(row);
        }
        return Card.titled(Localization.text("stats.byInstance"), "", rows);
    }

    private static Node grow(Node node) {
        HBox.setHgrow(node, Priority.ALWAYS);
        if (node instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
            region.setPrefWidth(0);
            region.setMinWidth(0);
        }
        return node;
    }
}
