package org.example.ui.markdown;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextFlow;

import com.vladsch.flexmark.ast.BulletList;
import com.vladsch.flexmark.ast.BulletListItem;
import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.Emphasis;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.ListItem;
import com.vladsch.flexmark.ast.OrderedList;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.StrongEmphasis;
import com.vladsch.flexmark.ast.ThematicBreak;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class MarkdownRenderer {

    private static final double MAX_IMAGE_WIDTH = 900;

    /*
     * Badges such as Shields.io are normally SVGs.
     *
     * We display small badge images at a reasonable height
     * while larger screenshots are limited by MAX_IMAGE_WIDTH.
     */
    private static final double BADGE_MAX_HEIGHT = 32;

    private final Parser parser;
    private final HttpClient httpClient;

    public MarkdownRenderer() {

        MutableDataSet options =
                new MutableDataSet();

        parser =
                Parser.builder(options)
                        .build();

        httpClient =
                HttpClient.newBuilder()
                        .followRedirects(
                                HttpClient.Redirect.NORMAL
                        )
                        .build();
    }

    // =============================================================
    // RENDER
    // =============================================================

    public VBox render(String markdown) {

        VBox root =
                new VBox(10);

        root.setFillWidth(true);

        if (markdown == null
                || markdown.isBlank()) {

            Label empty =
                    new Label(
                            "This project does not have a description."
                    );

            empty.getStyleClass().add(
                    "mod-description-text"
            );

            root.getChildren().add(
                    empty
            );

            return root;
        }

        com.vladsch.flexmark.util.ast.Node document =
                parser.parse(markdown);

        for (
                com.vladsch.flexmark.util.ast.Node child =
                document.getFirstChild();

                child != null;

                child = child.getNext()
        ) {

            Node rendered =
                    renderNode(child);

            if (rendered != null) {

                root.getChildren().add(
                        rendered
                );
            }
        }

        return root;
    }

    // =============================================================
    // BLOCK NODE
    // =============================================================

    private Node renderNode(
            com.vladsch.flexmark.util.ast.Node node
    ) {

        if (node instanceof Heading heading) {

            return renderHeading(
                    heading
            );
        }

        if (node instanceof Paragraph paragraph) {

            return renderParagraph(
                    paragraph
            );
        }

        if (node instanceof BulletList list) {

            return renderBulletList(
                    list
            );
        }

        if (node instanceof OrderedList list) {

            return renderOrderedList(
                    list
            );
        }

        if (node instanceof ThematicBreak) {

            Label divider =
                    new Label();

            divider.setMaxWidth(
                    Double.MAX_VALUE
            );

            divider.getStyleClass().add(
                    "mod-description-divider"
            );

            return divider;
        }

        return null;
    }

    // =============================================================
    // HEADINGS
    // =============================================================

    private Label renderHeading(
            Heading heading
    ) {

        String text =
                heading.getText().toString();

        Label label =
                new Label(
                        text
                );

        label.setWrapText(
                true
        );

        label.getStyleClass().add(
                switch (heading.getLevel()) {

                    case 1 ->
                            "mod-description-h1";

                    case 2 ->
                            "mod-description-h2";

                    case 3 ->
                            "mod-description-h3";

                    default ->
                            "mod-description-heading";
                }
        );

        return label;
    }

    // =============================================================
    // PARAGRAPH
    // =============================================================

    private Node renderParagraph(
            Paragraph paragraph
    ) {

        /*
         * IMPORTANT:
         *
         * A Modrinth paragraph can contain:
         *
         * [Environment](https://...)
         * [image](https://img.shields.io/...)
         * normal text
         *
         * The image can be nested inside a Link node.
         *
         * TextFlow cannot contain ImageView/VBox nodes properly,
         * so paragraphs are rendered using FlowPane.
         *
         * FlowPane allows:
         *
         * Text
         * Hyperlinks
         * Images
         *
         * to exist together and wrap naturally.
         */

        FlowPane flow =
                new FlowPane();

        flow.setHgap(
                6
        );

        flow.setVgap(
                6
        );

        flow.setPrefWrapLength(
                850
        );

        flow.setMaxWidth(
                Double.MAX_VALUE
        );

        flow.getStyleClass().add(
                "mod-description-paragraph"
        );

        renderFlowChildren(
                paragraph.getFirstChild(),
                flow
        );

        return flow;
    }

    // =============================================================
    // FLOW CHILDREN
    // =============================================================

    private void renderFlowChildren(
            com.vladsch.flexmark.util.ast.Node node,
            FlowPane flow
    ) {

        for (
                com.vladsch.flexmark.util.ast.Node current =
                node;

                current != null;

                current = current.getNext()
        ) {

            renderFlowNode(
                    current,
                    flow
            );
        }
    }

    // =============================================================
    // FLOW NODE
    // =============================================================

    private void renderFlowNode(
            com.vladsch.flexmark.util.ast.Node current,
            FlowPane flow
    ) {

        // ---------------------------------------------------------
        // NORMAL TEXT
        // ---------------------------------------------------------

        if (current instanceof com.vladsch.flexmark.ast.Text text) {

            javafx.scene.text.Text rendered =
                    new javafx.scene.text.Text(
                            text.getChars().toString()
                    );

            rendered.getStyleClass().add(
                    "mod-description-text"
            );

            flow.getChildren().add(
                    rendered
            );

            return;
        }

        // ---------------------------------------------------------
        // BOLD
        // ---------------------------------------------------------

        if (current instanceof StrongEmphasis strong) {

            javafx.scene.text.Text rendered =
                    new javafx.scene.text.Text(
                            strong.getText().toString()
                    );

            rendered.getStyleClass().add(
                    "mod-description-bold"
            );

            flow.getChildren().add(
                    rendered
            );

            return;
        }

        // ---------------------------------------------------------
        // ITALIC
        // ---------------------------------------------------------

        if (current instanceof Emphasis emphasis) {

            javafx.scene.text.Text rendered =
                    new javafx.scene.text.Text(
                            emphasis.getText().toString()
                    );

            rendered.getStyleClass().add(
                    "mod-description-italic"
            );

            flow.getChildren().add(
                    rendered
            );

            return;
        }

        // ---------------------------------------------------------
        // CODE
        // ---------------------------------------------------------

        if (current instanceof Code code) {

            javafx.scene.text.Text rendered =
                    new javafx.scene.text.Text(
                            code.getText().toString()
                    );

            rendered.getStyleClass().add(
                    "mod-description-code"
            );

            flow.getChildren().add(
                    rendered
            );

            return;
        }

        // ---------------------------------------------------------
        // LINK
        // ---------------------------------------------------------

        if (current instanceof Link link) {

            /*
             * This is the important fix.
             *
             * Modrinth commonly has:
             *
             * [image](https://img.shields.io/...)
             *
             * Flexmark parses this as:
             *
             * Link
             *   └── Image
             *
             * Previously we converted the entire Link to a
             * Hyperlink and therefore lost the image.
             *
             * If the link contains an Image, render the Image.
             */

            com.vladsch.flexmark.util.ast.Node child =
                    link.getFirstChild();

            boolean containsImage =
                    containsImage(
                            link
                    );

            if (containsImage) {

                renderFlowChildren(
                        child,
                        flow
                );

                return;
            }

            /*
             * Normal text link.
             */

            Hyperlink hyperlink =
                    new Hyperlink(
                            link.getText().toString()
                    );

            hyperlink.getStyleClass().add(
                    "mod-description-link"
            );

            String url =
                    link.getUrl().toString();

            hyperlink.setOnAction(
                    event ->
                            openUrl(url)
            );

            flow.getChildren().add(
                    hyperlink
            );

            return;
        }

        // ---------------------------------------------------------
        // IMAGE
        // ---------------------------------------------------------

        if (current instanceof com.vladsch.flexmark.ast.Image image) {

            flow.getChildren().add(
                    renderInlineImage(
                            image
                    )
            );

            return;
        }

        // ---------------------------------------------------------
        // CHILDREN
        // ---------------------------------------------------------

        if (current.getFirstChild() != null) {

            renderFlowChildren(
                    current.getFirstChild(),
                    flow
            );
        }
    }

    // =============================================================
    // INLINE IMAGE
    // =============================================================

    private Node renderInlineImage(
            com.vladsch.flexmark.ast.Image markdownImage
    ) {

        String url =
                markdownImage
                        .getUrl()
                        .toString();

        ImageView imageView =
                new ImageView();

        imageView.setPreserveRatio(
                true
        );

        imageView.setSmooth(
                true
        );

        imageView.setCache(
                true
        );

        /*
         * Start with a small invisible placeholder.
         *
         * Once the image loads, its dimensions are updated.
         */

        imageView.setFitHeight(
                BADGE_MAX_HEIGHT
        );

        imageView.setOpacity(
                0
        );

        imageView.setCursor(
                javafx.scene.Cursor.HAND
        );

        imageView.setOnMouseClicked(
                event ->
                        openUrl(url)
        );

        loadImage(
                url,
                imageView
        );

        return imageView;
    }

    // =============================================================
    // CHECK FOR IMAGE
    // =============================================================

    private boolean containsImage(
            com.vladsch.flexmark.util.ast.Node node
    ) {

        for (
                com.vladsch.flexmark.util.ast.Node current =
                node.getFirstChild();

                current != null;

                current = current.getNext()
        ) {

            if (current instanceof com.vladsch.flexmark.ast.Image) {

                return true;
            }

            if (containsImage(current)) {

                return true;
            }
        }

        return false;
    }

    // =============================================================
    // LOAD IMAGE
    // =============================================================

    private void loadImage(
            String url,
            ImageView imageView
    ) {

        Thread thread =
                new Thread(() -> {

                    try {

                        System.out.println(
                                "[MarkdownRenderer] Loading image: "
                                        + url
                        );

                        URI uri =
                                URI.create(
                                        url
                                );

                        HttpRequest request =
                                HttpRequest.newBuilder(
                                                uri
                                        )
                                        .GET()
                                        .header(
                                                "User-Agent",
                                                "VantaLauncher/1.0"
                                        )
                                        .header(
                                                "Accept",
                                                "image/avif,image/webp,image/apng,image/svg+xml,image/png,image/jpeg,image/*,*/*;q=0.8"
                                        )
                                        .build();

                        HttpResponse<byte[]> response =
                                httpClient.send(
                                        request,
                                        HttpResponse.BodyHandlers
                                                .ofByteArray()
                                );

                        int status =
                                response.statusCode();

                        String contentType =
                                response.headers()
                                        .firstValue(
                                                "Content-Type"
                                        )
                                        .orElse(
                                                "unknown"
                                        );

                        System.out.println(
                                "[MarkdownRenderer] HTTP status: "
                                        + status
                        );

                        System.out.println(
                                "[MarkdownRenderer] Content-Type: "
                                        + contentType
                        );

                        if (status < 200
                                || status >= 300) {

                            Platform.runLater(() ->
                                    showInlineImageError(
                                            imageView,
                                            "HTTP "
                                                    + status
                                    )
                            );

                            return;
                        }

                        byte[] data =
                                response.body();

                        System.out.println(
                                "[MarkdownRenderer] Downloaded "
                                        + data.length
                                        + " bytes"
                        );

                        byte[] pngData;

                        /*
                         * SVG
                         *
                         * Shields.io and some other badge providers
                         * return SVG even though the URL does not end
                         * in ".svg".
                         */

                        if (isSvg(
                                data,
                                contentType
                        )) {

                            System.out.println(
                                    "[MarkdownRenderer] Detected SVG."
                            );

                            pngData =
                                    convertSvgToPng(
                                            data
                                    );

                        } else {

                            /*
                             * Normal PNG/JPEG/WebP/etc.
                             *
                             * ImageIO is used first so JavaFX receives
                             * a known PNG format.
                             */

                            java.awt.image.BufferedImage bufferedImage;

                            try (
                                    ByteArrayInputStream input =
                                            new ByteArrayInputStream(
                                                    data
                                            )
                            ) {

                                bufferedImage =
                                        javax.imageio.ImageIO.read(
                                                input
                                        );
                            }

                            if (bufferedImage == null) {

                                System.out.println(
                                        "[MarkdownRenderer] "
                                                + "ImageIO could not decode image."
                                );

                                Platform.runLater(() ->
                                        showInlineImageError(
                                                imageView,
                                                "Unsupported image format."
                                        )
                                );

                                return;
                            }

                            System.out.println(
                                    "[MarkdownRenderer] ImageIO decoded: "
                                            + bufferedImage.getWidth()
                                            + "x"
                                            + bufferedImage.getHeight()
                            );

                            ByteArrayOutputStream output =
                                    new ByteArrayOutputStream();

                            boolean written =
                                    javax.imageio.ImageIO.write(
                                            bufferedImage,
                                            "png",
                                            output
                                    );

                            if (!written) {

                                Platform.runLater(() ->
                                        showInlineImageError(
                                                imageView,
                                                "Failed to process image."
                                        )
                                );

                                return;
                            }

                            pngData =
                                    output.toByteArray();
                        }

                        /*
                         * JavaFX now receives PNG regardless of whether
                         * the original source was SVG, JPEG, PNG, etc.
                         */

                        Image image =
                                new Image(
                                        new ByteArrayInputStream(
                                                pngData
                                        )
                                );

                        if (image.isError()) {

                            System.out.println(
                                    "[MarkdownRenderer] "
                                            + "JavaFX could not decode converted PNG."
                            );

                            Platform.runLater(() ->
                                    showInlineImageError(
                                            imageView,
                                            "Failed to display image."
                                    )
                            );

                            return;
                        }

                        System.out.println(
                                "[MarkdownRenderer] Final image: "
                                        + image.getWidth()
                                        + "x"
                                        + image.getHeight()
                        );

                        Platform.runLater(() -> {

                            imageView.setImage(
                                    image
                            );

                            double width =
                                    image.getWidth();

                            double height =
                                    image.getHeight();

                            /*
                             * Small images/badges:
                             *
                             * Limit their height so badges don't become
                             * enormous.
                             */

                            if (height > 0
                                    && height <= 100) {

                                double scale =
                                        BADGE_MAX_HEIGHT
                                                / height;

                                imageView.setFitHeight(
                                        BADGE_MAX_HEIGHT
                                );

                                imageView.setFitWidth(
                                        width * scale
                                );

                            } else {

                                /*
                                 * Large screenshots.
                                 */

                                double finalWidth =
                                        Math.min(
                                                width,
                                                MAX_IMAGE_WIDTH
                                        );

                                imageView.setFitWidth(
                                        finalWidth
                                );

                                imageView.setFitHeight(
                                        0
                                );
                            }

                            imageView.setOpacity(
                                    1
                            );
                        });

                    } catch (Throwable ex) {

                        System.out.println(
                                "[MarkdownRenderer] IMAGE LOAD FAILED"
                        );

                        ex.printStackTrace();

                        Platform.runLater(() ->
                                showInlineImageError(
                                        imageView,
                                        "Failed: "
                                                + ex.getClass()
                                                .getSimpleName()
                                )
                        );
                    }

                });

        thread.setDaemon(
                true
        );

        thread.start();
    }

    // =============================================================
    // SVG DETECTION
    // =============================================================

    private boolean isSvg(
            byte[] data,
            String contentType
    ) {

        if (contentType != null
                && contentType
                .toLowerCase()
                .contains("image/svg")) {

            return true;
        }

        /*
         * Some servers return the wrong Content-Type.
         *
         * Check the beginning of the actual data too.
         */

        int length =
                Math.min(
                        data.length,
                        1000
                );

        String header =
                new String(
                        data,
                        0,
                        length,
                        java.nio.charset.StandardCharsets.UTF_8
                )
                        .trim()
                        .toLowerCase();

        return header.startsWith(
                "<svg"
        )
                || header.startsWith(
                "<?xml"
        )
                && header.contains(
                "<svg"
        );
    }

    // =============================================================
    // SVG -> PNG
    // =============================================================

    private byte[] convertSvgToPng(
            byte[] svgData
    ) throws Exception {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        PNGTranscoder transcoder =
                new PNGTranscoder();

        /*
         * Don't force a size here.
         *
         * Batik uses the SVG's intrinsic dimensions.
         */

        TranscoderInput input =
                new TranscoderInput(
                        new ByteArrayInputStream(
                                svgData
                        )
                );

        TranscoderOutput transcoderOutput =
                new TranscoderOutput(
                        output
                );

        transcoder.transcode(
                input,
                transcoderOutput
        );

        return output.toByteArray();
    }

    // =============================================================
    // IMAGE ERROR
    // =============================================================

    private void showInlineImageError(
            ImageView imageView,
            String message
    ) {

        imageView.setOpacity(
                1
        );

        /*
         * ImageView cannot display an error label itself.
         *
         * Keep it invisible instead of destroying the paragraph
         * layout with a giant error message.
         */

        System.out.println(
                "[MarkdownRenderer] "
                        + message
        );
    }

    // =============================================================
    // BULLET LIST
    // =============================================================

    private VBox renderBulletList(
            BulletList list
    ) {

        VBox container =
                new VBox(7);

        container.getStyleClass().add(
                "mod-description-list"
        );

        for (
                com.vladsch.flexmark.util.ast.Node child =
                list.getFirstChild();

                child != null;

                child = child.getNext()
        ) {

            if (child instanceof BulletListItem item) {

                container.getChildren().add(
                        renderListItem(
                                item,
                                "•"
                        )
                );
            }
        }

        return container;
    }

    // =============================================================
    // ORDERED LIST
    // =============================================================

    private VBox renderOrderedList(
            OrderedList list
    ) {

        VBox container =
                new VBox(7);

        container.getStyleClass().add(
                "mod-description-list"
        );

        int number = 1;

        for (
                com.vladsch.flexmark.util.ast.Node child =
                list.getFirstChild();

                child != null;

                child = child.getNext()
        ) {

            if (child instanceof ListItem item) {

                container.getChildren().add(
                        renderListItem(
                                item,
                                number + "."
                        )
                );

                number++;
            }
        }

        return container;
    }

    // =============================================================
    // LIST ITEM
    // =============================================================

    private HBox renderListItem(
            ListItem item,
            String prefix
    ) {

        Label bullet =
                new Label(
                        prefix
                );

        bullet.getStyleClass().add(
                "mod-description-bullet"
        );

        TextFlow text =
                new TextFlow();

        text.setLineSpacing(
                3
        );

        com.vladsch.flexmark.util.ast.Node first =
                item.getFirstChild();

        if (first instanceof Paragraph paragraph) {

            renderInline(
                    paragraph.getFirstChild(),
                    text
            );

        } else {

            renderInline(
                    first,
                    text
            );
        }

        HBox row =
                new HBox(
                        8,
                        bullet,
                        text
                );

        row.setAlignment(
                Pos.TOP_LEFT
        );

        HBox.setHgrow(
                text,
                Priority.ALWAYS
        );

        return row;
    }

    // =============================================================
    // LEGACY TEXTFLOW INLINE
    // =============================================================

    private void renderInline(
            com.vladsch.flexmark.util.ast.Node node,
            TextFlow flow
    ) {

        for (
                com.vladsch.flexmark.util.ast.Node current =
                node;

                current != null;

                current = current.getNext()
        ) {

            /*
             * Lists normally contain text, not badge images.
             *
             * Keep the existing TextFlow renderer for lists.
             */

            if (current instanceof com.vladsch.flexmark.ast.Text text) {

                javafx.scene.text.Text rendered =
                        new javafx.scene.text.Text(
                                text.getChars().toString()
                        );

                rendered.getStyleClass().add(
                        "mod-description-text"
                );

                flow.getChildren().add(
                        rendered
                );

                continue;
            }

            if (current instanceof StrongEmphasis strong) {

                javafx.scene.text.Text rendered =
                        new javafx.scene.text.Text(
                                strong.getText().toString()
                        );

                rendered.getStyleClass().add(
                        "mod-description-bold"
                );

                flow.getChildren().add(
                        rendered
                );

                continue;
            }

            if (current instanceof Emphasis emphasis) {

                javafx.scene.text.Text rendered =
                        new javafx.scene.text.Text(
                                emphasis.getText().toString()
                        );

                rendered.getStyleClass().add(
                        "mod-description-italic"
                );

                flow.getChildren().add(
                        rendered
                );

                continue;
            }

            if (current instanceof Code code) {

                javafx.scene.text.Text rendered =
                        new javafx.scene.text.Text(
                                code.getText().toString()
                        );

                rendered.getStyleClass().add(
                        "mod-description-code"
                );

                flow.getChildren().add(
                        rendered
                );

                continue;
            }

            if (current instanceof Link link) {

                if (containsImage(link)) {

                    /*
                     * TextFlow cannot display the ImageView.
                     *
                     * This should normally not happen in list content,
                     * so simply render the link text as a fallback.
                     */

                    javafx.scene.text.Text rendered =
                            new javafx.scene.text.Text(
                                    link.getText().toString()
                            );

                    rendered.getStyleClass().add(
                            "mod-description-link"
                    );

                    flow.getChildren().add(
                            rendered
                    );

                } else {

                    Hyperlink hyperlink =
                            new Hyperlink(
                                    link.getText().toString()
                            );

                    hyperlink.getStyleClass().add(
                            "mod-description-link"
                    );

                    String url =
                            link.getUrl().toString();

                    hyperlink.setOnAction(
                            event ->
                                    openUrl(url)
                    );

                    flow.getChildren().add(
                            hyperlink
                    );
                }

                continue;
            }

            if (current.getFirstChild() != null) {

                renderInline(
                        current.getFirstChild(),
                        flow
                );
            }
        }
    }

    // =============================================================
    // OPEN LINK
    // =============================================================

    private void openUrl(
            String url
    ) {

        try {

            URI uri =
                    URI.create(
                            url
                    );

            String scheme =
                    uri.getScheme();

            if (scheme == null
                    || (
                    !scheme.equalsIgnoreCase("https")
                            && !scheme.equalsIgnoreCase("http")
            )) {

                return;
            }

            if (Desktop.isDesktopSupported()) {

                Desktop.getDesktop().browse(
                        uri
                );
            }

        } catch (Exception ignored) {
            // Ignore unsupported/broken links.
        }
    }
}