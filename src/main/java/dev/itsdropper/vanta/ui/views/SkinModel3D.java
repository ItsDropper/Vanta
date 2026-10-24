package dev.itsdropper.vanta.ui.views;

import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;

import java.util.ArrayList;
import java.util.List;

/**
 * A lightweight, texture-mapped Minecraft player preview.
 * Drag horizontally to rotate around the player and vertically to change the view angle.
 */
public final class SkinModel3D extends StackPane {

    private final Group player = new Group();
    private final Rotate yaw = new Rotate(-22, Rotate.Y_AXIS);
    private final Rotate pitch = new Rotate(-8, Rotate.X_AXIS);
    private Image skin;
    private boolean slimArms;
    private int atlasHeight = 64;

    public SkinModel3D() {
        setMinSize(240, 300);
        setPrefSize(280, 340);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setStyle("-fx-background-color: transparent;");

        player.getTransforms().addAll(yaw, pitch);
        Group world = new Group(player);
        world.getChildren().add(new AmbientLight(Color.rgb(190, 190, 190)));
        PointLight keyLight = new PointLight(Color.WHITE);
        keyLight.setTranslateX(-45);
        keyLight.setTranslateY(-60);
        keyLight.setTranslateZ(-55);
        world.getChildren().add(keyLight);

        SubScene scene = new SubScene(world, 280, 340, true, SceneAntialiasing.BALANCED);
        scene.setFill(Color.TRANSPARENT);
        scene.widthProperty().bind(widthProperty());
        scene.heightProperty().bind(heightProperty());

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(1000);
        camera.setTranslateZ(-112);
        scene.setCamera(camera);

        scene.setOnMousePressed(event -> {
            scene.setUserData(new double[]{event.getSceneX(), event.getSceneY(), yaw.getAngle(), pitch.getAngle()});
            event.consume();
        });
        scene.setOnMouseDragged(event -> {
            Object data = scene.getUserData();
            if (data instanceof double[] start) {
                yaw.setAngle(start[2] + (event.getSceneX() - start[0]) * 0.8);
                pitch.setAngle(clamp(start[3] - (event.getSceneY() - start[1]) * 0.55, -65, 65));
            }
            event.consume();
        });
        scene.setOnScroll(event -> {
            camera.setTranslateZ(clamp(camera.getTranslateZ() + event.getDeltaY() * 0.12, -170, -72));
            event.consume();
        });

        getChildren().add(scene);
    }

    public void setSkin(Image skin, boolean slimArms) {
        this.skin = skin;
        this.slimArms = slimArms;
        rebuild();
    }

    public void setSkinModel(boolean slimArms) {
        if (this.slimArms != slimArms) {
            this.slimArms = slimArms;
            rebuild();
        }
    }

    private void rebuild() {
        player.getChildren().clear();
        if (skin == null || skin.isError() || skin.getPixelReader() == null
                || skin.getWidth() < 64 || skin.getHeight() < 64) {
            return;
        }

        atlasHeight = (int) skin.getHeight();
        PhongMaterial material = new PhongMaterial(Color.WHITE);
        material.setDiffuseMap(skin);
        material.setSpecularColor(Color.rgb(25, 25, 25));

        if (atlasHeight >= 64) {
            // Head and transparent hat layer.
            addBox(material, 8, 8, 8, 0, -12, 0,
                    face(8, 8, 8, 8), face(24, 8, 8, 8),
                    face(0, 8, 8, 8), face(16, 8, 8, 8),
                    face(8, 0, 8, 8), face(16, 0, 8, 8));
            addBox(material, 8.5, 8.5, 8.5, 0, -12, 0,
                    face(40, 8, 8, 8), face(56, 8, 8, 8),
                    face(32, 8, 8, 8), face(48, 8, 8, 8),
                    face(40, 0, 8, 8), face(48, 0, 8, 8));
        } else {
            // Legacy 64x32 skins have no hat or left-limb texture regions.
            slimArms = false;
            addBox(material, 8, 8, 8, 0, -12, 0,
                    face(8, 8, 8, 8), face(24, 8, 8, 8),
                    face(0, 8, 8, 8), face(16, 8, 8, 8),
                    face(8, 0, 8, 8), face(16, 0, 8, 8));
        }

        // Torso (front, back, right, left, top, bottom UV rectangles).
        addBox(material, 8, 12, 4, 0, 0, 0,
                face(20, 20, 8, 12), face(32, 20, 8, 12),
                face(16, 20, 4, 12), face(28, 20, 4, 12),
                face(20, 16, 8, 4), face(28, 16, 8, 4));

        if (atlasHeight >= 64 && slimArms) {
            addBox(material, 3, 12, 4, -5.5, 0, 0,
                    face(36, 52, 3, 12), face(44, 52, 3, 12),
                    face(32, 52, 4, 12), face(40, 52, 4, 12),
                    face(36, 48, 3, 4), face(39, 48, 3, 4));
            addBox(material, 3, 12, 4, 5.5, 0, 0,
                    face(44, 20, 3, 12), face(52, 20, 3, 12),
                    face(40, 20, 4, 12), face(48, 20, 4, 12),
                    face(44, 16, 3, 4), face(47, 16, 3, 4));
        } else if (atlasHeight >= 64) {
            addBox(material, 4, 12, 4, -6, 0, 0,
                    face(36, 52, 4, 12), face(44, 52, 4, 12),
                    face(32, 52, 4, 12), face(40, 52, 4, 12),
                    face(36, 48, 4, 4), face(40, 48, 4, 4));
            addBox(material, 4, 12, 4, 6, 0, 0,
                    face(44, 20, 4, 12), face(52, 20, 4, 12),
                    face(40, 20, 4, 12), face(48, 20, 4, 12),
                    face(44, 16, 4, 4), face(48, 16, 4, 4));
        } else {
            addBox(material, 4, 12, 4, -6, 0, 0,
                    face(44, 20, 4, 12), face(52, 20, 4, 12),
                    face(40, 20, 4, 12), face(48, 20, 4, 12),
                    face(44, 16, 4, 4), face(48, 16, 4, 4));
            addBox(material, 4, 12, 4, 6, 0, 0,
                    face(44, 20, 4, 12), face(52, 20, 4, 12),
                    face(40, 20, 4, 12), face(48, 20, 4, 12),
                    face(44, 16, 4, 4), face(48, 16, 4, 4));
        }

        // Legs: modern skins have independent left-leg UVs; legacy skins mirror the right leg.
        addBox(material, 4, 12, 4, -2, 12, 0,
                face(4, 20, 4, 12), face(12, 20, 4, 12),
                face(0, 20, 4, 12), face(8, 20, 4, 12),
                face(4, 16, 4, 4), face(8, 16, 4, 4));
        if (atlasHeight >= 64) {
            addBox(material, 4, 12, 4, 2, 12, 0,
                    face(20, 52, 4, 12), face(28, 52, 4, 12),
                    face(16, 52, 4, 12), face(24, 52, 4, 12),
                    face(20, 48, 4, 4), face(24, 48, 4, 4));
        } else {
            addBox(material, 4, 12, 4, 2, 12, 0,
                    face(4, 20, 4, 12), face(12, 20, 4, 12),
                    face(0, 20, 4, 12), face(8, 20, 4, 12),
                    face(4, 16, 4, 4), face(8, 16, 4, 4));
        }

        // Scale pixel-sized geometry to make the player comfortably visible.
        player.setScaleX(3.4);
        player.setScaleY(3.4);
        player.setScaleZ(3.4);
        player.setTranslateY(5);
    }

