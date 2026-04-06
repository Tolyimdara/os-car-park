package carpark.ui;

import carpark.model.ThreadStatus;
import carpark.simulation.SimulationController;
import carpark.threads.ConsumerThread;
import carpark.threads.ProducerThread;
import javafx.animation.*;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Top-down parking lot UI — reference-matched layout:
 *   IN gate  at top-center, OUT gate at bottom-center,
 *   symmetric left/right parking bays flanking a central vertical drive lane.
 *
 * Visual layers (StackPane order, bottom to top):
 *   1. mapPane   — JavaFX shape nodes: grass, asphalt, canopies, lane, slot lines
 *   2. slotLayer — ImageView per slot (visible only when a car is parked)
 *   3. animLayer — animated transient car nodes
 */
public class CarParkGameUI extends Application {

    // ── Canvas (calculated dynamically) ────────────────────────────────────
    private double CW = 750;  // width, set by layout
    private double CH = 620;  // height, set by layout

    // ── Car image sizes ──────────────────────────────────────────────────────
    private static final double SLOT_W       = 70.0;
    private static final double SLOT_H       = 118.0;
    private static final double CAR_MARGIN_X = 8.0;
    private static final double CAR_MARGIN_Y = 10.0;
    private static final double CAR_W        = SLOT_W - CAR_MARGIN_X * 2.0;   // 54
    private static final double CAR_H        = SLOT_H - CAR_MARGIN_Y * 2.0;   // 98
    private static final double PARKED_CAR_W = 52.0;
    private static final double PARKED_CAR_H = 92.0;
    private static final double ROAD_CAR_W   = 34.0;
    private static final double ROAD_CAR_H   = 70.0;

    // ── Symmetric lot layout (calculated dynamically) ────────────────────────
    private double MAP_GRASS_BORDER;
    private double MAP_PARK_LEFT_X;
    private double MAP_PARK_RIGHT_X;
    private double MAP_PARK_TOP_Y;
    private double MAP_PARK_BOT_Y;
    private double MAP_LANE_LEFT_X;
    private double MAP_LANE_RIGHT_X;
    private double CENTER_LANE_X;
    private double MAP_LEFT_SLOT_X;
    private double MAP_RIGHT_SLOT_X;
    private double MAP_CANOPY_TOP;
    private double MAP_CANOPY_HEIGHT;
    private double MAP_CANOPY_BOT;
    private double MAP_BOT_CURB_TOP;
    private double MAP_SLOT_TOP;
    private double MAP_SLOT_BOTTOM;
    private double GATE_TOP_Y;
    private double GATE_BOT_Y;

    // ── Palette ──────────────────────────────────────────────────────────────
    private static final Color C_GRASS    = Color.web("#4a8c3c");
    private static final Color C_ASPHALT  = Color.web("#2d3135");
    private static final Color C_LANE     = Color.web("#363a42");
    private static final Color C_SLOT_AREA= Color.web("#282c30");
    private static final Color C_MARKING  = Color.web("#f0eedf");
    private static final Color C_YELLOW   = Color.web("#f2c94c");
    private static final Color C_CURB     = Color.web("#9da4aa");
    private static final Color C_CANOPY   = Color.web("#a0a8b0");
    private static final Color C_PILLAR   = Color.web("#d4a820");

    private static final String[] CAR_COLORS = {
        "#c0392b", "#2980b9", "#27ae60", "#d35400",
        "#8e44ad", "#16a085", "#f39c12", "#e91e63"
    };

    // ── Simulation ───────────────────────────────────────────────────────────
    private SimulationController controller;
    private int     uiArrived       = 0;   // cumulative arrivals shown by UI
    private int     uiDeparted      = 0;   // cumulative departures shown by UI
    private boolean[] slotOccupied;
    private boolean[] slotExiting;
    private boolean[] slotAnimatingIn;
    private int[]     slotColor;
    private int       numSlots;

    // ── Image Cache ───────────────────────────────────────────────────────────
    private WritableImage[][] carImages;
    private Image[]           loadedCarImages;

    // ── Layers ────────────────────────────────────────────────────────────────
    private Pane       mapPane;
    private Pane       slotLayer;
    private Pane       animLayer;
    private ImageView[] slotViews;

    // ── Controls ─────────────────────────────────────────────────────────────
    private Button startBtn, stopBtn, resetBtn;
    private Label  statusBadge;
    private Label  occupancyLbl, producedLbl, consumedLbl, throughputLbl, waitTimeLbl;
    private Slider capSlider, prodSlider, consSlider, procSlider;
    private Label  capVal, prodVal, consVal, procVal;
    private Label  nextEntryLbl, nextEntryDetailLbl;
    private ImageView nextEntryPreview;
    private final Map<String, Circle> threadDots = new LinkedHashMap<>();

    // ── Animation Queues ─────────────────────────────────────────────────────
    private final Queue<Integer> arrivals   = new ConcurrentLinkedQueue<>();
    private final Queue<Integer> departures = new ConcurrentLinkedQueue<>();
    private int           colorIdx  = 0;
    private int           nextArrivalScanIdx = 0;
    private int           nextDepartureScanIdx = 0;
    private AnimationTimer gameLoop;

    // ── Gate Rotations ────────────────────────────────────────────────────────
    private Rotate inGateRotate;
    private Rotate outGateRotate;

    // Lane occupancy — prevents simultaneous road animations
    private boolean entryLaneOccupied = false;
    private boolean exitLaneOccupied  = false;
    private final Deque<Runnable> entryLaneQueue = new ArrayDeque<>();
    private final Deque<Runnable> exitLaneQueue  = new ArrayDeque<>();
    private long lastArrivalTime = 0;
    private static final long MIN_ARRIVAL_INTERVAL_MS = 220;
    private Animation[] activeAnims;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void start(Stage stage) {
        controller       = new SimulationController();
        numSlots         = SimulationController.DEFAULT_CAPACITY;
        slotOccupied     = new boolean[numSlots];
        slotExiting      = new boolean[numSlots];
        slotAnimatingIn  = new boolean[numSlots];
        slotColor        = new int[numSlots];
        activeAnims      = new Animation[numSlots];

        loadImageAssets();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:#1a1f30;");
        root.setTop(buildTopBar());
        root.setCenter(buildCenter());
        root.setRight(buildThreadPanel());
        root.setBottom(buildBottomBar());
        updateNextEntryPreview();

        Scene scene = new Scene(root, 1120, 740);
        loadCss(scene);

        stage.setTitle("Car Park Simulation");
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(700);
        stage.show();

        startGameLoop();
    }

    @Override
    public void stop() {
        if (gameLoop != null) gameLoop.stop();
        controller.stop();
    }

    private void loadCss(Scene scene) {
        try {
            var url = getClass().getResource("/carpark/ui/style.css");
            if (url != null) scene.getStylesheets().add(url.toExternalForm());
        } catch (Exception ignored) {}
    }

    private void loadImageAssets() {
        loadedCarImages = new Image[] {
            loadImageResource("/images/car_red.png"),
            loadImageResource("/images/car_blue.png"),
            loadImageResource("/images/car_green.png")
        };
    }

    private Image loadImageResource(String resourcePath) {
        try {
            var url = getClass().getResource(resourcePath);
            if (url == null) return null;
            Image image = new Image(url.toExternalForm(), false);
            return image.isError() ? null : image;
        } catch (Exception ignored) { return null; }
    }

    private Image loadedCarImage(int colorIndex) {
        if (loadedCarImages == null || loadedCarImages.length == 0) return null;
        Image img = loadedCarImages[Math.floorMod(colorIndex, loadedCarImages.length)];
        if (img != null) return img;
        for (Image c : loadedCarImages) if (c != null) return c;
        return null;
    }

    private int carAssetCount() {
        return (loadedCarImages == null || loadedCarImages.length == 0) ? 1 : loadedCarImages.length;
    }

    private String formatMillis(double millis) {
        return millis >= 100.0 ? String.format("%.0f ms", millis) : String.format("%.1f ms", millis);
    }

