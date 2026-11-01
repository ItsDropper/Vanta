package dev.itsdropper.vanta.ui.components;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Stop;

/** Lightweight, locally rendered Minecraft-inspired landscape. */
public final class MinecraftBackdrop extends StackPane {
    private final Canvas canvas = new Canvas();

    public MinecraftBackdrop() {
        getStyleClass().add("minecraft-backdrop");
        setMouseTransparent(true);
        canvas.setMouseTransparent(true);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.widthProperty().addListener((o, a, b) -> draw());
        canvas.heightProperty().addListener((o, a, b) -> draw());
        widthProperty().addListener((o, a, b) -> draw());
        heightProperty().addListener((o, a, b) -> draw());
        getChildren().add(canvas);
    }

    private void draw() {
        double w = getWidth(), h = getHeight();
        if (w <= 1 || h <= 1) return;
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#14283a")),
                new Stop(0.58, Color.web("#263d43")),
                new Stop(1, Color.web("#101b1b"))));
        g.fillRect(0, 0, w, h);

        g.setFill(Color.rgb(220, 235, 225, 0.62));
        double[][] stars = {{.55,.16,1.5},{.66,.10,1},{.76,.22,1.2},{.87,.12,1},{.94,.28,1.4},{.48,.28,1},{.70,.34,.9},{.91,.42,.8}};
        for (double[] star : stars) g.fillOval(w * star[0], h * star[1], star[2], star[2]);
        g.setFill(Color.rgb(220, 226, 195, .72));
        g.fillOval(w * .82, h * .12, h * .20, h * .20);

        g.setFill(Color.web("#243b3b"));
        polygon(g,w,h,new double[][]{{0,.72},{.10,.55},{.18,.65},{.29,.43},{.40,.68},{.52,.50},{.63,.68},{.76,.48},{.88,.64},{1,.48},{1,1},{0,1}});
        g.setFill(Color.web("#182e2c"));
        polygon(g,w,h,new double[][]{{0,.82},{.13,.70},{.24,.78},{.38,.62},{.51,.78},{.65,.64},{.79,.80},{.91,.66},{1,.75},{1,1},{0,1}});

        g.setFill(Color.web("#102522"));
        double[] trees = {.08,.17,.31,.44,.61,.72,.91};
        for (int i=0;i<trees.length;i++) {
            double x=w*trees[i], base=h*(.84+(i%2)*.04), tree=h*(.16+(i%3)*.025);
            g.fillRect(x+w*.008,base-tree*.36,w*.012,tree*.48);
            g.fillRect(x-w*.012,base-tree,w*.052,tree*.56);
            g.fillRect(x-w*.004,base-tree*1.24,w*.036,tree*.40);
        }
        g.setFill(new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,
                new Stop(0,Color.rgb(9,22,20,.18)),new Stop(1,Color.rgb(5,12,12,.92))));
        g.fillRect(0,h*.68,w,h*.32);
        g.setFill(new LinearGradient(0,0,1,0,true,CycleMethod.NO_CYCLE,
                new Stop(0,Color.rgb(7,13,14,.86)),new Stop(.48,Color.rgb(7,13,14,.36)),new Stop(1,Color.rgb(7,13,14,.12))));
        g.fillRect(0,0,w,h);
    }

    private static void polygon(GraphicsContext g,double w,double h,double[][] points) {
        double[] x=new double[points.length], y=new double[points.length];
        for(int i=0;i<points.length;i++){x[i]=points[i][0]*w;y[i]=points[i][1]*h;}
        g.fillPolygon(x,y,points.length);
    }
}
