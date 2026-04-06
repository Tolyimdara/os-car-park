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
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Car Park Simulation — JavaFX UI.
 * Modern professional dashboard with soft cartoon-style animated background.
 * All simulation logic, threading, and animation workflows are unchanged.
 */
public class CarParkApp extends Application {

    // ── Layout constants (unchanged) ──────────────────────────────────────────
    private static final int SW     = 70;   // space width
    private static final int SH     = 80;   // space height
    private static final int LANE_H = 80;   // lane height
    private static final int GAP    = 8;    // gap between spaces
    private static final int H_PAD  = 42;   // left/right road padding trimmed to active lot width
    private static final int V_PAD  = 20;   // top/bottom padding
    private static final int CAR_W  = 28;   // car body width  (top-down)
    private static final int CAR_H  = 46;   // car body height (top-down)
    private static final int RATE_SLIDER_MAX_MS = 6000;
    private static final int PROC_SLIDER_MAX_MS = 3000;
    private static final int DEFAULT_UI_PROD_RATE_MS = SimulationController.DEFAULT_PROD_RATE_MS;
    private static final int DEFAULT_UI_CONS_RATE_MS = SimulationController.DEFAULT_CONS_RATE_MS;
    private static final int DEFAULT_UI_PROC_TIME_MS = SimulationController.DEFAULT_PROC_TIME_MS;
    private static final int SLOT_ALIGN_MS = 700;
    private static final int SLOT_PARK_MS = 900;
    private static final int SLOT_PULL_OUT_MS = 800;

    // ── Modern professional colour palette ────────────────────────────────────
    private static final String C_BG           = "#EEF2FF";   // indigo-50 background
    private static final String C_SURFACE      = "#FFFFFF";   // card surface
    private static final String C_PRIMARY      = "#2563EB";   // blue-600
    private static final String C_PRIMARY_MID  = "#3B82F6";   // blue-500
    private static final String C_ACCENT       = "#0EA5E9";   // sky-500
    private static final String C_TEXT         = "#1E293B";   // slate-800
    private static final String C_MUTED        = "#64748B";   // slate-500
    private static final String C_BORDER       = "#E2E8F0";   // slate-200
    private static final String C_RED          = "#DC2626";   // red-600
    private static final String C_ORANGE       = "#D97706";   // amber-600
    private static final String C_SUCCESS      = "#16A34A";   // green-600

    // Parking-lot visuals — deep slate for high contrast
    private static final String C_FLOOR   = "#1E293B";   // slate-900
    private static final String C_LANE    = "#334155";   // slate-800
    private static final String C_SPACE_E = "#475569";   // slate-600  (empty)
    private static final String C_SPACE_R = "#D97706";   // amber-600  (reserved / clearing)
    private static final String C_SPACE_O = "#15803D";   // green-700  (occupied)

    // Cartoon background palette
    private static final String C_TRUNK   = "#92400E";   // amber-800
    private static final String C_CROWN   = "#15803D";   // green-700
    private static final String C_CROWN_L = "#16A34A";   // green-600

    // ── Brighter cartoon car colours ──────────────────────────────────────────
    private static final Color[] CAR_COLORS = {
        Color.web("#EF4444"), Color.web("#3B82F6"), Color.web("#22C55E"),
        Color.web("#F97316"), Color.web("#A855F7"), Color.web("#06B6D4"),
        Color.web("#EC4899"), Color.web("#EAB308"), Color.web("#14B8A6"),
        Color.web("#6366F1")
    };

    // ── Geometry (set in start()) ─────────────────────────────────────────────
    private int    capacity;
    private int    nCols;
    private double lotW, lotH, laneCY, entryLaneCY, exitLaneCY;

    // ── Simulation state (unchanged) ──────────────────────────────────────────
    private SimulationController controller;
    private Timeline             updateLoop;

    private Pane lotPane;
    private final List<Rectangle> spaceRects = new ArrayList<>();
    private Group[]    parkedCars;
    private Animation[] activeAnims;
    private boolean[]  slotParked;

    private int prevTotalParked = 0;
    private int prevTotalLeft   = 0;
    private int pendingArrivals = 0;
    private int pendingDepartures = 0;
    private final Deque<Integer> freeSlots  = new ArrayDeque<>();
    private final Deque<Integer> takenSlots = new ArrayDeque<>();

    private boolean entryLaneOccupied = false;
    private boolean exitLaneOccupied  = false;
    private final Deque<Runnable> entryLaneQueue = new ArrayDeque<>();
    private final Deque<Runnable> exitLaneQueue  = new ArrayDeque<>();

    private int  visualOccupied = 0;
    private int  visualTotalIn  = 0;
    private int  visualTotalOut = 0;
    private long simStartMs     = 0;

    // ── UI widgets (unchanged) ────────────────────────────────────────────────
    private Label occupiedLbl, availableLbl, occupancyLbl;
    private Label throughputLbl, waitingLbl, arrivedLbl, processedLbl;
    private ProgressBar occupancyBar;
    private VBox   threadPanel;
    private Button stopBtn;
    private Slider capacitySlider, prodRateSlider, consRateSlider, procTimeSlider;
    private Label  capacityValLbl, prodRateValLbl, consRateValLbl, procTimeValLbl;
    private Label  statusBadge;

    // ── Animated background clouds ────────────────────────────────────────────
    private final List<Group> clouds = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void start(Stage stage) {
        controller  = new SimulationController();
        updateLotMetrics(SimulationController.DEFAULT_CAPACITY);
        parkedCars  = new Group[capacity];
        activeAnims = new Animation[capacity];
        slotParked  = new boolean[capacity];
        freeSlots.clear(); takenSlots.clear();
        for (int i = 0; i < capacity; i++) freeSlots.offer(i);

        // Animated background layer (sits behind all UI)
        Pane bgPane = buildAnimatedBackground();

        // Functional UI layout (transparent — cards have their own bg)
        BorderPane layout = new BorderPane();
        layout.setStyle("-fx-background-color:transparent;");
        layout.setTop(buildHeader());
        layout.setCenter(buildCenter());
        layout.setRight(buildThreadPanel());

        StackPane root = new StackPane(bgPane, layout);

        Scene scene = new Scene(root, 1100, 680);
        bgPane.prefWidthProperty().bind(scene.widthProperty());
        bgPane.prefHeightProperty().bind(scene.heightProperty());

        stage.setTitle("Car Park Management Sim — RUPP OS");
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setMinWidth(920);
        stage.setMinHeight(560);
        stage.show();

        simStartMs = System.currentTimeMillis();
        startUpdateLoop();
        applyDefaultSpeedSettings();
        controller.start();
        stopBtn.setDisable(false);
        setStatusBadge(true);
        startCloudAnimation(scene);
    }

    private void updateLotMetrics(int newCapacity) {
        capacity = newCapacity;
        nCols = Math.max(topRowCountForCapacity(newCapacity), bottomRowCountForCapacity(newCapacity));
        lotW = H_PAD + nCols * (SW + GAP) - GAP + H_PAD;
        lotH = V_PAD + SH + LANE_H + SH + V_PAD;
        laneCY = V_PAD + SH + LANE_H / 2.0;
        entryLaneCY = V_PAD + SH + LANE_H * 0.34;
        exitLaneCY = V_PAD + SH + LANE_H * 0.66;
    }

