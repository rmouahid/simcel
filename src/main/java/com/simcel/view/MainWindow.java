package com.simcel.view;

import com.simcel.controller.SimulationController;
import com.simcel.model.Cell;
import com.simcel.model.CellState;
import com.simcel.model.CellType;
import com.simcel.model.Environment;
import com.simcel.model.FireSimulator;
import com.simcel.model.Grid;
import com.simcel.model.SimulationListener;
import com.simcel.model.SimulationState;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Fenêtre principale de l'application JavaFX.
 *
 * <p>Layout global :</p>
 * <ul>
 *   <li>TOP : {@link HeaderBar} + {@link LegendPanel}</li>
 *   <li>CENTER : zone de grille (zoom/pan) + barre de lecture + graphique</li>
 *   <li>RIGHT : {@link WindIndicator}, mode édition, {@link StatisticsPanel},
 *       contrôles de taille de grille</li>
 * </ul>
 */
public class MainWindow {

    private static final double CELL_SIZE    = 14.0;
    private static final double RIGHT_WIDTH  = 310.0;
    private static final double CHART_HEIGHT = 200.0;
    private static final double STATS_WIDTH  = 210.0;
    private static final double STAGE_WIDTH  = 1050.0;
    private static final double STAGE_HEIGHT = 780.0;

    private static final double ZOOM_FACTOR = 1.15;
    private static final double MIN_SCALE   = 0.2;
    private static final double MAX_SCALE   = 8.0;

    private static final double DENSITY_FORET   = 0.60;
    private static final double DENSITY_PRAIRIE = 0.20;
    private static final double DENSITY_BROUSS  = 0.10;
    private static final double DENSITY_HUMIDE  = 0.05;
    private static final double DENSITY_URBAINE = 0.05;

    // -------------------------------------------------------------------------
    // Outils d'édition
    // -------------------------------------------------------------------------

    private enum EditTool {
        FORET        ("Forêt",    CellType.FORET.getHealthyColor(),        "white"),
        PRAIRIE      ("Prairie",  CellType.PRAIRIE.getHealthyColor(),      "white"),
        BROUSSAILLES ("Brouss.",  CellType.BROUSSAILLES.getHealthyColor(), "white"),
        ZONE_HUMIDE  ("Humide",   CellType.ZONE_HUMIDE.getHealthyColor(),  "white"),
        ZONE_URBAINE ("Urbain",   CellType.ZONE_URBAINE.getHealthyColor(), "white"),
        VIDE         ("Effacer",  CellState.VIDE.getColor(),               "black"),
        EAU          ("Eau",      CellState.EAU.getColor(),                "white"),
        ROCHER       ("Rocher",   CellState.ROCHER.getColor(),             "black"),
        FOYER        ("Foyer 🔥", CellState.EN_FEU.getColor(),             "white");

        final String label, bgColor, fgColor;

        EditTool(String label, String bgColor, String fgColor) {
            this.label   = label;
            this.bgColor = bgColor;
            this.fgColor = fgColor;
        }

        Cell createCell() {
            return switch (this) {
                case FORET        -> new Cell(CellType.FORET);
                case PRAIRIE      -> new Cell(CellType.PRAIRIE);
                case BROUSSAILLES -> new Cell(CellType.BROUSSAILLES);
                case ZONE_HUMIDE  -> new Cell(CellType.ZONE_HUMIDE);
                case ZONE_URBAINE -> new Cell(CellType.ZONE_URBAINE);
                case VIDE   -> { Cell c = new Cell(CellType.PRAIRIE); c.setState(CellState.VIDE);   yield c; }
                case EAU    -> { Cell c = new Cell(CellType.PRAIRIE); c.setState(CellState.EAU);    yield c; }
                case ROCHER -> { Cell c = new Cell(CellType.PRAIRIE); c.setState(CellState.ROCHER); yield c; }
                case FOYER  -> null;
            };
        }
    }

    private boolean      editMode     = false;
    private EditTool     selectedTool = EditTool.FOYER;
    private VBox         editToolsBox;
    private ToggleButton btnEditToggle;
    private Pane         gridPane;

    // -------------------------------------------------------------------------
    // Composants principaux
    // -------------------------------------------------------------------------