    // ── Car Image Factory ────────────────────────────────────────────────────

    private WritableImage renderCar(String hex) {
        Canvas c  = new Canvas(CAR_W, CAR_H);
        GraphicsContext g = c.getGraphicsContext2D();
        Color body  = Color.web(hex);
        Color roof  = body.deriveColor(0, 1.0, 0.62, 1.0);
        Color glass = Color.web("#aed6f180");
        Color glassR= Color.web("#85c1e970");
        Color tire  = Color.web("#111111");
        Color rim   = Color.web("#3d3d3d");
        double cx   = CAR_W / 2.0;
        g.setFill(body);
        g.fillRoundRect(8, 4, CAR_W - 16, CAR_H - 8, 12, 12);
        paintTyre(g, 1,          10,         10, 20, tire, rim);
        paintTyre(g, CAR_W - 11, 10,         10, 20, tire, rim);
        paintTyre(g, 1,          CAR_H - 30, 10, 20, tire, rim);
        paintTyre(g, CAR_W - 11, CAR_H - 30, 10, 20, tire, rim);
        g.setFill(Color.web("#fef9c3ee"));
        g.fillRoundRect(10, 6,  14, 8, 3, 3);
        g.fillRoundRect(CAR_W - 24, 6, 14, 8, 3, 3);
        g.setFill(glass);
        g.fillRoundRect(12, 17, CAR_W - 24, 20, 5, 5);
        g.setFill(roof);
        g.fillRoundRect(cx - 17, 40, 34, 34, 9, 9);
        g.setFill(glassR);
        g.fillRoundRect(13, CAR_H - 33, CAR_W - 26, 17, 4, 4);
        g.setFill(Color.web("#e53e3eee"));
        g.fillRoundRect(10, CAR_H - 12, 14, 6, 2, 2);
        g.fillRoundRect(CAR_W - 24, CAR_H - 12, 14, 6, 2, 2);
        g.setFill(Color.web("#ffffff18"));
        g.fillRoundRect(cx - 8, 42, 9, 30, 4, 4);
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        return c.snapshot(sp, null);
    }

    private WritableImage rotate180(WritableImage src) {
        Canvas c = new Canvas(CAR_W, CAR_H);
        GraphicsContext g = c.getGraphicsContext2D();
        g.save(); g.translate(CAR_W, CAR_H); g.rotate(180);
        g.drawImage(src, 0, 0); g.restore();
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        return c.snapshot(sp, null);
    }

    private void paintTyre(GraphicsContext g, double x, double y, double w, double h,
                            Color tireColor, Color rimColor) {
        g.setFill(tireColor); g.fillRoundRect(x, y, w, h, 3, 3);
        g.setFill(rimColor);  g.fillRoundRect(x + 2, y + 3, w - 4, h - 6, 2, 2);
    }

    // ── Top Bar ──────────────────────────────────────────────────────────────

    private HBox buildTopBar() {
        Label title = new Label("CAR PARK SIMULATION");
        title.setStyle("-fx-font-size:18px; -fx-font-weight:bold;" +
                       "-fx-text-fill:#e2e8f0; -fx-letter-spacing:2;");

        statusBadge = new Label("STOPPED");
        statusBadge.getStyleClass().add("badge-stopped");

        HBox nextEntryCard = buildNextEntryCard();
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        startBtn = styledBtn("START", "btn-green");
        stopBtn  = styledBtn("STOP",  "btn-red");
        resetBtn = styledBtn("RESET", "btn-gray");
        stopBtn.setDisable(true);

        startBtn.setOnAction(e -> onStart());
        stopBtn .setOnAction(e -> onStop());
        resetBtn.setOnAction(e -> onReset());

        HBox bar = new HBox(14, title, statusBadge, gap, startBtn, stopBtn, resetBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(13, 20, 13, 20));
        bar.setStyle("-fx-background-color:#141929;" +
                     "-fx-border-color:#252f4a; -fx-border-width:0 0 1 0;");
        return bar;
    }