    private int topRowCount() {
        return topRowCountForCapacity(capacity);
    }

    private int bottomRowCount() {
        return bottomRowCountForCapacity(capacity);
    }

    private int topRowCountForCapacity(int cap) {
        return (cap + 1) / 2;
    }

    private int bottomRowCountForCapacity(int cap) {
        return cap / 2;
    }

    private boolean isTopSlot(int idx) {
        return idx % 2 == 0;
    }

    private int slotRowPosition(int idx) {
        return idx / 2;
    }

    private double rowStartX(boolean top) {
        return H_PAD;
    }

    private double slotX(int idx) {
        return rowStartX(isTopSlot(idx)) + slotRowPosition(idx) * (SW + GAP);
    }

    private double slotCenterX(int idx) {
        return slotX(idx) + SW / 2.0;
    }

    private double slotCenterY(int idx) {
        return isTopSlot(idx) ? V_PAD + SH / 2.0 : V_PAD + SH + LANE_H + SH / 2.0;
    }

    private double slotMouthY(int idx) {
        return isTopSlot(idx) ? V_PAD + SH + 10.0 : V_PAD + SH + LANE_H - 10.0;
    }

    private double laneTravelMillis(double fromX, double toX) {
        return 900.0 + Math.abs(toX - fromX) * 1.5;
    }