    private FireSimulator        simulator;
    private SimulationController controller;
    private Grid                 grid;

    private GridView        gridView;
    private StatisticsPanel statisticsPanel;
    private ChartView       chartView;
    private LegendPanel     legendPanel;
    private WindIndicator   windIndicator;
    private HeaderBar       headerBar;
    private BorderPane      root;
    private VBox            centerPanel;
    private FileChooser     fileChooser;

    private final Button btnStart    = new Button("▶");
    private final Button btnPause    = new Button("⏸");
    private final Button btnStep     = new Button("⏭");
    private final Button btnStepBack = new Button("⏮");
    private final Button btnReset    = new Button("↺");
    private final Button btnSave     = new Button("💾  Sauvegarder");
    private final Button btnLoad     = new Button("📂  Charger");

    /**
     * Crée la fenêtre principale.
     *
     * @param simulator  moteur de simulation, non {@code null}
     * @param controller contrôleur de la boucle, non {@code null}
     */
    public MainWindow(FireSimulator simulator, SimulationController controller) {
        this.simulator  = simulator;
        this.controller = controller;
        this.grid       = simulator.getGrid();
    }

    /**
     * Construit la scène, enregistre les listeners et affiche la fenêtre.
     *
     * @param stage stage JavaFX fourni par {@code Application.start()}
     */
    public void show(Stage stage) {
        Environment env = simulator.getEnvironment();
        gridView        = new GridView(grid, CELL_SIZE);
        statisticsPanel = new StatisticsPanel();
        chartView       = new ChartView(700, CHART_HEIGHT);
        legendPanel     = new LegendPanel();
        windIndicator   = new WindIndicator(env);
        headerBar       = new HeaderBar(btnSave, btnLoad, grid.getWidth(), grid.getHeight());

        fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Simulation SimCel (*.simcel)", "*.simcel"));

        simulator.addListener(gridView);
        simulator.addListener(statisticsPanel);
        simulator.addListener(chartView);
        simulator.addListener(headerBar);
        simulator.addListener(stoppingListener());

        setupButtonActions();
        setIdleState();

        BorderPane contentPane = new BorderPane();
        centerPanel = buildCenterPanel();
        contentPane.setCenter(centerPanel);
        contentPane.setRight(buildScrollableRightPanel());

        root = new BorderPane();
        root.setTop(new VBox(headerBar, legendPanel));
        root.setCenter(contentPane);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(
                getClass().getResource("/com/simcel/style.css").toExternalForm());

        stage.setTitle("SimCel — Simulation de Propagation de Feu");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setWidth(STAGE_WIDTH);
        stage.setHeight(STAGE_HEIGHT);
        stage.setOnCloseRequest(e -> controller.stop());
        stage.show();
    }

    // -------------------------------------------------------------------------
    // Construction du layout
    // -------------------------------------------------------------------------

    /** VBox centrale : zone de grille + barre de lecture + graphique avec statistiques. */
    private VBox buildCenterPanel() {
        Pane gridHolder = buildGridHolder();

        Pane chartWrapper = new Pane(chartView);
        chartWrapper.setPrefHeight(CHART_HEIGHT);
        chartWrapper.setMinHeight(CHART_HEIGHT);
        chartWrapper.setMaxHeight(CHART_HEIGHT);
        chartWrapper.getStyleClass().add("chart-wrapper");
        HBox.setHgrow(chartWrapper, Priority.ALWAYS);
        chartWrapper.widthProperty().addListener((obs, o, n) -> {
            if (n.doubleValue() > 10) chartView.resize(n.doubleValue(), CHART_HEIGHT);
        });

        statisticsPanel.setPrefWidth(STATS_WIDTH);
        statisticsPanel.setMinWidth(STATS_WIDTH);
        statisticsPanel.setMaxWidth(STATS_WIDTH);
        statisticsPanel.getStyleClass().add("stats-side");

        HBox bottomSection = new HBox(chartWrapper, statisticsPanel);
        bottomSection.setMinHeight(CHART_HEIGHT);
        bottomSection.setPrefHeight(CHART_HEIGHT);
        bottomSection.setMaxHeight(CHART_HEIGHT);

        return new VBox(gridHolder, buildPlaybackBar(), bottomSection);
    }