    private void addBox(PhongMaterial material,
                        double width, double height, double depth,
                        double x, double y, double z,
                        Uv front, Uv back, Uv right, Uv left, Uv top, Uv bottom) {
        TriangleMesh mesh = new TriangleMesh();
        List<Float> points = new ArrayList<>(72);
        List<Float> texCoords = new ArrayList<>(48);
        List<Integer> faces = new ArrayList<>(72);

        double x0 = -width / 2, x1 = width / 2;
        double y0 = -height / 2, y1 = height / 2;
        double z0 = -depth / 2, z1 = depth / 2;

        // Each face has independent vertices so every face can use its own atlas rectangle.
        addFace(mesh, points, texCoords, faces,
                new double[][]{{x0,y0,z0},{x1,y0,z0},{x1,y1,z0},{x0,y1,z0}}, front);
        addFace(mesh, points, texCoords, faces,
                new double[][]{{x1,y0,z1},{x0,y0,z1},{x0,y1,z1},{x1,y1,z1}}, back);
        addFace(mesh, points, texCoords, faces,
                new double[][]{{x1,y0,z0},{x1,y0,z1},{x1,y1,z1},{x1,y1,z0}}, right);
        addFace(mesh, points, texCoords, faces,
                new double[][]{{x0,y0,z1},{x0,y0,z0},{x0,y1,z0},{x0,y1,z1}}, left);
        addFace(mesh, points, texCoords, faces,
                new double[][]{{x0,y0,z1},{x1,y0,z1},{x1,y0,z0},{x0,y0,z0}}, top);
        addFace(mesh, points, texCoords, faces,
                new double[][]{{x0,y1,z0},{x1,y1,z0},{x1,y1,z1},{x0,y1,z1}}, bottom);

        mesh.getPoints().setAll(toFloatArray(points));
        mesh.getTexCoords().setAll(toFloatArray(texCoords));
        mesh.getFaces().setAll(toIntArray(faces));
        mesh.getFaceSmoothingGroups().setAll(new int[faces.size() / 6]);

        MeshView view = new MeshView(mesh);
        view.setMaterial(material);
        view.setCullFace(CullFace.NONE);
        view.setTranslateX(x);
        view.setTranslateY(y);
        view.setTranslateZ(z);
        player.getChildren().add(view);
    }

    private static void addFace(TriangleMesh mesh, List<Float> points, List<Float> texCoords,
                                List<Integer> faces, double[][] corners, Uv uv) {
        int pointStart = points.size() / 3;
        int texStart = texCoords.size() / 2;
        for (double[] point : corners) {
            points.add((float) point[0]);
            points.add((float) point[1]);
            points.add((float) point[2]);
        }
        float u0 = uv.x / 64f, v0 = uv.y / (float) atlasHeight;
        float u1 = (uv.x + uv.width) / 64f, v1 = (uv.y + uv.height) / (float) atlasHeight;
        texCoords.add(u0); texCoords.add(v0);
        texCoords.add(u1); texCoords.add(v0);
        texCoords.add(u1); texCoords.add(v1);
        texCoords.add(u0); texCoords.add(v1);
        faces.add(pointStart); faces.add(texStart);
        faces.add(pointStart + 1); faces.add(texStart + 1);
        faces.add(pointStart + 2); faces.add(texStart + 2);
        faces.add(pointStart); faces.add(texStart);
        faces.add(pointStart + 2); faces.add(texStart + 2);
        faces.add(pointStart + 3); faces.add(texStart + 3);
    }

    private Uv face(int x, int y, int width, int height) {
        return new Uv(x, y, width, height);
    }

    private static float[] toFloatArray(List<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Uv(int x, int y, int width, int height) { }
}