    private void applyDefaultSpeedSettings() {
        controller.setProductionRateMs(DEFAULT_UI_PROD_RATE_MS);
        controller.setConsumptionRateMs(DEFAULT_UI_CONS_RATE_MS);
        controller.setProcessingTimeMs(DEFAULT_UI_PROC_TIME_MS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Animated cartoon background
    // ─────────────────────────────────────────────────────────────────────────
    private Pane buildAnimatedBackground() {
        Pane bg = new Pane();
        bg.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        // Sky gradient via two rectangles blending top-to-bottom
        bg.setStyle(
            "-fx-background-color:linear-gradient(to bottom, #BFDBFE 0%, #DBEAFE 55%, #EFF6FF 100%);"
        );

        // Grass strips — top and bottom decorative bands
        Rectangle grassTop = new Rectangle(0, 0, 1920, 52);
        grassTop.setFill(Color.web("#86EFAC", 0.50));
        Rectangle grassTop2 = new Rectangle(0, 44, 1920, 14);
        grassTop2.setFill(Color.web("#4ADE80", 0.30));

        Rectangle grassBot = new Rectangle(0, 636, 1920, 80);
        grassBot.setFill(Color.web("#86EFAC", 0.45));
        Rectangle grassBot2 = new Rectangle(0, 630, 1920, 12);
        grassBot2.setFill(Color.web("#4ADE80", 0.28));

        bg.getChildren().addAll(grassTop, grassTop2, grassBot, grassBot2);

        // Faint road hint at bottom — parking lot driveway context
        Rectangle bgRoad = new Rectangle(0, 598, 1920, 42);
        bgRoad.setFill(Color.web("#94A3B8", 0.20));
        Rectangle roadLine = new Rectangle(0, 616, 1920, 2);
        roadLine.setFill(Color.web("#CBD5E1", 0.28));
        bg.getChildren().addAll(bgRoad, roadLine);

        // Trees — top grass strip, full width
        bg.getChildren().addAll(
            makeCartoonTree(  42, 38, 0.80),
            makeCartoonTree( 118, 30, 0.96),
            makeCartoonTree( 195, 38, 0.86),
            makeCartoonTree( 268, 32, 0.78),
            makeCartoonTree( 340, 40, 0.90),
            makeCartoonTree( 415, 28, 0.82),
            makeCartoonTree( 488, 36, 0.74),
            makeCartoonTree( 560, 34, 0.88),
            makeCartoonTree( 634, 30, 0.80),
            makeCartoonTree( 708, 38, 0.76),
            makeCartoonTree( 782, 32, 0.84),
            makeCartoonTree( 855, 28, 0.94),
            makeCartoonTree( 930, 36, 0.88),
            makeCartoonTree(1005, 30, 0.80),
            makeCartoonTree(1078, 38, 0.86),
            makeCartoonTree(1150, 26, 0.78),
            makeCartoonTree(1225, 34, 0.90),
            makeCartoonTree(1300, 30, 0.82),
            makeCartoonTree(1375, 38, 0.76),
            makeCartoonTree(1450, 28, 0.84),
            makeCartoonTree(1525, 36, 0.72),
            makeCartoonTree(1600, 32, 0.86),
            makeCartoonTree(1675, 40, 0.78),
            makeCartoonTree(1748, 28, 0.82),
            makeCartoonTree(1822, 36, 0.88),
            makeCartoonTree(1895, 30, 0.76)
        );
        // Trees — bottom grass strip, full width
        bg.getChildren().addAll(
            makeCartoonTree(  60, 648, 0.76),
            makeCartoonTree( 155, 654, 0.82),
            makeCartoonTree( 250, 646, 0.72),
            makeCartoonTree( 345, 652, 0.80),
            makeCartoonTree( 440, 644, 0.78),
            makeCartoonTree( 535, 650, 0.84),
            makeCartoonTree( 630, 646, 0.74),
            makeCartoonTree( 725, 654, 0.80),
            makeCartoonTree( 820, 648, 0.76),
            makeCartoonTree( 915, 652, 0.86),
            makeCartoonTree(1010, 644, 0.72),
            makeCartoonTree(1105, 650, 0.80),
            makeCartoonTree(1200, 646, 0.78),
            makeCartoonTree(1295, 654, 0.82),
            makeCartoonTree(1390, 648, 0.74),
            makeCartoonTree(1485, 652, 0.86),
            makeCartoonTree(1580, 644, 0.78),
            makeCartoonTree(1675, 650, 0.80),
            makeCartoonTree(1770, 648, 0.76),
            makeCartoonTree(1865, 654, 0.82)
        );

        // Clouds
        clouds.clear();
        clouds.add(makeCloud(110,  40, 0.92));
        clouds.add(makeCloud(370,  22, 0.80));
        clouds.add(makeCloud(620,  48, 0.88));
        clouds.add(makeCloud(860,  30, 0.76));
        clouds.add(makeCloud(-90,  58, 0.84));
        clouds.add(makeCloud(1160, 36, 0.78));
        bg.getChildren().addAll(clouds);

        return bg;
    }

    /**
     * Cartoon-style tree: layered ellipses for a soft depth effect,
     * with a subtle SwayTransition on the crown layers.
     */
    private Group makeCartoonTree(double x, double y, double scale) {
        // Trunk
        Rectangle trunk = new Rectangle(-7, 0, 14, 30);
        trunk.setFill(Color.web(C_TRUNK));
        trunk.setArcWidth(5); trunk.setArcHeight(5);

        // Ground shadow
        Ellipse gShadow = new Ellipse(1, 30, 12, 4);
        gShadow.setFill(Color.web("#000000", 0.10));

        // Crown layers — back (darkest) → front (lightest highlight)
        Ellipse crownBack = new Ellipse(0, -22, 28, 25);
        crownBack.setFill(Color.web("#14532D"));

        Ellipse crownMid = new Ellipse(0, -28, 24, 22);
        crownMid.setFill(Color.web(C_CROWN));

        Ellipse crownFront = new Ellipse(-3, -34, 17, 17);
        crownFront.setFill(Color.web(C_CROWN_L));

        Ellipse crownTop = new Ellipse(-1, -41, 11, 11);
        crownTop.setFill(Color.web("#4ADE80", 0.72));

        Group tree = new Group(gShadow, trunk, crownBack, crownMid, crownFront, crownTop);
        tree.setLayoutX(x);
        tree.setLayoutY(y);
        tree.setScaleX(scale);
        tree.setScaleY(scale);

        // Subtle, randomised sway for each crown layer
        double period = 2.4 + Math.random() * 2.2;

        TranslateTransition swayMid = new TranslateTransition(Duration.seconds(period), crownMid);
        swayMid.setFromX(-2.0); swayMid.setToX(2.0);
        swayMid.setAutoReverse(true);
        swayMid.setCycleCount(Animation.INDEFINITE);
        swayMid.setInterpolator(Interpolator.EASE_BOTH);
        swayMid.play();

        TranslateTransition swayFront = new TranslateTransition(Duration.seconds(period * 0.78), crownFront);
        swayFront.setFromX(-3.0); swayFront.setToX(3.0);
        swayFront.setAutoReverse(true);
        swayFront.setCycleCount(Animation.INDEFINITE);
        swayFront.setInterpolator(Interpolator.EASE_BOTH);
        swayFront.play();

        TranslateTransition swayTop = new TranslateTransition(Duration.seconds(period * 0.65), crownTop);
        swayTop.setFromX(-4.0); swayTop.setToX(4.0);
        swayTop.setAutoReverse(true);
        swayTop.setCycleCount(Animation.INDEFINITE);
        swayTop.setInterpolator(Interpolator.EASE_BOTH);
        swayTop.play();

        return tree;
    }

    /** Fluffy cloud built from overlapping ellipses and a rounded base rect. */
    private Group makeCloud(double x, double y, double opacity) {
        Rectangle base = new Rectangle(0, 20, 82, 30);
        base.setFill(Color.web("#FFFFFF", opacity));
        base.setArcWidth(30); base.setArcHeight(30);

        Ellipse p1 = new Ellipse(18, 20, 20, 18);
        p1.setFill(Color.web("#FFFFFF", opacity));
        Ellipse p2 = new Ellipse(42, 13, 23, 21);
        p2.setFill(Color.web("#FFFFFF", opacity));
        Ellipse p3 = new Ellipse(66, 22, 17, 15);
        p3.setFill(Color.web("#FFFFFF", opacity * 0.88));

        // Soft under-shadow
        Ellipse shadow = new Ellipse(40, 50, 37, 6);
        shadow.setFill(Color.web("#94A3B8", 0.10));

        Group cloud = new Group(shadow, base, p1, p2, p3);
        cloud.setLayoutX(x);
        cloud.setLayoutY(y);
        return cloud;
    }

    /** One shared Timeline drifts all clouds leftward at individual speeds. */
    private void startCloudAnimation(Scene scene) {
        double[] speeds = {0.16, 0.11, 0.14, 0.09, 0.13, 0.10};
        Timeline tl = new Timeline(new KeyFrame(Duration.millis(16), e -> {
            double wrap = scene.getWidth() + 130;
            for (int i = 0; i < clouds.size(); i++) {
                Group c = clouds.get(i);
                double nx = c.getLayoutX() - speeds[i % speeds.length];
                if (nx < -130) nx = wrap;
                c.setLayoutX(nx);
            }
        }));
        tl.setCycleCount(Animation.INDEFINITE);
        tl.play();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Header — dark navy bar for strong contrast
    // ─────────────────────────────────────────────────────────────────────────
    private HBox buildHeader() {
        HBox h = new HBox(14);
        h.setPadding(new Insets(13, 24, 13, 24));
        h.setAlignment(Pos.CENTER_LEFT);
        h.setStyle(
            "-fx-background-color:rgba(15,23,42,0.97);" +
            "-fx-border-color:#1E3A5F;" +
            "-fx-border-width:0 0 1 0;"
        );

        // Blue accent bar
        Rectangle accent = new Rectangle(4, 38);
        accent.setFill(Color.web(C_PRIMARY_MID));
        accent.setArcWidth(4); accent.setArcHeight(4);

        // "P" badge icon
        Label icon = new Label("P");
        icon.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
        icon.setTextFill(Color.WHITE);
        icon.setPadding(new Insets(5, 11, 5, 11));
        icon.setOpacity(0.5);
        icon.setStyle(
            "-fx-background-color:" + C_PRIMARY + ";" +
            "-fx-background-radius:9;"
        );

        Label title = new Label("Car Park Management Sim");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        title.setTextFill(Color.WHITE);

        Label sub = new Label("Producer\u2013Consumer Simulation  \u00b7  RUPP Operating Systems");
        sub.setFont(Font.font("Segoe UI", 11));
        sub.setTextFill(Color.web("#94A3B8"));

        VBox titleBox = new VBox(2, title, sub);

        // Live status badge
        statusBadge = new Label("\u25cf  RUNNING");
        statusBadge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        statusBadge.setPadding(new Insets(4, 12, 4, 12));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        stopBtn         = mkBtn("\u25a0  Stop",  "#EF4444");
        Button resetBtn = mkBtn("\u21ba  Reset", C_PRIMARY_MID);
        stopBtn.setDisable(true);

        stopBtn.setOnAction(e -> {
            controller.stop();
            stopBtn.setDisable(true);
            setStatusBadge(false);
        });
        resetBtn.setOnAction(e -> {
            controller.reset();
            clearLot();
            stopBtn.setDisable(false);
            applyDefaultSpeedSettings();
            controller.start();
            setStatusBadge(true);
            if (capacitySlider != null) capacitySlider.setValue(SimulationController.DEFAULT_CAPACITY);
            if (prodRateSlider  != null) prodRateSlider.setValue(DEFAULT_UI_PROD_RATE_MS);
            if (consRateSlider  != null) consRateSlider.setValue(DEFAULT_UI_CONS_RATE_MS);
            if (procTimeSlider  != null) procTimeSlider.setValue(DEFAULT_UI_PROC_TIME_MS);
        });

        h.getChildren().addAll(accent, icon, titleBox, statusBadge, spacer, stopBtn, resetBtn);
        return h;
    }

    private void setStatusBadge(boolean running) {
        if (statusBadge == null) return;
        if (running) {
            statusBadge.setText("\u25cf  RUNNING");
            statusBadge.setTextFill(Color.web("#4ADE80"));
            statusBadge.setStyle(
                "-fx-background-color:rgba(21,128,61,0.28);" +
                "-fx-background-radius:20;" +
                "-fx-border-color:#16A34A;" +
                "-fx-border-radius:20;" +
                "-fx-border-width:1;"
            );
        } else {
            statusBadge.setText("\u25a0  STOPPED");
            statusBadge.setTextFill(Color.web("#FCA5A5"));
            statusBadge.setStyle(
                "-fx-background-color:rgba(220,38,38,0.24);" +
                "-fx-background-radius:20;" +
                "-fx-border-color:#DC2626;" +
                "-fx-border-radius:20;" +
                "-fx-border-width:1;"
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Centre panel
    // ─────────────────────────────────────────────────────────────────────────
    private ScrollPane buildCenter() {
        VBox v = new VBox(16);
        v.setPadding(new Insets(20));
        v.getChildren().addAll(
            buildSummaryCards(),
            sectionHeader("PARKING LOT", "Live animated view \u2014 cars move in real time"),
            buildLotCard(),
            sectionHeader("SIMULATION CONTROLS", "Adjust all variables in real-time"),
            buildControlsCard()
        );

        ScrollPane sp = new ScrollPane(v);
        sp.setFitToWidth(true);
        sp.setStyle(
            "-fx-background:transparent;" +
            "-fx-background-color:transparent;" +
            "-fx-border-color:transparent;"
        );
        return sp;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Summary cards row
    // ─────────────────────────────────────────────────────────────────────────
    private HBox buildSummaryCards() {
        occupiedLbl   = bigValueLabel("0");
        availableLbl  = bigValueLabel(String.valueOf(capacity));
        throughputLbl = bigValueLabel("0.00/s");
        waitingLbl    = bigValueLabel("0 ms");

        occupancyLbl = new Label("0.0 %");
        occupancyLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        occupancyLbl.setTextFill(Color.web(C_TEXT));

        occupancyBar = new ProgressBar(0);
        occupancyBar.setPrefWidth(Double.MAX_VALUE);
        occupancyBar.setPrefHeight(10);
        occupancyBar.setStyle(
            "-fx-accent:" + C_SUCCESS + ";" +
            "-fx-background-radius:6;" +
            "-fx-control-inner-background:#E2E8F0;"
        );

        arrivedLbl   = midValueLabel("0");
        processedLbl = midValueLabel("0");

        Label trafficKey = cardKeyLabel("Traffic");
        HBox inRow  = new HBox(8, colorDot("#22C55E"), trafficNumLabel(arrivedLbl),   mutedLabel("entered"));
        HBox outRow = new HBox(8, colorDot("#EF4444"), trafficNumLabel(processedLbl), mutedLabel("exited"));
        inRow.setAlignment(Pos.CENTER_LEFT);
        outRow.setAlignment(Pos.CENTER_LEFT);
        VBox trafficCard = surfaceCard(new VBox(8, trafficKey, inRow, outRow));

        Label occKey = cardKeyLabel("Occupancy");
        VBox occBarCard = surfaceCard(new VBox(8, occKey, occupancyLbl, occupancyBar));

        HBox row = new HBox(12,
            statCard("Occupied",   occupiedLbl,   "#DCFCE7", C_SUCCESS),
            statCard("Available",  availableLbl,  "#DBEAFE", C_PRIMARY),
            statCard("Throughput", throughputLbl, "#FEF3C7", C_ORANGE),
            statCard("Avg Wait",   waitingLbl,    "#FCE7F3", "#BE185D"),
            occBarCard,
            trafficCard
        );
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Summary stat card with a coloured top strip and drop shadow. */
    private VBox statCard(String key, Label val, String bg, String strip) {
        Rectangle topStrip = new Rectangle(36, 4);
        topStrip.setFill(Color.web(strip));
        topStrip.setArcWidth(4); topStrip.setArcHeight(4);

        Label keyLbl = cardKeyLabel(key);
        VBox content = new VBox(6, topStrip, keyLbl, val);
        content.setPadding(new Insets(16));
        content.setStyle(
            "-fx-background-color:" + C_SURFACE + ";" +
            "-fx-background-radius:14;" +
            "-fx-border-color:" + C_BORDER + ";" +
            "-fx-border-radius:14;" +
            "-fx-border-width:1;" +
            "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.07),10,0,0,2);"
        );
        HBox.setHgrow(content, Priority.ALWAYS);
        return content;
    }

    /** Wraps any VBox in a white surface card with shadow. */
    private VBox surfaceCard(VBox inner) {
        inner.setPadding(new Insets(16));
        inner.setStyle(
            "-fx-background-color:" + C_SURFACE + ";" +
            "-fx-background-radius:14;" +
            "-fx-border-color:" + C_BORDER + ";" +
            "-fx-border-radius:14;" +
            "-fx-border-width:1;" +
            "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.07),10,0,0,2);"
        );
        HBox.setHgrow(inner, Priority.ALWAYS);
        return inner;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parking lot card wrapper
    // ─────────────────────────────────────────────────────────────────────────
    private VBox buildLotCard() {
        buildLotPane();

        ScrollPane lotScroll = new ScrollPane(lotPane);
        lotScroll.setFitToHeight(true);
        lotScroll.setStyle(
            "-fx-background:transparent;" +
            "-fx-background-color:transparent;" +
            "-fx-border-color:transparent;"
        );

        VBox card = new VBox(lotScroll);
        card.setPadding(new Insets(16));
        card.setStyle(
            "-fx-background-color:" + C_SURFACE + ";" +
            "-fx-background-radius:14;" +
            "-fx-border-color:" + C_BORDER + ";" +
            "-fx-border-radius:14;" +
            "-fx-border-width:1;" +
            "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.08),12,0,0,3);"
        );
        return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parking lot pane — static graphics + animated car nodes
    // ─────────────────────────────────────────────────────────────────────────
    private Pane buildLotPane() {
        lotPane = new Pane();
        lotPane.setPrefSize(lotW, lotH);
        lotPane.setMaxSize(lotW, lotH);
        lotPane.setMinSize(lotW, lotH);
        lotPane.setStyle("-fx-background-color:transparent;");

        drawLotBackground(lotPane, lotW, lotH);
        drawParkingSpaces(lotPane);
        return lotPane;
    }

    /**
     * Draws the static lot background (floor, curbs, lane, dashes, entry/exit labels).
     * Shared by buildLotPane() and rebuildLotPane() — uses the current laneCY field.
     */
    private void drawLotBackground(Pane p, double w, double h) {
        Rectangle floor = new Rectangle(0, 0, w, h);
        floor.setFill(Color.web(C_FLOOR));
        floor.setArcWidth(12); floor.setArcHeight(12);
        p.getChildren().add(floor);

        Rectangle curbTop = new Rectangle(0, 0, w, 5);
        curbTop.setFill(Color.web("#475569"));
        Rectangle curbBot = new Rectangle(0, h - 5, w, 5);
        curbBot.setFill(Color.web("#475569"));
        p.getChildren().addAll(curbTop, curbBot);

        Rectangle lane = new Rectangle(0, V_PAD + SH, w, LANE_H);
        lane.setFill(Color.web(C_LANE));
        p.getChildren().add(lane);

        Rectangle laneEdgeTop = new Rectangle(H_PAD - 4, V_PAD + SH, w - 2 * H_PAD + 8, 2);
        laneEdgeTop.setFill(Color.web("#FFFFFF", 0.18));
        Rectangle laneEdgeBot = new Rectangle(H_PAD - 4, V_PAD + SH + LANE_H - 2, w - 2 * H_PAD + 8, 2);
        laneEdgeBot.setFill(Color.web("#FFFFFF", 0.18));
        Rectangle laneDivider = new Rectangle(H_PAD - 4, laneCY - 1, w - 2 * H_PAD + 8, 2);
        laneDivider.setFill(Color.web("#FFFFFF", 0.10));
        p.getChildren().addAll(laneEdgeTop, laneEdgeBot, laneDivider);

        for (double x = H_PAD + 10; x < w - H_PAD - 22; x += 28) {
            Rectangle entryDash = new Rectangle(x, entryLaneCY - 2, 16, 4);
            entryDash.setFill(Color.web("#4ADE80", 0.55));
            entryDash.setArcWidth(2); entryDash.setArcHeight(2);

            Rectangle exitDash = new Rectangle(x, exitLaneCY - 2, 16, 4);
            exitDash.setFill(Color.web("#F87171", 0.55));
            exitDash.setArcWidth(2); exitDash.setArcHeight(2);

            p.getChildren().addAll(entryDash, exitDash);
        }

        Label inLbl = new Label("ENTRY ->");
        inLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        inLbl.setTextFill(Color.web("#4ADE80"));
        inLbl.setLayoutX(8);
        inLbl.setLayoutY(entryLaneCY - 24);
        p.getChildren().add(inLbl);

        Label outLbl = new Label("EXIT ->");
        outLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        outLbl.setTextFill(Color.web("#F87171"));
        outLbl.setLayoutX(w - 56);
        outLbl.setLayoutY(exitLaneCY + 6);
        p.getChildren().add(outLbl);
    }

    /** Draws parking-space rectangles + number labels. Populates spaceRects. */
    private void drawParkingSpaces(Pane p) {
        spaceRects.clear();
        for (int i = 0; i < capacity; i++) {
            boolean top = isTopSlot(i);
            double sx = slotX(i);
            double sy = top ? V_PAD : V_PAD + SH + LANE_H;

            Rectangle r = new Rectangle(sx, sy, SW, SH);
            r.setFill(Color.web(C_SPACE_E));
            r.setStroke(Color.web("#FFFFFF", 0.16));
            r.setStrokeWidth(1);
            r.setArcWidth(5); r.setArcHeight(5);
            spaceRects.add(r);
            p.getChildren().add(r);

            Rectangle frontMark = new Rectangle(sx, top ? sy + SH - 3 : sy, SW, 3);
            frontMark.setFill(Color.web("#FFFFFF", 0.10));
            p.getChildren().add(frontMark);

            Label num = new Label(String.valueOf(i + 1));
            num.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            num.setTextFill(Color.web("#FFFFFF", 0.52));
            num.setLayoutX(sx + SW / 2.0 - (i + 1 >= 10 ? 6 : 4));
            num.setLayoutY(top ? sy + SH - 17 : sy + 4);
            p.getChildren().add(num);
        }
    }
    // -----------------------------------------------------------------------------
    // Controls card
    // -----------------------------------------------------------------------------
    private VBox buildControlsCard() {
        capacitySlider = mkSlider(1,    30,   SimulationController.DEFAULT_CAPACITY);
        prodRateSlider = mkSlider(100,  RATE_SLIDER_MAX_MS, DEFAULT_UI_PROD_RATE_MS);
        consRateSlider = mkSlider(100,  RATE_SLIDER_MAX_MS, DEFAULT_UI_CONS_RATE_MS);
        procTimeSlider = mkSlider(50,   PROC_SLIDER_MAX_MS, DEFAULT_UI_PROC_TIME_MS);

        capacityValLbl = ctrlValueLabel(String.valueOf(SimulationController.DEFAULT_CAPACITY));
        prodRateValLbl = ctrlValueLabel(DEFAULT_UI_PROD_RATE_MS + " ms");
        consRateValLbl = ctrlValueLabel(DEFAULT_UI_CONS_RATE_MS + " ms");
        procTimeValLbl = ctrlValueLabel(DEFAULT_UI_PROC_TIME_MS + " ms");

        capacitySlider.valueProperty().addListener((obs, o, n) -> {
            int v = n.intValue();
            capacityValLbl.setText(String.valueOf(v));
            rebuildLotPane(v);
        });
        prodRateSlider.valueProperty().addListener((obs, o, n) -> {
            int v = n.intValue();
            prodRateValLbl.setText(v + " ms");
            controller.setProductionRateMs(v);
        });
        consRateSlider.valueProperty().addListener((obs, o, n) -> {
            int v = n.intValue();
            consRateValLbl.setText(v + " ms");
            controller.setConsumptionRateMs(v);
        });
        procTimeSlider.valueProperty().addListener((obs, o, n) -> {
            int v = n.intValue();
            procTimeValLbl.setText(v + " ms");
            controller.setProcessingTimeMs(v);
        });

        HBox controls = new HBox(20,
            ctrlGroup("Buffer Capacity",  capacitySlider, capacityValLbl), ctrlDivider(),
            ctrlGroup("Arrival Rate",     prodRateSlider, prodRateValLbl), ctrlDivider(),
            ctrlGroup("Guide Rate",       consRateSlider, consRateValLbl), ctrlDivider(),
            ctrlGroup("Processing Time",  procTimeSlider, procTimeValLbl)
        );
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(18));

        VBox card = new VBox(controls);
        card.setStyle(
            "-fx-background-color:" + C_SURFACE + ";" +
            "-fx-background-radius:14;" +
            "-fx-border-color:" + C_BORDER + ";" +
            "-fx-border-radius:14;" +
            "-fx-border-width:1;" +
            "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.07),10,0,0,2);"
        );
        return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Thread panel (right side)
    // ─────────────────────────────────────────────────────────────────────────
    private VBox buildThreadPanel() {
        threadPanel = new VBox(8);
        threadPanel.setPadding(new Insets(18));
        threadPanel.setPrefWidth(232);
        threadPanel.setStyle(
            "-fx-background-color:rgba(255,255,255,0.96);" +
            "-fx-border-color:" + C_BORDER + ";" +
            "-fx-border-width:0 0 0 1;"
        );
        threadPanel.getChildren().add(sectionHeader("THREADS", "Active thread monitor"));
        return threadPanel;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 250 ms update loop (unchanged)
    // ─────────────────────────────────────────────────────────────────────────
    private void startUpdateLoop() {
        updateLoop = new Timeline(new KeyFrame(Duration.millis(250), e -> refresh()));
        updateLoop.setCycleCount(Animation.INDEFINITE);
        updateLoop.play();
    }

    private void refresh() {
        refreshThreadPanel();

        double elapsed    = (System.currentTimeMillis() - simStartMs) / 1000.0;
        double throughput = elapsed > 1.0 ? visualTotalOut / elapsed : 0.0;
        double pct        = capacity > 0 ? visualOccupied * 100.0 / capacity : 0.0;

        occupiedLbl.setText(String.valueOf(visualOccupied));
        availableLbl.setText(String.valueOf(capacity - visualOccupied));
        occupancyLbl.setText(String.format("%.1f %%", pct));
        occupancyBar.setProgress(pct / 100.0);

        // Occupancy bar colour: green → amber → red
        String barColor = pct < 60 ? C_SUCCESS : pct < 85 ? "#F59E0B" : C_RED;
        occupancyBar.setStyle(
            "-fx-accent:" + barColor + ";" +
            "-fx-background-radius:6;" +
            "-fx-control-inner-background:#E2E8F0;"
        );

        throughputLbl.setText(String.format("%.2f/s", throughput));
        waitingLbl.setText(String.format("%.0f ms", controller.getStats().getAverageWaitTimeMs()));
        arrivedLbl.setText(String.valueOf(visualTotalIn));
        processedLbl.setText(String.valueOf(visualTotalOut));

        if (!controller.isRunning()) return;

        var lot = controller.getParkingLot();

        // Use cumulative counters — every park/leave event captured across ticks
        int totalParked   = lot.getTotalParked();
        int totalLeft     = lot.getTotalLeft();
        int newArrivals   = totalParked - prevTotalParked;
        int newDepartures = totalLeft   - prevTotalLeft;
        prevTotalParked = totalParked;
        prevTotalLeft   = totalLeft;

        pendingArrivals += Math.max(0, newArrivals);
        pendingDepartures += Math.max(0, newDepartures);
        dispatchPendingVisualEvents();
    }

    private void dispatchPendingVisualEvents() {
        while (pendingArrivals > 0 && !freeSlots.isEmpty()) {
            int slot = freeSlots.poll();
            pendingArrivals--;
            animateArrive(slot);
        }
        while (pendingDepartures > 0 && !takenSlots.isEmpty()) {
            int slot = takenSlots.poll();
            pendingDepartures--;
            animateLeave(slot);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // -----------------------------------------------------------------------------
    // Lane gates - entry and exit are managed independently
    // -----------------------------------------------------------------------------
    private void releaseEntryLane() {
        if (!entryLaneQueue.isEmpty()) {
            entryLaneQueue.poll().run();
        } else {
            entryLaneOccupied = false;
        }
    }

    private void releaseExitLane() {
        if (!exitLaneQueue.isEmpty()) {
            exitLaneQueue.poll().run();
        } else {
            exitLaneOccupied = false;
        }
    }
    // -----------------------------------------------------------------------------
    // Arrival animation: distinct entry lane -> slot mouth -> smooth park-in
    // -----------------------------------------------------------------------------
    private void animateArrive(int idx) {
        Runnable task = () -> doAnimateArrive(idx);
        if (entryLaneOccupied) {
            entryLaneQueue.offer(task);
        } else {
            entryLaneOccupied = true;
            task.run();
        }
    }

    private void doAnimateArrive(int idx) {
        if (idx >= capacity || lotPane == null) {
            releaseEntryLane();
            return;
        }

        if (activeAnims[idx] != null) { activeAnims[idx].stop(); activeAnims[idx] = null; }
        if (parkedCars[idx]  != null) { lotPane.getChildren().remove(parkedCars[idx]); parkedCars[idx] = null; }

        boolean top = isTopSlot(idx);
        double slotCX = slotCenterX(idx);
        double slotCY = slotCenterY(idx);
        double mouthY = slotMouthY(idx);
        double startX = -CAR_H / 2.0 - 24.0;

        Group car = makeCar(CAR_COLORS[idx % CAR_COLORS.length]);
        car.setLayoutX(startX);
        car.setLayoutY(entryLaneCY);
        car.setRotate(90);
        car.toFront();
        lotPane.getChildren().add(car);
        parkedCars[idx] = car;
        if (idx < spaceRects.size()) {
            spaceRects.get(idx).setFill(Color.web(C_SPACE_R));
        }

        TranslateTransition driveLane = new TranslateTransition(Duration.millis(laneTravelMillis(startX, slotCX)), car);
        driveLane.setToX(slotCX);
        driveLane.setToY(entryLaneCY);
        driveLane.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition merge = new TranslateTransition(Duration.millis(SLOT_ALIGN_MS), car);
        merge.setToX(slotCX);
        merge.setToY(mouthY);
        merge.setInterpolator(Interpolator.EASE_BOTH);

        RotateTransition turn = new RotateTransition(Duration.millis(SLOT_ALIGN_MS), car);
        turn.setToAngle(top ? 0 : 180);
        turn.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition alignToSlot = new ParallelTransition(merge, turn);
        alignToSlot.setOnFinished(e -> releaseEntryLane());

        TranslateTransition parkIn = new TranslateTransition(Duration.millis(SLOT_PARK_MS), car);
        parkIn.setToX(slotCX);
        parkIn.setToY(slotCY);
        parkIn.setInterpolator(Interpolator.EASE_BOTH);

        SequentialTransition seq = new SequentialTransition(driveLane, alignToSlot, parkIn);
        seq.setOnFinished(e -> {
            activeAnims[idx] = null;
            slotParked[idx] = true;
            takenSlots.offer(idx);
            if (idx < spaceRects.size()) {
                spaceRects.get(idx).setFill(Color.web(C_SPACE_O));
            }
            visualOccupied++;
            visualTotalIn++;
            dispatchPendingVisualEvents();
        });
        activeAnims[idx] = seq;
        seq.play();
    }
    // -----------------------------------------------------------------------------
    // Departure animation: slot -> exit lane -> exit road
    // -----------------------------------------------------------------------------
    private void animateLeave(int idx) {
        Runnable task = () -> doAnimateLeave(idx);
        if (exitLaneOccupied) {
            exitLaneQueue.offer(task);
        } else {
            exitLaneOccupied = true;
            task.run();
        }
    }

    private void doAnimateLeave(int idx) {
        if (idx >= capacity || lotPane == null) {
            releaseExitLane();
            return;
        }

        if (activeAnims[idx] != null) { activeAnims[idx].stop(); activeAnims[idx] = null; }

        Group car = parkedCars[idx];
        parkedCars[idx] = null;

        if (idx < spaceRects.size()) {
            spaceRects.get(idx).setFill(Color.web(C_SPACE_R));
        }

        if (car == null) {
            if (idx < spaceRects.size()) {
                spaceRects.get(idx).setFill(Color.web(C_SPACE_E));
            }
            slotParked[idx] = false;
            freeSlots.offer(idx);
            if (visualOccupied > 0) {
                visualOccupied--;
            }
            releaseExitLane();
            dispatchPendingVisualEvents();
            return;
        }

        double slotCX = slotCenterX(idx);
        double mouthY = slotMouthY(idx);
        double exitX = lotW + CAR_H / 2.0 + 24.0;

        TranslateTransition pullOut = new TranslateTransition(Duration.millis(SLOT_PULL_OUT_MS), car);
        pullOut.setToX(slotCX);
        pullOut.setToY(mouthY);
        pullOut.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition merge = new TranslateTransition(Duration.millis(SLOT_ALIGN_MS), car);
        merge.setToX(slotCX);
        merge.setToY(exitLaneCY);
        merge.setInterpolator(Interpolator.EASE_BOTH);

        RotateTransition turn = new RotateTransition(Duration.millis(SLOT_ALIGN_MS), car);
        turn.setToAngle(90);
        turn.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition alignToExit = new ParallelTransition(merge, turn);

        TranslateTransition driveOut = new TranslateTransition(Duration.millis(laneTravelMillis(slotCX, exitX)), car);
        driveOut.setToX(exitX);
        driveOut.setToY(exitLaneCY);
        driveOut.setInterpolator(Interpolator.EASE_BOTH);

        SequentialTransition seq = new SequentialTransition(pullOut, alignToExit, driveOut);
        seq.setOnFinished(e -> {
            activeAnims[idx] = null;
            lotPane.getChildren().remove(car);
            slotParked[idx] = false;
            if (idx < spaceRects.size()) {
                spaceRects.get(idx).setFill(Color.web(C_SPACE_E));
            }
            freeSlots.offer(idx);
            if (visualOccupied > 0) {
                visualOccupied--;
            }
            visualTotalOut++;
            releaseExitLane();
            dispatchPendingVisualEvents();
        });
        activeAnims[idx] = seq;
        seq.play();
    }
    // -----------------------------------------------------------------------------
    // Car shape ? enhanced cartoon top-down view, centred at (0, 0)
    // -----------------------------------------------------------------------------
    private Group makeCar(Color color) {
        // Wheels (drawn first — behind body)
        double wr = 4.5;
        Circle wfl = new Circle(-CAR_W / 2.0 + 1,  -CAR_H / 2.0 + 9,  wr, Color.web("#0F172A"));
        Circle wfr = new Circle( CAR_W / 2.0 - 1,  -CAR_H / 2.0 + 9,  wr, Color.web("#0F172A"));
        Circle wrl = new Circle(-CAR_W / 2.0 + 1,   CAR_H / 2.0 - 9,  wr, Color.web("#0F172A"));
        Circle wrr = new Circle( CAR_W / 2.0 - 1,   CAR_H / 2.0 - 9,  wr, Color.web("#0F172A"));

        // Body
        Rectangle body = new Rectangle(-CAR_W / 2.0, -CAR_H / 2.0, CAR_W, CAR_H);
        body.setFill(color);
        body.setArcWidth(9); body.setArcHeight(9);

        // Side shine
        Rectangle shine = new Rectangle(-CAR_W / 2.0 + 2, -CAR_H / 2.0 + 3, 5, CAR_H - 6);
        shine.setFill(Color.web("#FFFFFF", 0.14));
        shine.setArcWidth(4); shine.setArcHeight(4);

        // Front windshield
        Rectangle ws = new Rectangle(-CAR_W / 2.0 + 4, -CAR_H / 2.0 + 7, CAR_W - 8, 11);
        ws.setFill(Color.web("#BAE6FD", 0.82));
        ws.setArcWidth(3); ws.setArcHeight(3);

        // Rear windshield
        Rectangle rw = new Rectangle(-CAR_W / 2.0 + 4, CAR_H / 2.0 - 16, CAR_W - 8, 9);
        rw.setFill(Color.web("#BAE6FD", 0.42));
        rw.setArcWidth(3); rw.setArcHeight(3);

        // Roof
        Rectangle roof = new Rectangle(-CAR_W / 2.0 + 7, -CAR_H / 2.0 + 20, CAR_W - 14, CAR_H - 38);
        roof.setFill(Color.web("#FFFFFF", 0.11));

        // Headlights
        Rectangle hlL = new Rectangle(-CAR_W / 2.0 + 3, -CAR_H / 2.0 + 2, 5, 3);
        hlL.setFill(Color.web("#FEF9C3", 0.92));
        hlL.setArcWidth(2); hlL.setArcHeight(2);
        Rectangle hlR = new Rectangle(CAR_W / 2.0 - 8, -CAR_H / 2.0 + 2, 5, 3);
        hlR.setFill(Color.web("#FEF9C3", 0.92));
        hlR.setArcWidth(2); hlR.setArcHeight(2);

        return new Group(wfl, wfr, wrl, wrr, body, shine, ws, rw, roof, hlL, hlR);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reset helper — clears visual lot state (unchanged)
    // ─────────────────────────────────────────────────────────────────────────
    private void clearLot() {
        for (int i = 0; i < capacity; i++) {
            if (activeAnims[i] != null) { activeAnims[i].stop(); activeAnims[i] = null; }
        }
        if (lotPane != null) lotPane.getChildren().removeIf(n -> n instanceof Group);
        spaceRects.forEach(r -> r.setFill(Color.web(C_SPACE_E)));
        Arrays.fill(parkedCars, null);
        Arrays.fill(slotParked, false);
        freeSlots.clear(); takenSlots.clear();
        for (int i = 0; i < capacity; i++) freeSlots.offer(i);
        entryLaneQueue.clear();
        exitLaneQueue.clear();
        entryLaneOccupied = false;
        exitLaneOccupied  = false;
        prevTotalParked = 0;
        prevTotalLeft   = 0;
        pendingArrivals = 0;
        pendingDepartures = 0;
        visualOccupied  = 0;
        visualTotalIn   = 0;
        visualTotalOut  = 0;
        simStartMs      = System.currentTimeMillis();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Thread panel refresh (unchanged logic — new styling in threadRow)
    // ─────────────────────────────────────────────────────────────────────────
    private void refreshThreadPanel() {
        threadPanel.getChildren().clear();
        threadPanel.getChildren().add(sectionHeader("THREADS", "Active thread monitor"));

        if (!controller.isRunning()) {
            Label stopped = new Label("Simulation stopped");
            stopped.setFont(Font.font("Segoe UI", 12));
            stopped.setTextFill(Color.web(C_MUTED));
            stopped.setPadding(new Insets(8, 0, 0, 0));
            threadPanel.getChildren().add(stopped);
            return;
        }

        threadPanel.getChildren().add(groupLabel("PRODUCERS"));
        for (ProducerThread p : controller.getProducers())
            threadPanel.getChildren().add(
                threadRow("Producer " + p.getProducerId(), p.getStatus(),
                          p.getCarsProduced() + " arrived"));

        threadPanel.getChildren().add(groupLabel("CONSUMERS"));
        for (ConsumerThread c : controller.getConsumers())
            threadPanel.getChildren().add(
                threadRow("Consumer " + c.getConsumerId(), c.getStatus(),
                          c.getCarsProcessed() + " guided"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rebuild lot pane in-place when capacity slider changes (unchanged logic)
    // ─────────────────────────────────────────────────────────────────────────
    private void rebuildLotPane(int newCap) {
        for (int i = 0; i < activeAnims.length; i++) {
            if (activeAnims[i] != null) { activeAnims[i].stop(); activeAnims[i] = null; }
        }
        if (lotPane != null) lotPane.getChildren().removeIf(n -> n instanceof Group);
        Arrays.fill(parkedCars, null);
        Arrays.fill(slotParked, false);
        freeSlots.clear(); takenSlots.clear();
        entryLaneQueue.clear();
        exitLaneQueue.clear();
        entryLaneOccupied = false;
        exitLaneOccupied  = false;
        visualOccupied = 0;
        pendingArrivals = 0;
        pendingDepartures = 0;
        prevTotalParked = controller.getParkingLot().getTotalParked();
        prevTotalLeft   = controller.getParkingLot().getTotalLeft();

        updateLotMetrics(newCap);

        parkedCars  = new Group[capacity];
        activeAnims = new Animation[capacity];
        slotParked  = new boolean[capacity];
        for (int i = 0; i < capacity; i++) freeSlots.offer(i);

        lotPane.getChildren().clear();
        spaceRects.clear();
        lotPane.setPrefSize(lotW, lotH);
        lotPane.setMaxSize(lotW, lotH);
        lotPane.setMinSize(lotW, lotH);

        drawLotBackground(lotPane, lotW, lotH);
        drawParkingSpaces(lotPane);

        controller.setBufferCapacity(newCap);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Widget helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Outlined button with fill-on-hover effect. */
    private Button mkBtn(String txt, String color) {
        Button b = new Button(txt);
        b.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        String base =
            "-fx-background-color:rgba(255,255,255,0.07);" +
            "-fx-text-fill:" + color + ";" +
            "-fx-background-radius:8;" +
            "-fx-border-color:" + color + ";" +
            "-fx-border-radius:8;" +
            "-fx-border-width:1.5;" +
            "-fx-padding:8 20;";
        String hover =
            "-fx-background-color:" + color + ";" +
            "-fx-text-fill:white;" +
            "-fx-background-radius:8;" +
            "-fx-border-color:" + color + ";" +
            "-fx-border-radius:8;" +
            "-fx-border-width:1.5;" +
            "-fx-padding:8 20;" +
            "-fx-cursor:hand;";
        b.setStyle(base);
        b.setOnMouseEntered(e -> b.setStyle(hover));
        b.setOnMouseExited( e -> b.setStyle(base));
        return b;
    }

    /** Two-line section header: bold title + muted subtitle. */
    private VBox sectionHeader(String title, String subtitle) {
        Label t = new Label(title);
        t.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        t.setTextFill(Color.web(C_TEXT));
        Label s = new Label(subtitle);
        s.setFont(Font.font("Segoe UI", 11));
        s.setTextFill(Color.web(C_MUTED));
        VBox v = new VBox(1, t, s);
        v.setPadding(new Insets(4, 0, 0, 0));
        return v;
    }

    /** Small all-caps group label for thread-panel sections. */
    private Label groupLabel(String t) {
        Label l = new Label(t);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
        l.setTextFill(Color.web(C_MUTED));
        l.setPadding(new Insets(10, 0, 2, 0));
        return l;
    }

    /** Muted key label used inside summary cards. */
    private Label cardKeyLabel(String t) {
        Label l = new Label(t);
        l.setFont(Font.font("Segoe UI", 11));
        l.setTextFill(Color.web(C_MUTED));
        return l;
    }

    /** Large bold value label for primary stat cards. */
    private Label bigValueLabel(String t) {
        Label l = new Label(t);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        l.setTextFill(Color.web(C_TEXT));
        return l;
    }

    /** Medium bold value label for traffic in/out. */
    private Label midValueLabel(String t) {
        Label l = new Label(t);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        l.setTextFill(Color.web(C_TEXT));
        return l;
    }

    /** Muted body label. */
    private Label mutedLabel(String t) {
        Label l = new Label(t);
        l.setFont(Font.font("Segoe UI", 11));
        l.setTextFill(Color.web(C_MUTED));
        return l;
    }

    /** Pass-through — used for in/out traffic rows. */
    private Label trafficNumLabel(Label l) { return l; }

    /** Small filled colour dot. */
    private Circle colorDot(String hex) {
        return new Circle(5, Color.web(hex));
    }

    /** Blue-styled value label for controls. */
    private Label ctrlValueLabel(String t) {
        Label l = new Label(t);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        l.setTextFill(Color.web(C_PRIMARY));
        l.setMinWidth(62);
        return l;
    }

    /** Thin vertical divider for the controls bar. */
    private HBox ctrlDivider() {
        Rectangle r = new Rectangle(1, 38);
        r.setFill(Color.web(C_BORDER));
        HBox h = new HBox(r);
        h.setAlignment(Pos.CENTER);
        h.setPadding(new Insets(0, 4, 0, 4));
        return h;
    }

    /** Creates a styled horizontal slider. */
    private Slider mkSlider(double min, double max, double val) {
        Slider s = new Slider(min, max, val);
        s.setPrefWidth(130);
        s.setBlockIncrement(1);
        s.setShowTickMarks(false);
        s.setStyle(
            "-fx-control-inner-background:#DBEAFE;" +
            "-fx-accent:" + C_PRIMARY + ";"
        );
        return s;
    }

    /** Control group: muted key label above, slider + value label beside it. */
    private VBox ctrlGroup(String key, Slider slider, Label val) {
        Label k = new Label(key);
        k.setFont(Font.font("Segoe UI", 11));
        k.setTextFill(Color.web(C_MUTED));
        HBox row = new HBox(8, slider, val);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox v = new VBox(5, k, row);
        v.setAlignment(Pos.CENTER_LEFT);
        return v;
    }

    /**
     * Thread status row — coloured dot (with pulse for ACTIVE), name, detail.
     * ACTIVE dots pulse subtly to communicate live thread activity.
     */
    private HBox threadRow(String name, ThreadStatus status, String detail) {
        String dotColor = switch (status) {
            case ACTIVE  -> "#22C55E";
            case WAITING -> "#F59E0B";
            case CRASHED -> "#EF4444";
            default      -> C_MUTED;
        };
        String rowBg = switch (status) {
            case ACTIVE  -> "#F0FDF4";
            case WAITING -> "#FFFBEB";
            case CRASHED -> "#FEF2F2";
            default      -> "#F8FAFC";
        };
        String borderColor = switch (status) {
            case ACTIVE  -> "#BBF7D0";
            case WAITING -> "#FDE68A";
            case CRASHED -> "#FECACA";
            default      -> C_BORDER;
        };

        Circle dot = new Circle(5, Color.web(dotColor));
        if (status == ThreadStatus.ACTIVE) {
            ScaleTransition pulse = new ScaleTransition(Duration.millis(900), dot);
            pulse.setFromX(0.75); pulse.setToX(1.30);
            pulse.setFromY(0.75); pulse.setToY(1.30);
            pulse.setAutoReverse(true);
            pulse.setCycleCount(Animation.INDEFINITE);
            pulse.setInterpolator(Interpolator.EASE_BOTH);
            pulse.play();
        }

        Label n = new Label(name);
        n.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        n.setTextFill(Color.web(C_TEXT));

        Label d = new Label(status.name() + "  \u00b7  " + detail);
        d.setFont(Font.font("Segoe UI", 10));
        d.setTextFill(Color.web(C_MUTED));

        VBox info = new VBox(1, n, d);
        HBox row  = new HBox(8, dot, info);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(9, 12, 9, 12));
        row.setStyle(
            "-fx-background-color:" + rowBg + ";" +
            "-fx-background-radius:10;" +
            "-fx-border-color:" + borderColor + ";" +
            "-fx-border-radius:10;" +
            "-fx-border-width:1;"
        );
        return row;
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void stop() {
        if (controller.isRunning()) controller.stop();
        if (updateLoop != null)     updateLoop.stop();
    }
}