    /** Enveloppe la grille dans un Pane qui occupe tout l'espace vertical disponible. */
    private Pane buildGridHolder() {
        Pane holder = buildGridPane();
        VBox.setVgrow(holder, Priority.ALWAYS);
        return holder;
    }

    /** Barre de lecture : Reset · Step back · Play · Pause · Step · vitesse. */
    private HBox buildPlaybackBar() {
        for (Button b : new Button[]{btnReset, btnStepBack, btnStart, btnPause, btnStep}) {
            b.getStyleClass().add("playback-btn");
        }

        Label lblSpeed = new Label("VITESSE");
        lblSpeed.setStyle("-fx-text-fill: #888899; -fx-font-size: 10px;");

        Slider sliderSpeed = new Slider(50, 1000, controller.getTickDelay());
        sliderSpeed.setPrefWidth(160);
        sliderSpeed.valueProperty().addListener(
                (obs, old, val) -> controller.setTickDelay(val.intValue()));

        Label lblSpeedVal = new Label(controller.getTickDelay() + " ms");
        lblSpeedVal.setStyle(
                "-fx-text-fill: #cccccc; -fx-font-size: 11px; -fx-min-width: 48px;");
        sliderSpeed.valueProperty().addListener((obs, old, val) ->
                lblSpeedVal.setText(val.intValue() + " ms"));

        HBox bar = new HBox(8,
                btnReset, btnStepBack, btnStart, btnPause, btnStep,
                new Separator(Orientation.VERTICAL),
                lblSpeed, sliderSpeed, lblSpeedVal);
        bar.getStyleClass().add("playback-bar");
        bar.setPadding(new Insets(8, 16, 8, 16));
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    /** Panneau droit scrollable : météo · édition · statistiques · taille grille. */
    private ScrollPane buildScrollableRightPanel() {
        VBox panel = new VBox(4,
                windIndicator,
                new Separator(),
                buildEditSection(),
                new Separator(),
                buildGridSection());
        panel.getStyleClass().add("right-panel");
        panel.setPadding(new Insets(0, 0, 8, 0));

        ScrollPane scroll = new ScrollPane(panel);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPrefWidth(RIGHT_WIDTH);
        scroll.setMinWidth(RIGHT_WIDTH);
        scroll.getStyleClass().add("right-scroll");
        return scroll;
    }

    // -------------------------------------------------------------------------
    // Zone grille : zoom, déplacement et édition
    // -------------------------------------------------------------------------

    private Pane buildGridPane() {
        gridPane = new Pane(gridView);
        gridPane.getStyleClass().add("grid-pane");

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(gridPane.widthProperty());
        clip.heightProperty().bind(gridPane.heightProperty());
        gridPane.setClip(clip);

        gridPane.setCursor(Cursor.OPEN_HAND);
        attachZoom(gridPane);
        attachPan(gridPane);
        return gridPane;
    }

    private void attachZoom(Pane pane) {
        pane.setOnScroll(e -> {
            double delta = e.getDeltaY() != 0 ? e.getDeltaY() : -e.getTextDeltaY();
            if (delta == 0) { e.consume(); return; }

            double oldScale = gridView.getScaleX();
            double factor   = delta > 0 ? ZOOM_FACTOR : 1.0 / ZOOM_FACTOR;
            double newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, oldScale * factor));
            double ratio    = newScale / oldScale;

            Bounds b      = gridView.getBoundsInParent();
            double pivotX = e.getX() - (b.getMinX() + b.getWidth()  / 2.0);
            double pivotY = e.getY() - (b.getMinY() + b.getHeight() / 2.0);

            gridView.setScaleX(newScale);
            gridView.setScaleY(newScale);
            gridView.setTranslateX(gridView.getTranslateX() + pivotX * (1 - ratio));
            gridView.setTranslateY(gridView.getTranslateY() + pivotY * (1 - ratio));
            e.consume();
        });
    }

    private void attachPan(Pane pane) {
        final double[] dragOrigin      = {0, 0};
        final double[] translateOrigin = {0, 0};

        pane.setOnMousePressed(e -> {
            if (editMode) {
                paintCellAt(e.getSceneX(), e.getSceneY());
            } else {
                dragOrigin[0]      = e.getX();
                dragOrigin[1]      = e.getY();
                translateOrigin[0] = gridView.getTranslateX();
                translateOrigin[1] = gridView.getTranslateY();
                pane.setCursor(Cursor.CLOSED_HAND);
            }
        });

        pane.setOnMouseDragged(e -> {
            if (editMode) {
                paintCellAt(e.getSceneX(), e.getSceneY());
            } else {
                gridView.setTranslateX(translateOrigin[0] + e.getX() - dragOrigin[0]);
                gridView.setTranslateY(translateOrigin[1] + e.getY() - dragOrigin[1]);
            }
        });

        pane.setOnMouseReleased(e -> {
            if (editMode) {
                grid.saveInitialState();
            } else {
                pane.setCursor(Cursor.OPEN_HAND);
            }
        });

        pane.setOnMouseClicked(e -> {
            if (!editMode && e.getClickCount() == 2) resetView();
        });
    }

    private void paintCellAt(double sceneX, double sceneY) {
        Point2D local  = gridView.sceneToLocal(sceneX, sceneY);
        int[]   coords = gridView.getCellAt(local.getX(), local.getY());
        if (coords == null) return;
        int x = coords[0], y = coords[1];
        if (selectedTool == EditTool.FOYER) {
            Cell c = new Cell(CellType.PRAIRIE);
            grid.setCell(x, y, c);
            grid.setFire(x, y);
        } else {
            grid.setCell(x, y, selectedTool.createCell());
        }
        gridView.refresh(grid);
    }

    private void toggleEditMode(boolean on) {
        editMode = on;
        editToolsBox.setVisible(on);
        editToolsBox.setManaged(on);
        gridPane.setCursor(on ? Cursor.CROSSHAIR : Cursor.OPEN_HAND);
    }

    /** Remet le zoom et la position de la grille à leurs valeurs par défaut. */
    public void resetView() {
        gridView.setScaleX(1.0);
        gridView.setScaleY(1.0);
        gridView.setTranslateX(0.0);
        gridView.setTranslateY(0.0);
    }

    // -------------------------------------------------------------------------
    // Actions des boutons
    // -------------------------------------------------------------------------

    private void setupButtonActions() {
        btnStart.setOnAction(e -> {
            controller.start();
            setRunningState();
        });

        btnPause.setOnAction(e -> {
            controller.pause();
            setIdleState();
        });

        btnStep.setOnAction(e -> controller.step());

        btnStepBack.setOnAction(e -> {
            controller.stepBack();
            chartView.removeLastPoint();
        });

        btnReset.setOnAction(e -> {
            controller.reset();
            gridView.refresh(grid);
            resetView();
            statisticsPanel.reset();
            chartView.clear();
            headerBar.resetTick();
            setIdleState();
        });

        btnSave.setOnAction(e -> {
            fileChooser.setTitle("Sauvegarder la simulation");
            java.io.File file = fileChooser.showSaveDialog(null);
            if (file == null) return;
            if (!file.getName().endsWith(".simcel"))
                file = new java.io.File(file.getAbsolutePath() + ".simcel");
            try {
                controller.saveToFile(file);
            } catch (java.io.IOException ex) {
                showError("Erreur lors de la sauvegarde : " + ex.getMessage());
            }
        });

        btnLoad.setOnAction(e -> {
            fileChooser.setTitle("Charger une simulation");
            java.io.File file = fileChooser.showOpenDialog(null);
            if (file == null) return;
            try {
                controller.loadFromFile(file);
                gridView.refresh(grid);
                statisticsPanel.reset();
                chartView.clear();
                headerBar.resetTick();
                windIndicator.refresh(simulator.getEnvironment());
                setIdleState();
            } catch (java.io.IOException | ClassNotFoundException ex) {
                showError("Erreur lors du chargement : " + ex.getMessage());
            } catch (IllegalArgumentException ex) {
                showError("Fichier incompatible : " + ex.getMessage());
            }
        });
    }

    // -------------------------------------------------------------------------
    // Section « Taille de la grille » (panneau droit)
    // -------------------------------------------------------------------------

    private VBox buildGridSection() {
        Label titleGrid = new Label("Taille de la grille");
        titleGrid.getStyleClass().add("label-title");

        // --- Largeur ---
        Label lblWidth = new Label("Largeur :");
        TextField tfWidth = new TextField(String.valueOf(grid.getWidth()));
        tfWidth.setPrefWidth(58);
        tfWidth.setMaxWidth(58);
        tfWidth.getStyleClass().add("dim-field");

        Slider sliderWidth = new Slider(10, 300, grid.getWidth());
        sliderWidth.setShowTickLabels(true);
        sliderWidth.setShowTickMarks(true);
        sliderWidth.setMajorTickUnit(100);
        sliderWidth.setMinorTickCount(9);

        sliderWidth.valueProperty().addListener((obs, old, val) ->
                tfWidth.setText(String.valueOf(val.intValue())));
        tfWidth.setOnAction(e ->
                applyDimField(tfWidth, sliderWidth, 10, 300));
        tfWidth.focusedProperty().addListener((obs, was, now) -> {
            if (!now) applyDimField(tfWidth, sliderWidth, 10, 300);
        });

        HBox widthRow = new HBox(8, lblWidth, tfWidth);
        widthRow.setAlignment(Pos.CENTER_LEFT);

        // --- Hauteur ---
        Label lblHeight = new Label("Hauteur :");
        TextField tfHeight = new TextField(String.valueOf(grid.getHeight()));
        tfHeight.setPrefWidth(58);
        tfHeight.setMaxWidth(58);
        tfHeight.getStyleClass().add("dim-field");

        Slider sliderHeight = new Slider(10, 200, grid.getHeight());
        sliderHeight.setShowTickLabels(true);
        sliderHeight.setShowTickMarks(true);
        sliderHeight.setMajorTickUnit(50);
        sliderHeight.setMinorTickCount(9);

        sliderHeight.valueProperty().addListener((obs, old, val) ->
                tfHeight.setText(String.valueOf(val.intValue())));
        tfHeight.setOnAction(e ->
                applyDimField(tfHeight, sliderHeight, 10, 200));
        tfHeight.focusedProperty().addListener((obs, was, now) -> {
            if (!now) applyDimField(tfHeight, sliderHeight, 10, 200);
        });

        HBox heightRow = new HBox(8, lblHeight, tfHeight);
        heightRow.setAlignment(Pos.CENTER_LEFT);

        Button btnNew = new Button("↺  Nouvelle grille");
        btnNew.setMaxWidth(Double.MAX_VALUE);
        btnNew.setOnAction(e ->
                reinitialize((int) sliderWidth.getValue(), (int) sliderHeight.getValue()));

        VBox section = new VBox(4, titleGrid,
                widthRow, sliderWidth, heightRow, sliderHeight, btnNew);
        section.setPadding(new Insets(6, 10, 8, 10));
        return section;
    }

    /**
     * Applique la valeur saisie dans un TextField à son Slider associé.
     * Si la valeur est hors plage elle est clampée ; si elle est invalide
     * le TextField est remis à la valeur courante du Slider.
     */
    private static void applyDimField(TextField tf, Slider slider, int min, int max) {
        try {
            int val = Integer.parseInt(tf.getText().trim());
            val = Math.max(min, Math.min(max, val));
            slider.setValue(val);
            tf.setText(String.valueOf(val));
        } catch (NumberFormatException ex) {
            tf.setText(String.valueOf((int) slider.getValue()));
        }
    }

    // -------------------------------------------------------------------------
    // Section « Edition du terrain » (panneau droit)
    // -------------------------------------------------------------------------

    private VBox buildEditSection() {
        btnEditToggle = new ToggleButton("✏  Mode édition");
        btnEditToggle.setMaxWidth(Double.MAX_VALUE);
        btnEditToggle.getStyleClass().add("edit-toggle");
        btnEditToggle.setOnAction(e -> toggleEditMode(btnEditToggle.isSelected()));

        ToggleGroup toolGroup = new ToggleGroup();
        TilePane toolTile = new TilePane(4, 4);
        toolTile.setPrefColumns(3);

        for (EditTool tool : EditTool.values()) {
            String base     = "-fx-background-color:" + tool.bgColor
                            + "; -fx-text-fill:" + tool.fgColor + ";";
            String selected = base + "-fx-border-color:white; -fx-border-width:2;"
                            + "-fx-border-radius:4;";

            ToggleButton btn = new ToggleButton(tool.label);
            btn.setToggleGroup(toolGroup);
            btn.getStyleClass().add("tool-btn");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setStyle(tool == EditTool.FOYER ? selected : base);
            btn.setOnAction(e -> selectedTool = tool);
            btn.selectedProperty().addListener((obs, old, sel) ->
                    btn.setStyle(sel ? selected : base));
            if (tool == EditTool.FOYER) btn.setSelected(true);
            toolTile.getChildren().add(btn);
        }

        Label hint = new Label("Cliquer ou glisser pour peindre");
        hint.getStyleClass().add("label-hint");

        editToolsBox = new VBox(4, toolTile, hint);
        editToolsBox.setPadding(new Insets(4, 0, 0, 0));
        editToolsBox.setVisible(false);
        editToolsBox.setManaged(false);

        Label titleEdit = new Label("Edition du terrain");
        titleEdit.getStyleClass().add("label-title");

        VBox section = new VBox(4, titleEdit, btnEditToggle, editToolsBox);
        section.setPadding(new Insets(4, 10, 4, 10));
        return section;
    }

    // -------------------------------------------------------------------------
    // Listener d'arrêt automatique
    // -------------------------------------------------------------------------

    private SimulationListener stoppingListener() {
        return (tick, g) -> {
            if (!hasActiveFire(g)) {
                Platform.runLater(() -> {
                    if (g != grid) return;
                    synchronized (simulator) {
                        if (controller.getState() != SimulationState.RUNNING || hasActiveFire(g)) return;
                        controller.stop();
                    }
                    setIdleState();
                });
            }
        };
    }

    private boolean hasActiveFire(Grid g) {
        for (int y = 0; y < g.getHeight(); y++)
            for (int x = 0; x < g.getWidth(); x++)
                if (g.getCell(x, y).getState() == CellState.EN_FEU)
                    return true;
        return false;
    }

    // -------------------------------------------------------------------------
    // Gestion des états des boutons
    // -------------------------------------------------------------------------

    private void setIdleState() {
        btnStart.setDisable(false);
        btnPause.setDisable(true);
        btnStep.setDisable(false);
        btnStepBack.setDisable(false);
        if (headerBar    != null) headerBar.setRunning(false);
        if (btnEditToggle != null) btnEditToggle.setDisable(false);
    }

    private void setRunningState() {
        btnStart.setDisable(true);
        btnPause.setDisable(false);
        btnStep.setDisable(true);
        btnStepBack.setDisable(true);
        if (headerBar != null) headerBar.setRunning(true);
        if (btnEditToggle != null) {
            if (editMode) {
                btnEditToggle.setSelected(false);
                toggleEditMode(false);
            }
            btnEditToggle.setDisable(true);
        }
    }

    private void showError(String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // -------------------------------------------------------------------------
    // Réinitialisation de la simulation avec une nouvelle grille
    // -------------------------------------------------------------------------

    private void reinitialize(int width, int height) {
        controller.stop();
        if (editMode) {
            btnEditToggle.setSelected(false);
            toggleEditMode(false);
        }

        Grid newGrid = new Grid(width, height);
        newGrid.initRandom(DENSITY_FORET, DENSITY_PRAIRIE, DENSITY_BROUSS,
                           DENSITY_HUMIDE, DENSITY_URBAINE);
        newGrid.setFire(width / 2, height / 2);
        newGrid.saveInitialState();

        Environment env   = simulator.getEnvironment();
        int         delay = controller.getTickDelay();

        simulator  = new FireSimulator(newGrid, env);
        controller = new SimulationController(simulator, delay);
        grid       = newGrid;

        gridView = new GridView(grid, CELL_SIZE);
        simulator.addListener(gridView);
        simulator.addListener(statisticsPanel);
        simulator.addListener(chartView);
        simulator.addListener(stoppingListener());
        simulator.addListener(headerBar);

        // Remplace le gridHolder (index 0) dans le panneau central
        centerPanel.getChildren().set(0, buildGridHolder());
        statisticsPanel.reset();
        chartView.clear();
        headerBar.resetTick();
        headerBar.setGridSize(width, height);
        setIdleState();
    }
}