    private HBox buildNextEntryCard() {
        Label roadHeader = new Label("APPROACH ROAD");
        roadHeader.setStyle("-fx-font-size:10px; -fx-font-weight:bold;" +
                            "-fx-text-fill:#60a5fa; -fx-letter-spacing:1;");
        Label roadStatus = new Label("Vehicles approaching");
        roadStatus.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:#e2e8f0;");
        Label roadDetail = new Label("Real-time simulation");
        roadDetail.setStyle("-fx-font-size:11px; -fx-text-fill:#94a3b8;");

        Pane roadViz = new Pane();
        roadViz.setMinSize(52, 76); roadViz.setPrefSize(52, 76); roadViz.setMaxSize(52, 76);
        roadViz.setStyle("-fx-background-color:#1a2332; -fx-background-radius:12;" +
                         "-fx-border-color:#3b4d66; -fx-border-radius:12; -fx-border-width:1;");
        Rectangle roadBase = new Rectangle(20, 70);
        roadBase.setFill(Color.web("#2d3f54"));
        roadBase.setLayoutX(16); roadBase.setLayoutY(3);
        for (int i = 0; i < 4; i++) {
            Rectangle dash = new Rectangle(16, 2);
            dash.setFill(Color.web("#60a5fa", 0.6));
            dash.setLayoutX(18); dash.setLayoutY(8 + i * 16);
            roadViz.getChildren().add(dash);
        }
        roadViz.getChildren().add(roadBase);

        VBox textBox = new VBox(2, roadHeader, roadStatus, roadDetail);
        HBox card = new HBox(10, roadViz, textBox);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(8, 12, 8, 12));
        card.setStyle("-fx-background-color:#0b1220d8; -fx-background-radius:16;" +
                      "-fx-border-color:#334155; -fx-border-radius:16;");
        return card;
    }

    private StackPane buildCenter() {
        mapPane   = new Pane();
        slotLayer = new Pane();
        animLayer = new Pane();
        
        // Remove fixed sizes - allow responsive growth
        slotLayer.setMouseTransparent(true);
        animLayer.setMouseTransparent(true);
        
        // Set min/max to allow growth
        mapPane.setMinSize(500, 400);
        mapPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        slotLayer.setMinSize(500, 400);
        slotLayer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        animLayer.setMinSize(500, 400);
        animLayer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // Add layout listener to recalculate coordinates and redraw when size changes
        mapPane.layoutBoundsProperty().addListener((obs, oldVal, newVal) -> {
            recalculateCoordinates(newVal.getWidth(), newVal.getHeight());
            drawParkingLot();
            buildSlotViews();
            for (int i = 0; i < numSlots; i++)
                if (slotOccupied[i]) showParkedCar(i, slotColor[i], false);
        });

        drawParkingLot();
        buildSlotViews();

        StackPane wrap = new StackPane(mapPane, slotLayer, animLayer);
        wrap.getStyleClass().add("parking-lot-shell");
        wrap.setPadding(new Insets(15, 12, 15, 15));
        wrap.setAlignment(Pos.CENTER);
        
        // Let the StackPane grow to fill available space
        HBox container = new HBox(wrap);
        container.setAlignment(Pos.CENTER);
        container.setStyle("-fx-background-color:#1a1f30;");
        HBox.setHgrow(wrap, Priority.ALWAYS);
        
        return wrap;
    }

    /**
     * Recalculate all coordinate constants based on actual pane dimensions.
     * This method is called whenever the pane is resized.
     */
    private void recalculateCoordinates(double width, double height) {
        if (width <= 100 || height <= 100) return; // Minimum size check
        
        CW = width;
        CH = height;
        
        // Calculate symmetric layout
        MAP_GRASS_BORDER = Math.max(18.0, CW * 0.035);  // Responsive border, ~3.5% of width
        MAP_PARK_LEFT_X = MAP_GRASS_BORDER;
        MAP_PARK_RIGHT_X = CW - MAP_GRASS_BORDER;
        MAP_PARK_TOP_Y = MAP_GRASS_BORDER;
        MAP_PARK_BOT_Y = CH - MAP_GRASS_BORDER;
        
        // Center lane width scales with parking lot width
        double parkW = MAP_PARK_RIGHT_X - MAP_PARK_LEFT_X;
        double laneWidth = parkW * 0.24;  // Center lane is ~24% of lot width
        MAP_LANE_LEFT_X = MAP_PARK_LEFT_X + parkW * 0.38;
        MAP_LANE_RIGHT_X = MAP_PARK_LEFT_X + parkW * 0.62;
        CENTER_LANE_X = (MAP_LANE_LEFT_X + MAP_LANE_RIGHT_X) / 2.0;
        
        MAP_LEFT_SLOT_X = (MAP_PARK_LEFT_X + MAP_LANE_LEFT_X) / 2.0;
        MAP_RIGHT_SLOT_X = (MAP_LANE_RIGHT_X + MAP_PARK_RIGHT_X) / 2.0;
        
        // Canopy at top
        MAP_CANOPY_TOP = MAP_PARK_TOP_Y;
        MAP_CANOPY_HEIGHT = CH * 0.115;  // ~11.5% of height
        MAP_CANOPY_BOT = MAP_CANOPY_TOP + MAP_CANOPY_HEIGHT;
        
        // Slot area band
        MAP_SLOT_TOP = MAP_CANOPY_BOT + CH * 0.08;
        MAP_BOT_CURB_TOP = MAP_PARK_BOT_Y - CH * 0.12;
        MAP_SLOT_BOTTOM = MAP_BOT_CURB_TOP - CH * 0.05;
        
        // Gate Y positions
        GATE_TOP_Y = MAP_CANOPY_BOT + 2.0;
        GATE_BOT_Y = MAP_BOT_CURB_TOP - 14.0;
    }

    // ── Parking Lot Drawing ──────────────────────────────────────────────────

    /**
     * Full redraw order (bottom → top):
     *   grass → asphalt → slot areas → center lane → canopies → trees
     *   → lane boundaries → slot lines → lane markings → gates → P sign
     */
    private void drawParkingLot() {
        mapPane.getChildren().clear();
        drawMapGrass();           // green grass ring with yellow curb stripe
        drawMapAsphalt();         // dark asphalt base
        drawMapLane();            // center driving lane (slightly lighter shade)
        drawMapCanopies();        // roof/awning structures at top of each parking section
        drawMapBottomApproach();  // gray curb band at bottom (exit side)
        drawMapTrees();           // decorative tree islands
        drawMapLaneBoundaries();  // white vertical lines at lane edges
        drawMapSlotLines();       // horizontal dividers between parking bays
        drawMapLaneMarkings();    // dashed center line
        drawMapGates();           // IN (top) and OUT (bottom) barrier gates
        drawMapParkingSign();     // blue P sign in lane center
    }

    /** Green grass border surrounding the parking lot with a yellow curb stripe. */
    private void drawMapGrass() {
        // Grass fill
        Rectangle grass = new Rectangle(0, 0, CW, CH);
        grass.setFill(C_GRASS);
        mapPane.getChildren().add(grass);
        // Inner grass highlight
        Rectangle inner = new Rectangle(4, 4, CW - 8, CH - 8);
        inner.setFill(Color.TRANSPARENT);
        inner.setStroke(Color.web("#5faa48"));
        inner.setStrokeWidth(3);
        mapPane.getChildren().add(inner);
        // Yellow curb line (inset just inside grass)
        Rectangle curb = new Rectangle(MAP_GRASS_BORDER - 6, MAP_GRASS_BORDER - 6,
                CW - (MAP_GRASS_BORDER - 6) * 2, CH - (MAP_GRASS_BORDER - 6) * 2);
        curb.setFill(Color.TRANSPARENT);
        curb.setStroke(C_YELLOW);
        curb.setStrokeWidth(4.5);
        mapPane.getChildren().add(curb);
    }

    /** Dark asphalt base covering the full parking area inside the grass border. */
    private void drawMapAsphalt() {
        double w = MAP_PARK_RIGHT_X - MAP_PARK_LEFT_X;
        double h = MAP_PARK_BOT_Y   - MAP_PARK_TOP_Y;
        Rectangle base = new Rectangle(MAP_PARK_LEFT_X, MAP_PARK_TOP_Y, w, h);
        base.setFill(C_ASPHALT);
        mapPane.getChildren().add(base);

        // Slightly darker zones where the parking bays are
        double slotH = MAP_SLOT_BOTTOM - MAP_SLOT_TOP;
        Rectangle leftZone = new Rectangle(MAP_PARK_LEFT_X, MAP_SLOT_TOP,
                MAP_LANE_LEFT_X - MAP_PARK_LEFT_X, slotH);
        leftZone.setFill(C_SLOT_AREA);
        mapPane.getChildren().add(leftZone);

        Rectangle rightZone = new Rectangle(MAP_LANE_RIGHT_X, MAP_SLOT_TOP,
                MAP_PARK_RIGHT_X - MAP_LANE_RIGHT_X, slotH);
        rightZone.setFill(C_SLOT_AREA);
        mapPane.getChildren().add(rightZone);
    }

    /** Center vertical driving lane (lighter asphalt shade). */
    private void drawMapLane() {
        Rectangle lane = new Rectangle(MAP_LANE_LEFT_X, MAP_PARK_TOP_Y,
                MAP_LANE_RIGHT_X - MAP_LANE_LEFT_X,
                MAP_PARK_BOT_Y - MAP_PARK_TOP_Y);
        lane.setFill(C_LANE);
        mapPane.getChildren().add(lane);
    }

    /**
     * Canopy / roof-awning structures over the top of each parking section.
     * Each section: concrete slab + yellow support pillars + light rail strip.
     * A gap in the center (the driving lane) is where the IN gate sits.
     */
    private void drawMapCanopies() {
        double leftW  = MAP_LANE_LEFT_X  - MAP_PARK_LEFT_X;   // 342
        double rightW = MAP_PARK_RIGHT_X - MAP_LANE_RIGHT_X;  // 342

        drawCanopySection(MAP_PARK_LEFT_X,  MAP_CANOPY_TOP, leftW,  MAP_CANOPY_HEIGHT);
        drawCanopySection(MAP_LANE_RIGHT_X, MAP_CANOPY_TOP, rightW, MAP_CANOPY_HEIGHT);
    }

    private void drawCanopySection(double x, double y, double w, double h) {
        // Roof slab (concrete)
        Rectangle roof = new Rectangle(x, y, w, h * 0.55);
        roof.setFill(C_CANOPY);
        mapPane.getChildren().add(roof);

        // Soffit (underside, slightly darker)
        Rectangle soffit = new Rectangle(x, y + h * 0.55, w, h * 0.45);
        soffit.setFill(Color.web("#8a9199"));
        mapPane.getChildren().add(soffit);

        // Yellow support pillars — evenly spaced
        int pillars = 4;
        double pillarW = 10;
        double spacing = w / (pillars + 1);
        for (int i = 1; i <= pillars; i++) {
            double px = x + i * spacing - pillarW / 2.0;
            Rectangle pillar = new Rectangle(px, y, pillarW, h);
            pillar.setFill(C_PILLAR);
            mapPane.getChildren().add(pillar);
        }

        // Light strip along the bottom of the soffit
        Rectangle lightStrip = new Rectangle(x + 6, y + h - 10, w - 12, 6);
        lightStrip.setFill(Color.web("#d8c060", 0.8));
        lightStrip.setArcWidth(4); lightStrip.setArcHeight(4);
        mapPane.getChildren().add(lightStrip);

        // Bottom edge shadow line
        Rectangle edge = new Rectangle(x, y + h - 2, w, 3);
        edge.setFill(Color.web("#505860"));
        mapPane.getChildren().add(edge);
    }

    /**
     * Gray curb / approach band at the bottom of the lot (exit side).
     * Mirrors the visual weight of the canopies at the top.
     */
    private void drawMapBottomApproach() {
        double w = MAP_PARK_RIGHT_X - MAP_PARK_LEFT_X;
        double h = MAP_PARK_BOT_Y - MAP_BOT_CURB_TOP;
        Rectangle curb = new Rectangle(MAP_PARK_LEFT_X, MAP_BOT_CURB_TOP, w, h);
        curb.setFill(C_CURB);
        mapPane.getChildren().add(curb);

        // Yellow/black hazard stripe at top edge of curb
        int numStripes = 26;
        double sw = w / numStripes;
        for (int i = 0; i < numStripes; i++) {
            Rectangle s = new Rectangle(MAP_PARK_LEFT_X + i * sw, MAP_BOT_CURB_TOP, sw, 12);
            s.setFill(i % 2 == 0 ? Color.web("#f0c030") : Color.web("#1a1a1a"));
            mapPane.getChildren().add(s);
        }
    }

    /** Tree islands — densely placed for a lush park feel. */
    private void drawMapTrees() {
        double midLeftX  = (MAP_PARK_LEFT_X + MAP_LANE_LEFT_X)  / 2.0;
        double midRightX = (MAP_LANE_RIGHT_X + MAP_PARK_RIGHT_X) / 2.0;

        // ── Top band: below the canopy ───────────────────────────────────────
        double topY = MAP_CANOPY_BOT + 18.0;
        // Front row
        addTreeIsland(MAP_PARK_LEFT_X + 56,  topY,      15.0, 0.88);
        addTreeIsland(midLeftX,              topY + 4,  12.0, 0.72);
        addTreeIsland(MAP_LANE_LEFT_X  - 52, topY - 3,  13.0, 0.76);
        addTreeIsland(MAP_LANE_RIGHT_X + 52, topY - 3,  13.0, 0.76);
        addTreeIsland(midRightX,             topY + 4,  12.0, 0.72);
        addTreeIsland(MAP_PARK_RIGHT_X - 56, topY,      15.0, 0.88);
        // Back row (slightly above, smaller — depth effect)
        addTreeIsland(MAP_PARK_LEFT_X + 28,  topY - 10, 10.0, 0.60);
        addTreeIsland(MAP_PARK_LEFT_X + 90,  topY -  8,  9.0, 0.55);
        addTreeIsland(midLeftX - 28,         topY -  6,  9.0, 0.58);
        addTreeIsland(midLeftX + 28,         topY -  7,  9.0, 0.54);
        addTreeIsland(MAP_LANE_LEFT_X  - 26, topY -  9, 10.0, 0.60);
        addTreeIsland(MAP_LANE_RIGHT_X + 26, topY -  9, 10.0, 0.60);
        addTreeIsland(midRightX - 28,        topY -  7,  9.0, 0.54);
        addTreeIsland(midRightX + 28,        topY -  6,  9.0, 0.58);
        addTreeIsland(MAP_PARK_RIGHT_X - 90, topY -  8,  9.0, 0.55);
        addTreeIsland(MAP_PARK_RIGHT_X - 28, topY - 10, 10.0, 0.60);

        // ── Bottom band: above the curb ──────────────────────────────────────
        double botY = MAP_BOT_CURB_TOP - 14.0;
        // Front row
        addTreeIsland(MAP_PARK_LEFT_X + 60,  botY,      13.0, 0.72);
        addTreeIsland(midLeftX,              botY - 3,  11.0, 0.68);
        addTreeIsland(MAP_LANE_LEFT_X  - 56, botY + 2,  12.0, 0.66);
        addTreeIsland(MAP_LANE_RIGHT_X + 56, botY + 2,  12.0, 0.66);
        addTreeIsland(midRightX,             botY - 3,  11.0, 0.68);
        addTreeIsland(MAP_PARK_RIGHT_X - 60, botY,      13.0, 0.72);
        // Back row (slightly below, smaller)
        addTreeIsland(MAP_PARK_LEFT_X + 30,  botY + 8,  9.0, 0.56);
        addTreeIsland(MAP_PARK_LEFT_X + 85,  botY + 6,  8.0, 0.52);
        addTreeIsland(midLeftX - 26,         botY + 5,  8.0, 0.54);
        addTreeIsland(midLeftX + 26,         botY + 6,  8.0, 0.50);
        addTreeIsland(MAP_LANE_LEFT_X  - 28, botY + 7,  9.0, 0.56);
        addTreeIsland(MAP_LANE_RIGHT_X + 28, botY + 7,  9.0, 0.56);
        addTreeIsland(midRightX - 26,        botY + 6,  8.0, 0.50);
        addTreeIsland(midRightX + 26,        botY + 5,  8.0, 0.54);
        addTreeIsland(MAP_PARK_RIGHT_X - 85, botY + 6,  8.0, 0.52);
        addTreeIsland(MAP_PARK_RIGHT_X - 30, botY + 8,  9.0, 0.56);

        // ── Outer left border strip ───────────────────────────────────────────
        double leftBX = MAP_GRASS_BORDER / 2.0;
        addTreeIsland(leftBX, MAP_PARK_TOP_Y  + CH * 0.22, 6.0, 0.46);
        addTreeIsland(leftBX, MAP_PARK_TOP_Y  + CH * 0.38, 6.0, 0.42);
        addTreeIsland(leftBX, MAP_PARK_TOP_Y  + CH * 0.54, 6.0, 0.44);
        addTreeIsland(leftBX, MAP_PARK_TOP_Y  + CH * 0.70, 6.0, 0.42);

        // ── Outer right border strip ──────────────────────────────────────────
        double rightBX = MAP_PARK_RIGHT_X + MAP_GRASS_BORDER / 2.0;
        addTreeIsland(rightBX, MAP_PARK_TOP_Y + CH * 0.22, 6.0, 0.46);
        addTreeIsland(rightBX, MAP_PARK_TOP_Y + CH * 0.38, 6.0, 0.42);
        addTreeIsland(rightBX, MAP_PARK_TOP_Y + CH * 0.54, 6.0, 0.44);
        addTreeIsland(rightBX, MAP_PARK_TOP_Y + CH * 0.70, 6.0, 0.42);
    }

    private void addTreeIsland(double cx, double groundY, double bedR, double scale) {
        Circle shadow = new Circle(cx, groundY + 3, bedR + 4);
        shadow.setFill(Color.web("#00000024"));
        mapPane.getChildren().add(shadow);

        Circle bed = new Circle(cx, groundY, bedR + 2);
        bed.setFill(Color.web("#7ebc67"));
        bed.setStroke(Color.web("#4f7f42")); bed.setStrokeWidth(2);
        mapPane.getChildren().add(bed);

        double trunkW = 8 * scale, trunkH = 20 * scale;
        Rectangle trunk = new Rectangle(cx - trunkW / 2, groundY - trunkH, trunkW, trunkH);
        trunk.setFill(Color.web("#76503a"));
        trunk.setArcWidth(4); trunk.setArcHeight(4);
        mapPane.getChildren().add(trunk);

        Circle cBack  = new Circle(cx,              groundY - trunkH - 14 * scale, 15 * scale);
        cBack.setFill(Color.web("#2f7b38"));
        Circle cLeft  = new Circle(cx - 12 * scale, groundY - trunkH -  6 * scale, 11 * scale);
        cLeft.setFill(Color.web("#469a47"));
        Circle cRight = new Circle(cx + 12 * scale, groundY - trunkH -  6 * scale, 11 * scale);
        cRight.setFill(Color.web("#469a47"));
        Circle cFront = new Circle(cx,              groundY - trunkH +  2 * scale, 13 * scale);
        cFront.setFill(Color.web("#5caf57"));
        Circle cHigh  = new Circle(cx -  5 * scale, groundY - trunkH - 11 * scale,  4.5 * scale);
        cHigh.setFill(Color.web("#8fd37d"));

        mapPane.getChildren().addAll(cBack, cLeft, cRight, cFront, cHigh);
    }

    /** White vertical boundary lines between the center lane and parking bays. */
    private void drawMapLaneBoundaries() {
        Line left = new Line(MAP_LANE_LEFT_X, MAP_SLOT_TOP, MAP_LANE_LEFT_X, MAP_SLOT_BOTTOM);
        left.setStroke(Color.web("#d0cec4")); left.setStrokeWidth(3);
        mapPane.getChildren().add(left);

        Line right = new Line(MAP_LANE_RIGHT_X, MAP_SLOT_TOP, MAP_LANE_RIGHT_X, MAP_SLOT_BOTTOM);
        right.setStroke(Color.web("#d0cec4")); right.setStrokeWidth(3);
        mapPane.getChildren().add(right);
    }

    /** Horizontal white dividers between individual parking bays on each side. */
    private void drawMapSlotLines() {
        drawSideSlotLines(MAP_PARK_LEFT_X,  MAP_LANE_LEFT_X,  leftSideSlotCount());
        drawSideSlotLines(MAP_LANE_RIGHT_X, MAP_PARK_RIGHT_X, rightSideSlotCount());
    }

    private void drawSideSlotLines(double x1, double x2, int count) {
        if (count <= 0) return;
        double cellH = (MAP_SLOT_BOTTOM - MAP_SLOT_TOP) / (double) count;
        addSlotLine(x1, x2, MAP_SLOT_TOP);
        for (int i = 1; i < count; i++) addSlotLine(x1, x2, MAP_SLOT_TOP + i * cellH);
        addSlotLine(x1, x2, MAP_SLOT_BOTTOM);
    }

    private void addSlotLine(double x1, double x2, double y) {
        Line line = new Line(x1, y, x2, y);
        line.setStroke(Color.web("#c8c8bc")); line.setStrokeWidth(2.5);
        mapPane.getChildren().add(line);
    }

    /** Dashed center line and edge lines for the driving lane. */
    private void drawMapLaneMarkings() {
        double cx  = CENTER_LANE_X;
        double top = MAP_PARK_TOP_Y + 4;
        double bot = MAP_PARK_BOT_Y - 4;

        Line centerLine = new Line(cx, top, cx, bot);
        centerLine.setStroke(C_MARKING); centerLine.setStrokeWidth(1.8);
        centerLine.getStrokeDashArray().addAll(18.0, 13.0);
        mapPane.getChildren().add(centerLine);

        Line leftEdge = new Line(MAP_LANE_LEFT_X + 5, top, MAP_LANE_LEFT_X + 5, bot);
        leftEdge.setStroke(C_MARKING); leftEdge.setStrokeWidth(1.4);
        leftEdge.getStrokeDashArray().addAll(14.0, 10.0);
        mapPane.getChildren().add(leftEdge);

        Line rightEdge = new Line(MAP_LANE_RIGHT_X - 5, top, MAP_LANE_RIGHT_X - 5, bot);
        rightEdge.setStroke(C_MARKING); rightEdge.setStrokeWidth(1.4);
        rightEdge.getStrokeDashArray().addAll(14.0, 10.0);
        mapPane.getChildren().add(rightEdge);
    }

    /**
     * IN gate (top-center) and OUT gate (bottom-center).
     * Each gate: two gray housing posts + an animated red/white striped barrier arm.
     * The arm pivots around its left end: 0° = closed (horizontal), -85° = open (raised).
     */
    private void drawMapGates() {
        double laneW = MAP_LANE_RIGHT_X - MAP_LANE_LEFT_X;
        double armH  = 10;

        // ── IN gate (top) ──
        drawGatePost(MAP_LANE_LEFT_X  - 10, GATE_TOP_Y);
        drawGatePost(MAP_LANE_RIGHT_X -  6, GATE_TOP_Y);

        Group inArm = buildBarrierArmGroup(laneW, false, armH);
        inArm.setTranslateX(MAP_LANE_LEFT_X);
        inArm.setTranslateY(GATE_TOP_Y + 8);
        inGateRotate = new Rotate(0, 0, armH / 2.0);
        inArm.getTransforms().add(inGateRotate);
        mapPane.getChildren().add(inArm);

        Text inLabel = new Text("IN");
        inLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        inLabel.setFill(Color.web("#e8f0fc"));
        inLabel.setX(CENTER_LANE_X - 10);
        inLabel.setY(GATE_TOP_Y - 2);
        mapPane.getChildren().add(inLabel);

        // ── OUT gate (bottom) ──
        drawGatePost(MAP_LANE_LEFT_X  - 10, GATE_BOT_Y);
        drawGatePost(MAP_LANE_RIGHT_X -  6, GATE_BOT_Y);

        Group outArm = buildBarrierArmGroup(laneW, true, armH);
        outArm.setTranslateX(MAP_LANE_LEFT_X);
        outArm.setTranslateY(GATE_BOT_Y + 8);
        outGateRotate = new Rotate(0, 0, armH / 2.0);
        outArm.getTransforms().add(outGateRotate);
        mapPane.getChildren().add(outArm);

        Text outLabel = new Text("OUT");
        outLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        outLabel.setFill(Color.web("#e8f0fc"));
        outLabel.setX(CENTER_LANE_X - 16);
        outLabel.setY(GATE_BOT_Y + 36);
        mapPane.getChildren().add(outLabel);
    }

    private void drawGatePost(double x, double y) {
        Rectangle post = new Rectangle(x, y - 4, 16, 30);
        post.setFill(Color.web("#464f5e"));
        post.setArcWidth(5); post.setArcHeight(5);
        mapPane.getChildren().add(post);
        Circle light = new Circle(x + 8, y - 4, 4.5);
        light.setFill(Color.web("#60d080"));
        mapPane.getChildren().add(light);
    }

    /** Builds an animated barrier arm Group in local coords (0,0)→(w,armH). */
    private Group buildBarrierArmGroup(double w, boolean flipped, double armH) {
        int stripes = 9;
        double sw = w / stripes;
        Group g = new Group();
        for (int i = 0; i < stripes; i++) {
            boolean isRed = (flipped ? i + 1 : i) % 2 == 0;
            Rectangle s = new Rectangle(i * sw, 0, sw, armH);
            s.setFill(isRed ? Color.web("#d93030") : Color.web("#f0f0f0"));
            g.getChildren().add(s);
        }
        Rectangle outline = new Rectangle(0, 0, w, armH);
        outline.setFill(Color.TRANSPARENT);
        outline.setStroke(Color.web("#303030")); outline.setStrokeWidth(1.5);
        g.getChildren().add(outline);
        return g;
    }

    /** Animates a gate arm up to -85° (open), then calls onOpen. */
    private void openGate(Rotate gateRotate, Runnable onOpen) {
        if (gateRotate == null) { onOpen.run(); return; }
        Timeline t = new Timeline(new KeyFrame(
            Duration.millis(280),
            new KeyValue(gateRotate.angleProperty(), -85, Interpolator.EASE_OUT)
        ));
        t.setOnFinished(e -> onOpen.run());
        t.play();
    }

    /** Animates a gate arm back down to 0° (closed). */
    private void closeGate(Rotate gateRotate) {
        if (gateRotate == null) return;
        Timeline t = new Timeline(new KeyFrame(
            Duration.millis(350),
            new KeyValue(gateRotate.angleProperty(), 0, Interpolator.EASE_IN)
        ));
        t.play();
    }

    /** Blue "P" parking sign in the centre of the driving lane. */
    private void drawMapParkingSign() {
        double cx = CENTER_LANE_X;
        double cy = (MAP_PARK_TOP_Y + MAP_PARK_BOT_Y) / 2.0;
        double sw = 76, sh = 76;
        Rectangle border = new Rectangle(cx - sw / 2 - 5, cy - sh / 2 - 5, sw + 10, sh + 10);
        border.setFill(Color.WHITE);
        border.setOpacity(0.5);
        border.setArcWidth(16); border.setArcHeight(16);
        mapPane.getChildren().add(border);
        Rectangle bg = new Rectangle(cx - sw / 2, cy - sh / 2, sw, sh);
        bg.setFill(Color.web("#1565c0"));
        bg.setOpacity(0.5);
        bg.setArcWidth(10); bg.setArcHeight(10);
        mapPane.getChildren().add(bg);
        Text pText = new Text("P");
        pText.setFont(Font.font("Arial", FontWeight.BOLD, 52));
        pText.setFill(Color.WHITE);
        pText.setOpacity(0.5);
        pText.setX(cx - 17); pText.setY(cy + 19);
        mapPane.getChildren().add(pText);
    }

    // ── Slot ImageViews ──────────────────────────────────────────────────────

    private void buildSlotViews() {
        slotViews = new ImageView[numSlots];
        slotLayer.getChildren().clear();
        int leftCount  = leftSideSlotCount();
        int rightCount = rightSideSlotCount();
        for (int i = 0; i < leftCount; i++)
            slotViews[i] = slotImageView(MAP_LEFT_SLOT_X, slotRowCenterY(i, leftCount));
        for (int i = 0; i < rightCount; i++)
            slotViews[leftCount + i] = slotImageView(MAP_RIGHT_SLOT_X, slotRowCenterY(i, rightCount));
        slotLayer.getChildren().addAll(slotViews);
    }

    private ImageView slotImageView(double centerX, double centerY) {
        ImageView iv = new ImageView();
        iv.setFitWidth(PARKED_CAR_W); iv.setFitHeight(PARKED_CAR_H);
        iv.setPreserveRatio(false); iv.setSmooth(true);
        iv.setLayoutX(centerX - PARKED_CAR_W / 2.0);
        iv.setLayoutY(centerY - PARKED_CAR_H / 2.0);
        iv.setVisible(false); iv.setOpacity(0);
        return iv;
    }

    private int leftSideSlotCount()  { return (numSlots + 1) / 2; }
    private int rightSideSlotCount() { return numSlots / 2; }
    private boolean isLeftSideSlot(int idx) { return idx < leftSideSlotCount(); }

    private double slotRowCenterY(int position, int count) {
        if (count <= 0) return (MAP_SLOT_TOP + MAP_SLOT_BOTTOM) / 2.0;
        double cellH = (MAP_SLOT_BOTTOM - MAP_SLOT_TOP) / count;
        return MAP_SLOT_TOP + cellH * (position + 0.5);
    }

    /**
     * Parked orientation: nose-toward-lane (front faces the center lane).
     *   Left  slots → rotation -90° (headlights face east = toward lane)
     *   Right slots → rotation  90° (headlights face west = toward lane)
     */
    private double parkedRotation(int slotIdx) {
        return isLeftSideSlot(slotIdx) ? -90.0 : 90.0;
    }

    // ── Thread Panel ─────────────────────────────────────────────────────────

    private VBox buildThreadPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(18, 14, 18, 14));
        panel.setPrefWidth(190);
        panel.setStyle("-fx-background-color:#141929;" +
                       "-fx-border-color:#252f4a; -fx-border-width:0 0 0 1;");

        Label hdr = new Label("THREADS");
        hdr.setStyle("-fx-font-size:10px; -fx-font-weight:bold;" +
                     "-fx-text-fill:#475569; -fx-letter-spacing:3;");
        panel.getChildren().addAll(hdr, hLine());

        panel.getChildren().add(sectionLbl("Producers"));
        for (int i = 1; i <= SimulationController.NUM_PRODUCERS; i++)
            panel.getChildren().add(threadRow("P" + i, "Producer " + i));
        panel.getChildren().add(hLine());
        panel.getChildren().add(sectionLbl("Consumers"));
        for (int i = 1; i <= SimulationController.NUM_CONSUMERS; i++)
            panel.getChildren().add(threadRow("C" + i, "Consumer " + i));
        panel.getChildren().add(hLine());
        panel.getChildren().add(sectionLbl("Live Stats"));
        occupancyLbl  = statEntry(panel, "Occupancy",  "0 / " + numSlots);
        producedLbl   = statEntry(panel, "Produced",   "0");
        consumedLbl   = statEntry(panel, "Consumed",   "0");
        throughputLbl = statEntry(panel, "Throughput", "0.0/s");
        waitTimeLbl   = statEntry(panel, "Avg Wait",   "0 ms");
        panel.getChildren().add(hLine());
        panel.getChildren().add(sectionLbl("Legend"));
        panel.getChildren().add(legendRow("#4caf7d", "Active"));
        panel.getChildren().add(legendRow("#fbbf24", "Waiting"));
        panel.getChildren().add(legendRow("#f87171", "Crashed"));
        return panel;
    }

    // ── Bottom Bar ───────────────────────────────────────────────────────────

    private HBox buildBottomBar() {
        capSlider  = slider(1,   20,   SimulationController.DEFAULT_CAPACITY);
        prodSlider = slider(100, 6000, SimulationController.DEFAULT_PROD_RATE_MS);
        consSlider = slider(100, 6000, SimulationController.DEFAULT_CONS_RATE_MS);
        procSlider = slider(50,  3000, SimulationController.DEFAULT_PROC_TIME_MS);
        capVal  = sliderVal(String.valueOf(SimulationController.DEFAULT_CAPACITY));
        prodVal = sliderVal(SimulationController.DEFAULT_PROD_RATE_MS + "ms");
        consVal = sliderVal(SimulationController.DEFAULT_CONS_RATE_MS + "ms");
        procVal = sliderVal(SimulationController.DEFAULT_PROC_TIME_MS + "ms");

        capSlider.valueProperty().addListener((o, ov, nv) -> {
            int cap = nv.intValue(); capVal.setText(String.valueOf(cap));
            controller.setBufferCapacity(cap); rebuildSlots(cap);
        });
        prodSlider.valueProperty().addListener((o, ov, nv) -> {
            int ms = nv.intValue(); prodVal.setText(ms + "ms");
            controller.setProductionRateMs(ms);
        });
        consSlider.valueProperty().addListener((o, ov, nv) -> {
            int ms = nv.intValue(); consVal.setText(ms + "ms");
            controller.setConsumptionRateMs(ms);
        });
        procSlider.valueProperty().addListener((o, ov, nv) -> {
            int ms = nv.intValue(); procVal.setText(ms + "ms");
            controller.setProcessingTimeMs(ms);
        });

        HBox bar = new HBox(26,
            sliderGroup("Buffer Capacity",       capSlider,  capVal),  vLine(),
            sliderGroup("Producer Speed (ms)",   prodSlider, prodVal), vLine(),
            sliderGroup("Consumer Speed (ms)",   consSlider, consVal), vLine(),
            sliderGroup("Processing Time (ms)",  procSlider, procVal)
        );
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(14, 24, 14, 24));
        bar.setStyle("-fx-background-color:#141929;" +
                     "-fx-border-color:#252f4a; -fx-border-width:1 0 0 0;");
        return bar;
    }

    // ── Game Loop ─────────────────────────────────────────────────────────────

    private void startGameLoop() {
        gameLoop = new AnimationTimer() {
            long lastTick = 0;
            @Override public void handle(long now) {
                if (now - lastTick < 120_000_000L) return;
                lastTick = now;
                refresh();
            }
        };
        gameLoop.start();
    }

    private void refresh() {
        int totalParked = controller.getParkingLot().getTotalParked();
        int totalLeft   = controller.getParkingLot().getTotalLeft();

        // Queue arrivals for every car that parked but hasn't been shown yet
        while (totalParked > uiArrived) {
            if (!enqueueArrival()) break;
            uiArrived++;
        }

        // Queue departures for every car that left but hasn't been shown leaving yet
        while (totalLeft > uiDeparted) {
            if (!enqueueDeparture()) break;
            uiDeparted++;
        }

        Integer idx;
        while ((idx = arrivals.poll())   != null) animateArrival(idx);
        while ((idx = departures.poll()) != null) animateDeparture(idx);

        updateStats();
        updateNextEntryPreview();
        updateThreadDots();
    }

    private void releaseEntryLane() {
        if (!entryLaneQueue.isEmpty()) entryLaneQueue.poll().run();
        else entryLaneOccupied = false;
    }

    private void releaseExitLane() {
        if (!exitLaneQueue.isEmpty()) exitLaneQueue.poll().run();
        else exitLaneOccupied = false;
    }

    private boolean enqueueArrival() {
        long now = System.currentTimeMillis();
        if (entryLaneOccupied || exitLaneOccupied || (now - lastArrivalTime < MIN_ARRIVAL_INTERVAL_MS)) return false;
        int n = slotOccupied.length;
        for (int step = 0; step < n; step++) {
            int i = (nextArrivalScanIdx + step) % n;
            if (!slotOccupied[i] && !slotExiting[i]) {
                slotOccupied[i]    = true;
                slotAnimatingIn[i] = true;
                slotColor[i]       = Math.floorMod(colorIdx++, carAssetCount());
                arrivals.offer(i);
                entryLaneOccupied = true;
                lastArrivalTime   = now;
                nextArrivalScanIdx = (i + 1) % n;
                return true;
            }
        }
        return false;
    }

    private boolean enqueueDeparture() {
        int n = slotOccupied.length;
        for (int step = 0; step < n; step++) {
            int i = (nextDepartureScanIdx + step) % n;
            if (slotOccupied[i] && !slotAnimatingIn[i] && !slotExiting[i]) {
                slotOccupied[i] = false;
                slotExiting[i]  = true;
                departures.offer(i);
                nextDepartureScanIdx = (i + 1) % n;
                return true;
            }
        }
        return false;
    }

    // ── Animations ───────────────────────────────────────────────────────────

    /**
     * Entry path:
     *   1. Car spawns above the IN gate, facing south (rotation=0°).
     *   2. Drives straight down the center lane to the target slot's row Y.
     *   3. Rotates and slides into the parking bay simultaneously:
     *        Left  slot: rotates -90° → ends at -90° (headlights east = toward lane)
     *        Right slot: rotates +90° → ends at  90° (headlights west = toward lane)
     *   4. Animated car removed; slot ImageView fades in.
     */
    private void animateArrival(int slotIdx) {
        int     ci     = slotColor[slotIdx];
        boolean isLeft = isLeftSideSlot(slotIdx);
        double[] sc    = slotCenter(slotIdx);

        Node car = roadCar(ci);
        animLayer.getChildren().add(car);

        // Spawn just above the IN gate
        double startY = MAP_PARK_TOP_Y - ROAD_CAR_H - 8;
        car.setTranslateX(CENTER_LANE_X);
        car.setTranslateY(startY);
        car.setRotate(0);  // facing south (front of image is at bottom)

        // Phase 1: Drive down center lane to the target row
        double travelPx   = Math.abs(sc[1] - startY);
        long   driveDur   = Math.max(400, (long)(travelPx * 0.85));
        TranslateTransition driveLane = new TranslateTransition(Duration.millis(driveDur), car);
        driveLane.setToX(CENTER_LANE_X);
        driveLane.setToY(sc[1]);
        driveLane.setInterpolator(Interpolator.EASE_BOTH);

        // Phase 2: Rotate and slide into bay simultaneously
        RotateTransition turnIn = new RotateTransition(Duration.millis(280), car);
        turnIn.setByAngle(isLeft ? -90.0 : 90.0);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), car);
        slideIn.setToX(sc[0]);
        slideIn.setToY(sc[1]);
        slideIn.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition parkPhase = new ParallelTransition(turnIn, slideIn);
        parkPhase.setOnFinished(e -> releaseEntryLane());

        SequentialTransition seq = new SequentialTransition(driveLane, parkPhase);
        seq.setOnFinished(e -> {
            animLayer.getChildren().remove(car);
            slotAnimatingIn[slotIdx] = false;
            showParkedCar(slotIdx, ci, false);
        });

        // Open IN gate, start driving once it's raised, close after car clears
        openGate(inGateRotate, () -> {
            seq.play();
            PauseTransition closeDelay = new PauseTransition(Duration.millis(380));
            closeDelay.setOnFinished(e -> closeGate(inGateRotate));
            closeDelay.play();
        });
    }

    /**
     * Exit path — ALL cars leave through the BOTTOM OUT gate only:
     *   1. Fade out the parked ImageView.
     *   2. Spawn animated car at slot center with parked rotation.
     *   3. Pull forward out of slot into the center lane (lateral translate).
     *   4. Rotate to face south (180°) simultaneously with the pull-out.
     *   5. Drive downward through the center lane and exit at the bottom.
     */
    private void animateDeparture(int slotIdx) {
        boolean isLeft = isLeftSideSlot(slotIdx);
        double[] sc    = slotCenter(slotIdx);
        int      ci    = slotIdx < slotColor.length ? slotColor[slotIdx] : 0;

        exitLaneOccupied = true;

        // Fade out parked ImageView
        ImageView iv = slotViews[slotIdx];
        FadeTransition fadeSlot = new FadeTransition(Duration.millis(180), iv);
        fadeSlot.setToValue(0);
        fadeSlot.setOnFinished(e -> iv.setVisible(false));
        fadeSlot.play();

        // Animated car at parked position/orientation
        Node car = roadCar(ci);
        car.setRotate(parkedRotation(slotIdx));   // 90° left, -90° right
        car.setTranslateX(sc[0]);
        car.setTranslateY(sc[1]);
        animLayer.getChildren().add(car);

        // Phase 1: Pull forward into center lane + rotate to face south simultaneously
        //   Left  slot starts at -90° + 90°  = 0° (south) ✓
        //   Right slot starts at  90° + (-90°) = 0° (south) ✓
        TranslateTransition pullOut = new TranslateTransition(Duration.millis(320), car);
        pullOut.setToX(CENTER_LANE_X);
        pullOut.setToY(sc[1]);
        pullOut.setInterpolator(Interpolator.EASE_BOTH);

        RotateTransition turnExit = new RotateTransition(Duration.millis(320), car);
        turnExit.setByAngle(isLeft ? 90.0 : -90.0);

        ParallelTransition mergeToLane = new ParallelTransition(pullOut, turnExit);

        // Phase 2: Drive south to the OUT gate and beyond
        double exitY = MAP_PARK_BOT_Y + ROAD_CAR_H + 20;
        long   exitDur = Math.max(380, (long)(Math.abs(exitY - sc[1]) * 0.85));
        TranslateTransition driveOut = new TranslateTransition(Duration.millis(exitDur), car);
        driveOut.setToX(CENTER_LANE_X);
        driveOut.setToY(exitY);
        driveOut.setInterpolator(Interpolator.EASE_BOTH);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(exitDur), car);
        fadeOut.setToValue(0);
        ParallelTransition exitDrive = new ParallelTransition(driveOut, fadeOut);

        exitDrive.setOnFinished(e -> {
            animLayer.getChildren().remove(car);
            slotExiting[slotIdx] = false;
            releaseExitLane();
        });

        // Open OUT gate when car reaches the lane, close after it passes the barrier
        mergeToLane.setOnFinished(e -> openGate(outGateRotate, () -> {
            exitDrive.play();
            PauseTransition closeDelay = new PauseTransition(Duration.millis(420));
            closeDelay.setOnFinished(e2 -> closeGate(outGateRotate));
            closeDelay.play();
        }));

        mergeToLane.play();
    }

    /** Makes the slot ImageView visible with fade, using the correct orientation. */
    private void showParkedCar(int slotIdx, int ci, boolean unused) {
        if (slotIdx >= slotViews.length) return;
        ImageView iv = slotViews[slotIdx];
        iv.setImage(loadedCarImage(ci));
        if (iv.getImage() == null) { iv.setVisible(false); iv.setOpacity(0); return; }
        iv.setRotate(parkedRotation(slotIdx));
        iv.setOpacity(0); iv.setVisible(true);
        FadeTransition ft = new FadeTransition(Duration.millis(300), iv);
        ft.setToValue(1.0); ft.play();
    }

    /** Returns screen-centre [x, y] of a parking slot. */
    private double[] slotCenter(int idx) {
        int leftCount = leftSideSlotCount();
        if (idx < leftCount)
            return new double[]{ MAP_LEFT_SLOT_X, slotRowCenterY(idx, leftCount) };
        int i = idx - leftCount;
        return new double[]{ MAP_RIGHT_SLOT_X, slotRowCenterY(i, rightSideSlotCount()) };
    }

    /** Animated car node used on the driving lane. */
    private Node roadCar(int colorIndex) {
        ImageView iv = new ImageView(loadedCarImage(colorIndex));
        iv.setFitWidth(ROAD_CAR_W); iv.setFitHeight(ROAD_CAR_H);
        iv.setPreserveRatio(false); iv.setSmooth(true);
        Group group = new Group(iv);
        iv.setTranslateX(-ROAD_CAR_W / 2.0);
        iv.setTranslateY(-ROAD_CAR_H / 2.0);
        return group;
    }

    private void rebuildSlots(int newCap) {
        boolean[] nextOcc    = new boolean[newCap];
        boolean[] nextExit   = new boolean[newCap];
        boolean[] nextAnimIn = new boolean[newCap];
        int[]     nextColor  = new int[newCap];
        int copy = Math.min(newCap, slotOccupied.length);
        System.arraycopy(slotOccupied, 0, nextOcc,    0, copy);
        System.arraycopy(slotColor,   0, nextColor,   0, copy);
        slotOccupied    = nextOcc;
        slotExiting     = nextExit;
        slotAnimatingIn = nextAnimIn;
        slotColor       = nextColor;
        numSlots        = newCap;
        nextArrivalScanIdx = 0;
        nextDepartureScanIdx = 0;
        uiDeparted = controller.getParkingLot().getTotalLeft();
        uiArrived  = uiDeparted + controller.getParkingLot().getOccupied();
        drawParkingLot();
        buildSlotViews();
        for (int i = 0; i < numSlots; i++)
            if (slotOccupied[i]) showParkedCar(i, slotColor[i], false);
        updateNextEntryPreview();
    }

    // ── Live Update ───────────────────────────────────────────────────────────

    private void updateStats() {
        var lot   = controller.getParkingLot();
        var stats = controller.getStats();
        // Count actual visually parked cars from UI state
        int visualOccupied = 0;
        for (boolean occupied : slotOccupied) {
            if (occupied) visualOccupied++;
        }
        double occupancyPercent = visualOccupied > 0 ? 
            (100.0 * visualOccupied / numSlots) : 0.0;
        if (occupancyLbl  != null) occupancyLbl.setText(
                visualOccupied + " / " + numSlots +
                " (" + String.format("%.0f", occupancyPercent) + "%)");
        if (producedLbl   != null) producedLbl.setText(String.valueOf(stats.getTotalProduced()));
        if (consumedLbl   != null) consumedLbl.setText(String.valueOf(stats.getTotalConsumed()));
        if (throughputLbl != null) throughputLbl.setText(String.format("%.1f/s", stats.getThroughput()));
        if (waitTimeLbl   != null) waitTimeLbl.setText(formatMillis(stats.getAverageWaitTimeMs()));
    }

    private void updateNextEntryPreview() { /* road visualization is static */ }

    private void updateThreadDots() {
        for (Circle dot : threadDots.values()) dot.setFill(Color.web("#475569"));
        for (ProducerThread p : controller.getProducers()) {
            Circle dot = threadDots.get("P" + p.getProducerId());
            if (dot != null) dot.setFill(statusColor(p.getStatus()));
        }
        for (ConsumerThread c : controller.getConsumers()) {
            Circle dot = threadDots.get("C" + c.getConsumerId());
            if (dot != null) dot.setFill(statusColor(c.getStatus()));
        }
    }

    private Color statusColor(ThreadStatus s) {
        return switch (s) {
            case ACTIVE  -> Color.web("#4caf7d");
            case WAITING -> Color.web("#fbbf24");
            case CRASHED -> Color.web("#f87171");
        };
    }

    // ── Button Handlers ───────────────────────────────────────────────────────

    private void onStart() {
        controller.start();
        startBtn.setDisable(true); stopBtn.setDisable(false);
        badge("RUNNING", "badge-running");
    }

    private void onStop() {
        controller.stop();
        startBtn.setDisable(false); stopBtn.setDisable(true);
        badge("STOPPED", "badge-stopped");
        updateStats(); updateThreadDots();
    }

    private void onReset() {
        controller.stop(); controller.reset();
        Arrays.fill(slotOccupied,    false);
        Arrays.fill(slotExiting,     false);
        Arrays.fill(slotAnimatingIn, false);
        uiArrived = 0; uiDeparted = 0; colorIdx = 0;
        nextArrivalScanIdx = 0; nextDepartureScanIdx = 0;
        entryLaneOccupied = false; exitLaneOccupied = false;
        entryLaneQueue.clear(); exitLaneQueue.clear();
        animLayer.getChildren().clear();
        for (ImageView iv : slotViews) { iv.setVisible(false); iv.setOpacity(0); }
        startBtn.setDisable(false); stopBtn.setDisable(true);
        badge("STOPPED", "badge-stopped");
        updateStats(); updateNextEntryPreview(); updateThreadDots();
    }

    private void badge(String text, String cls) {
        statusBadge.setText(text);
        statusBadge.getStyleClass().setAll(cls);
    }

    // ── UI Component Helpers ──────────────────────────────────────────────────

    private Button styledBtn(String text, String cls) {
        Button b = new Button(text);
        b.getStyleClass().addAll("sim-btn", cls);
        return b;
    }

    private HBox threadRow(String key, String name) {
        Circle dot = new Circle(6, Color.web("#475569"));
        threadDots.put(key, dot);
        Label lbl = new Label(name);
        lbl.setStyle("-fx-font-size:12px; -fx-text-fill:#94a3b8;");
        HBox row = new HBox(10, dot, lbl);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox legendRow(String hex, String text) {
        Circle dot = new Circle(5, Color.web(hex));
        Label  lbl = new Label(text);
        lbl.setStyle("-fx-font-size:11px; -fx-text-fill:#64748b;");
        HBox row = new HBox(8, dot, lbl);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Label statEntry(VBox parent, String name, String value) {
        Label nm  = new Label(name);  nm.setStyle("-fx-font-size:10px; -fx-text-fill:#475569;");
        Label val = new Label(value); val.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:#e2e8f0;");
        parent.getChildren().add(new VBox(1, nm, val));
        return val;
    }

    private Slider slider(double min, double max, double val) {
        Slider s = new Slider(min, max, val);
        s.getStyleClass().add("sim-slider");
        s.setPrefWidth(155);
        return s;
    }

    private Label sliderVal(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:#4f9eff; -fx-min-width:55px;");
        return l;
    }

    private VBox sliderGroup(String title, Slider s, Label val) {
        Label lbl = new Label(title); lbl.setStyle("-fx-font-size:11px; -fx-text-fill:#64748b;");
        HBox row  = new HBox(10, s, val); row.setAlignment(Pos.CENTER_LEFT);
        return new VBox(4, lbl, row);
    }

    private Label sectionLbl(String text) {
        Label l = new Label(text); l.setStyle("-fx-font-size:11px; -fx-text-fill:#64748b;");
        return l;
    }

    private Region hLine() {
        Region r = new Region(); r.setPrefHeight(1);
        r.setStyle("-fx-background-color:#252f4a;");
        VBox.setMargin(r, new Insets(4, 0, 4, 0));
        return r;
    }

    private Region vLine() {
        Region r = new Region(); r.setPrefWidth(1); r.setPrefHeight(46);
        r.setStyle("-fx-background-color:#252f4a;");
        return r;
    }

    public static void main(String[] args) {
        if (System.getenv("WSL_DISTRO_NAME") != null)
            System.setProperty("prism.order", "sw");
        launch(args);
    }
}
