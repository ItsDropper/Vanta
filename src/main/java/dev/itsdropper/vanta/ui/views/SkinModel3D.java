package dev.itsdropper.vanta.ui.views;

import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
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

    public enum Layer {
        HAT,
        JACKET,
        SLEEVES,
        TROUSERS
    }


    private final Group player = new Group();
    private final java.util.EnumMap<Layer, Group> layerGroups =
            new java.util.EnumMap<>(Layer.class);
    private final Rotate yaw = new Rotate(-22, Rotate.Y_AXIS);
    private final Rotate pitch = new Rotate(-8, Rotate.X_AXIS);
    private Image skin;
    private Image cape;
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
        camera.setTranslateZ(-190);
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
            camera.setTranslateZ(clamp(camera.getTranslateZ() + event.getDeltaY() * 0.12, -280, -125));
            event.consume();
        });

        getChildren().add(scene);
    }

    public void setSkin(Image skin, boolean slimArms) {
        this.skin = skin;
        this.slimArms = slimArms;
        rebuild();
    }

    public void setCape(Image cape) {
        this.cape = cape;
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
        layerGroups.clear();
        if (skin == null || skin.isError() || skin.getPixelReader() == null
                || skin.getWidth() < 64 || skin.getHeight() < 32) {
            return;
        }

        atlasHeight = (int) skin.getHeight();
        PhongMaterial material = new PhongMaterial(Color.WHITE);
        material.setDiffuseMap(upscaleNearest(skin, 8));
        material.setSpecularColor(Color.rgb(25, 25, 25));

        if (atlasHeight >= 64) {
            // Head and transparent hat layer.
            addBox(material, 8, 8, 8, 0, -10, 0,
                    face(8, 8, 8, 8), face(24, 8, 8, 8),
                    face(0, 8, 8, 8), face(16, 8, 8, 8),
                    face(8, 0, 8, 8), face(16, 0, 8, 8));
            addLayerBox(Layer.HAT, material, 8.5, 8.5, 8.5, 0, -10, 0,
                    face(40, 8, 8, 8), face(56, 8, 8, 8),
                    face(32, 8, 8, 8), face(48, 8, 8, 8),
                    face(40, 0, 8, 8), face(48, 0, 8, 8));
        } else {
            // Legacy 64x32 skins have no hat or left-limb texture regions.
            slimArms = false;
            addBox(material, 8, 8, 8, 0, -10, 0,
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

        // Transparent second-layer jacket, sleeves, and trousers.
        if (atlasHeight >= 64) {
            addLayerBox(Layer.JACKET, material, 8.35, 12.35, 4.35, 0, 0, 0,
                    face(20, 36, 8, 12), face(32, 36, 8, 12),
                    face(16, 36, 4, 12), face(28, 36, 4, 12),
                    face(20, 32, 8, 4), face(28, 32, 8, 4));

            if (slimArms) {
                SkinAtlasLayout.Uv[] leftSleeve = SkinAtlasLayout.leftArmOverlay(true);
                addLayerBox(Layer.SLEEVES, material, 3.35, 12.35, 4.35, -5.5, 0, 0,
                        uv(leftSleeve[0]), uv(leftSleeve[1]), uv(leftSleeve[2]),
                        uv(leftSleeve[3]), uv(leftSleeve[4]), uv(leftSleeve[5]));
                addLayerBox(Layer.SLEEVES, material, 3.35, 12.35, 4.35, 5.5, 0, 0,
                        face(44, 36, 3, 12), face(52, 36, 3, 12),
                        face(40, 36, 4, 12), face(48, 36, 4, 12),
                        face(44, 32, 3, 4), face(47, 32, 3, 4));
            } else {
                SkinAtlasLayout.Uv[] leftSleeve = SkinAtlasLayout.leftArmOverlay(false);
                addLayerBox(Layer.SLEEVES, material, 4.35, 12.35, 4.35, -6, 0, 0,
                        uv(leftSleeve[0]), uv(leftSleeve[1]), uv(leftSleeve[2]),
                        uv(leftSleeve[3]), uv(leftSleeve[4]), uv(leftSleeve[5]));
                addLayerBox(Layer.SLEEVES, material, 4.35, 12.35, 4.35, 6, 0, 0,
                        face(44, 36, 4, 12), face(52, 36, 4, 12),
                        face(40, 36, 4, 12), face(48, 36, 4, 12),
                        face(44, 32, 4, 4), face(48, 32, 4, 4));
            }

            addLayerBox(Layer.TROUSERS, material, 4.35, 12.35, 4.35, -2, 12, 0,
                    face(4, 36, 4, 12), face(12, 36, 4, 12),
                    face(0, 36, 4, 12), face(8, 36, 4, 12),
                    face(4, 32, 4, 4), face(8, 32, 4, 4));
            addLayerBox(Layer.TROUSERS, material, 4.35, 12.35, 4.35, 2, 12, 0,
                    face(20, 52, 4, 12), face(28, 52, 4, 12),
                    face(16, 52, 4, 12), face(24, 52, 4, 12),
                    face(20, 48, 4, 4), face(24, 48, 4, 4));
        }

        if (cape != null && !cape.isError() && cape.getPixelReader() != null
                && cape.getWidth() >= 22 && cape.getHeight() >= 17) {
            addCape(cape);
        }

        // Scale pixel-sized geometry to make the player comfortably visible.
        player.setScaleX(2.6);
        player.setScaleY(2.6);
        player.setScaleZ(2.6);
        player.setTranslateY(5);
    }

    private void addCape(Image source) {
        PhongMaterial capeMaterial = new PhongMaterial(Color.WHITE);
        capeMaterial.setDiffuseMap(upscaleNearest(source, 8));
        capeMaterial.setSpecularColor(Color.BLACK);

        // A Minecraft cape is cloth, not a single rigid card. Build front and back
        // surfaces from horizontal segments so it hangs away from the player's back.
        TriangleMesh mesh = new TriangleMesh();
        final int segments = 5;
        final float topY = -5.5f;
        final float bottomY = 12.0f;
        final float halfWidth = 5.0f;
        final float thickness = 0.22f;
        for (int side = 0; side < 2; side++) {
            for (int row = 0; row <= segments; row++) {
                float t = row / (float) segments;
                float y = topY + (bottomY - topY) * t;
                // The lower cloth hangs farther back; subtle alternating offsets
                // create a soft cloth-like silhouette without distorting the texture.
                float z = 2.25f + 0.25f * t + 2.1f * t * t + 0.12f * (float) Math.sin(t * Math.PI);
                if (side == 1) z += thickness;
                float width = halfWidth * (1.0f - 0.035f * t);
                mesh.getPoints().addAll(-width, y, z, width, y, z);
            }
        }

        // Standard 64x32 cape atlas: front at (1,1), reverse at (12,1).
        for (int side = 0; side < 2; side++) {
            int pointBase = side * (segments + 1) * 2;
            float uLeft = (side == 0 ? 1f : 12f) / 64f;
            float uRight = (side == 0 ? 11f : 22f) / 64f;
            float vTop = 1f / 32f;
            float vBottom = 17f / 32f;
            for (int row = 0; row < segments; row++) {
                int p0 = pointBase + row * 2;
                int p1 = p0 + 1;
                int p2 = p0 + 3;
                int p3 = p0 + 2;
                int t0 = mesh.getTexCoords().size() / 2;
                float v0 = vTop + (vBottom - vTop) * row / segments;
                float v1 = vTop + (vBottom - vTop) * (row + 1) / segments;
                mesh.getTexCoords().addAll(uLeft, v0, uRight, v0, uRight, v1, uLeft, v1);
                if (side == 0) {
                    mesh.getFaces().addAll(p0, t0, p1, t0 + 1, p2, t0 + 2,
                            p0, t0, p2, t0 + 2, p3, t0 + 3);
                } else {
                    mesh.getFaces().addAll(p0, t0, p2, t0 + 2, p1, t0 + 1,
                            p0, t0, p3, t0 + 3, p2, t0 + 2);
                }
                mesh.getFaceSmoothingGroups().addAll(0, 0);
            }
        }

        // Close the cloth edges with narrow textured side strips.
        for (int edge = 0; edge < 2; edge++) {
            for (int row = 0; row < segments; row++) {
                int frontBase = row * 2;
                int backBase = (segments + 1) * 2 + row * 2;
                int frontNext = frontBase + 2;
                int backNext = backBase + 2;
                int left = edge == 0 ? frontBase : frontBase + 1;
                int leftNext = edge == 0 ? frontNext : frontNext + 1;
                int back = edge == 0 ? backBase : backBase + 1;
                int backNextEdge = edge == 0 ? backNext : backNext + 1;
                int t = mesh.getTexCoords().size() / 2;
                mesh.getTexCoords().addAll(1f / 64f, 1f / 32f,
                        2f / 64f, 1f / 32f, 2f / 64f, 17f / 32f, 1f / 64f, 17f / 32f);
                mesh.getFaces().addAll(left, t, back, t + 1, backNextEdge, t + 2,
                        left, t, backNextEdge, t + 2, leftNext, t + 3);
                mesh.getFaceSmoothingGroups().addAll(0, 0);
            }
        }

        MeshView view = new MeshView(mesh);
        view.setMaterial(capeMaterial);
        view.setCullFace(CullFace.NONE);
        player.getChildren().add(view);
    }

    private static Image upscaleNearest(Image source, int scale) {
        if (source == null || source.isError() || source.getPixelReader() == null) return source;
        int width = (int) source.getWidth();
        int height = (int) source.getHeight();
        PixelReader reader = source.getPixelReader();
        WritableImage result = new WritableImage(width * scale, height * scale);
        PixelWriter writer = result.getPixelWriter();
        for (int y = 0; y < height * scale; y++) {
            for (int x = 0; x < width * scale; x++) {
                writer.setArgb(x, y, reader.getArgb(x / scale, y / scale));
            }
        }
        return result;
    }
    private void addBox(PhongMaterial material,
                        double width, double height, double depth,
                        double x, double y, double z,
                        Uv front, Uv back, Uv right, Uv left, Uv top, Uv bottom) {
        addBox(player, material, width, height, depth, x, y, z,
                front, back, right, left, top, bottom);
    }

    private void addLayerBox(Layer layer, PhongMaterial material,
                             double width, double height, double depth,
                             double x, double y, double z,
                             Uv front, Uv back, Uv right, Uv left, Uv top, Uv bottom) {
        Group group = layerGroups.computeIfAbsent(layer, key -> {
            Group created = new Group();
            created.setVisible(true);
            player.getChildren().add(created);
            return created;
        });
        addBox(group, material, width, height, depth, x, y, z,
                front, back, right, left, top, bottom);
    }

    private void addBox(Group target, PhongMaterial material,
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
        target.getChildren().add(view);
    }

    private void addFace(TriangleMesh mesh, List<Float> points, List<Float> texCoords,
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

    private Uv uv(SkinAtlasLayout.Uv uv) {
        return face(uv.x(), uv.y(), uv.width(), uv.height());
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
