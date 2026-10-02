package com.simcel.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Moteur de simulation de propagation du feu sur une {@link Grid}.
 *
 * <p>
 * L'algorithme repose sur un double buffering : un snapshot des états est pris
 * en début de tick afin que toutes les décisions de propagation soient basées
 * sur l'état <em>avant</em> le tick, et non sur un état partiellement mis à
 * jour.</p>
 *
 * <h2>Déroulement d'un tick</h2>
 * <ol>
 * <li>Snapshot des états courants.</li>
 * <li>Pour chaque cellule {@link CellState#EN_FEU}, calcul stochastique de
 * propagation vers les voisins {@link CellState#SAIN}.</li>
 * <li>Application atomique des nouvelles ignitions.</li>
 * <li>Décrémentation du temps de combustion des cellules en feu ; extinction de
 * celles dont le compteur atteint 0.</li>
 * <li>Notification des {@link SimulationListener}.</li>
 * </ol>
 */
public class FireSimulator {

    /** Nombre maximum d'états conservés pour le retour en arrière. */
    private static final int    MAX_HISTORY     = 100;
    /** Diviseur de l'intensité du vent dans le calcul du facteur directionnel. */
    private static final double MAX_WIND_FACTOR = Environment.MAX_WIND;

    private final Grid grid;
    private final Environment environment;
    private final List<SimulationListener> listeners;
    private final Random random;
    private final Deque<Cell[][]> history = new ArrayDeque<>();
    private int currentTick;

    /**
     * Crée un simulateur pour la grille et l'environnement donnés.
     *
     * @param grid grille de simulation, non {@code null}
     * @param environment conditions environnementales, non {@code null}
     */
    public FireSimulator(Grid grid, Environment environment) {
        this(grid, environment, new Random());
    }

    /**
     * Crée un simulateur dont le tirage aléatoire est fourni par l'appelant,
     * ce qui rend la propagation reproductible (tests).
     *
     * @param grid grille de simulation, non {@code null}
     * @param environment conditions environnementales, non {@code null}
     * @param random générateur utilisé pour les tirages de propagation
     */
    FireSimulator(Grid grid, Environment environment, Random random) {
        this.grid = grid;
        this.environment = environment;
        this.listeners = new ArrayList<>();
        this.random = random;
        this.currentTick = 0;
    }

    /**
     * Avance la simulation d'un pas de temps.
     *
     * <p>
     * Toutes les modifications de la grille sont appliquées de manière atomique
     * après la phase de calcul, garantissant l'indépendance des décisions par
     * rapport à l'ordre de parcours des cellules.</p>
     */
    public void tick() {
        pushHistory();
        CellState[][] snapshot = takeSnapshot();

        Set<Cell> toIgnite = collectIgnitions(snapshot);

        for (Cell cell : toIgnite) {
            cell.ignite();
        }

        applyExtinctions(snapshot);

        currentTick++;
        notifyListeners(currentTick);
    }

    /**
     * Calcule la probabilité qu'une cellule cible s'enflamme à partir d'une
     * cellule source en feu, en intégrant le vent et l'humidité.
     *
     * <p>
     * La formule appliquée est :</p>
     * <pre>
     *   windFactor     = 1.0 + (windStrength / 5.0) × cos(θ)
     *   humidityFactor = 1.0 − humidity / 100.0
     *   P = clamp(baseInflammability × windFactor × humidityFactor, 0.0, 1.0)
     * </pre>
     * <p>
     * où θ est l'angle entre le vecteur vent et le vecteur source→cible. Si
     * l'intensité du vent est nulle, {@code windFactor} vaut {@code 1.0}.</p>
     *
     * @param srcX colonne de la cellule source
     * @param srcY ligne de la cellule source
     * @param tgtX colonne de la cellule cible
     * @param tgtY ligne de la cellule cible
     * @param tgt  cellule cible (candidate à l'inflammation)
     * @return probabilité dans {@code [0.0, 1.0]}
     */
    double computeInflammationProbability(int srcX, int srcY, int tgtX, int tgtY, Cell tgt) {
        double base = tgt.getType().getInflammability();
        double windFactor = applyWindFactor(tgtX - srcX, tgtY - srcY);
        double p = applyHumidityFactor(base * windFactor);
        return Math.max(0.0, Math.min(1.0, p));
    }

    /**
     * Retourne la grille associée à ce simulateur.
     *
     * @return grille, jamais {@code null}
     */
    public Grid getGrid() {
        return grid;
    }

    /**
     * Retourne l'environnement météorologique associé à ce simulateur.
     *
     * @return environnement, jamais {@code null}
     */
    public Environment getEnvironment() {
        return environment;
    }

    /**
     * Enregistre un observateur qui sera notifié à chaque tick.
     *
     * @param listener observateur à ajouter, non {@code null}
     */
    public void addListener(SimulationListener listener) {
        listeners.add(listener);
    }

    /**
     * Recule la simulation d'un tick en restaurant l'état sauvegardé juste
     * avant le dernier appel à {@link #tick()}.
     *
     * <p>Sans effet si l'historique est vide. Notifie les listeners après
     * restauration pour que la vue se mette à jour.</p>
     *
     * @return {@code true} si un état précédent a été restauré
     */
    public boolean stepBack() {
        if (history.isEmpty()) return false;
        Cell[][] prev = history.pop();
        int w = grid.getWidth();
        int h = grid.getHeight();
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                grid.setCell(x, y, prev[x][y]);
        if (currentTick > 0) currentTick--;
        notifyListeners(currentTick);
        return true;
    }

    /**
     * Vide l'historique des états sauvegardés.
     * À appeler lors d'un reset pour éviter de revenir à des états pré-reset.
     */
    public void clearHistory() {
        history.clear();
    }

    /**
     * Creates a snapshot of the current simulation state.
     *
     * @return immutable snapshot capturing grid, environment and tick
     */
    public SimulationSnapshot createSnapshot() {
        return new SimulationSnapshot(
                grid.getWidth(), grid.getHeight(),
                grid.copyCells(), grid.copyInitialCells(),
                environment, currentTick);
    }

    /**
     * Restores the simulation to a previously saved snapshot.
     *
     * <p>Clears the step-back history and notifies all listeners.</p>
     *
     * @param snapshot snapshot to restore, must match current grid dimensions
     * @throws IllegalArgumentException if grid dimensions do not match
     */
    public void applySnapshot(SimulationSnapshot snapshot) {
        if (snapshot.getWidth() != grid.getWidth() || snapshot.getHeight() != grid.getHeight()) {
            throw new IllegalArgumentException("Snapshot grid dimensions do not match current grid");
        }
        grid.restoreState(snapshot.getCells(), snapshot.getInitialCells());
        environment.setDirection(snapshot.getWindDirection());
        environment.setWindStrength(snapshot.getWindStrength());
        environment.setHumidity(snapshot.getHumidity());
        currentTick = snapshot.getTick();
        history.clear();
        notifyListeners(currentTick);
    }

    // -------------------------------------------------------------------------
    // Méthodes privées
    // -------------------------------------------------------------------------

    /**
     * Sauvegarde l'état courant de la grille dans l'historique.
     * Le tableau interne utilise la convention {@code snap[x][y]} (ordre inversé
     * par rapport au stockage interne de {@link Grid}, mais cohérent avec les
     * accesseurs publics {@code getCell(x, y)}).
     * Si la capacité maximale {@link #MAX_HISTORY} est atteinte, l'état le plus
     * ancien est supprimé.
     */
    private void pushHistory() {
        int w = grid.getWidth();
        int h = grid.getHeight();
        Cell[][] snap = new Cell[w][h];
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                snap[x][y] = grid.getCell(x, y).copy();
        history.push(snap);
        if (history.size() > MAX_HISTORY) history.pollLast();
    }

    /**
     * Capture les états courants des cellules dans un tableau indépendant,
     * utilisé comme référence immuable pendant toute la durée du tick.
     * Convention : {@code snapshot[x][y]}, cohérente avec {@link #pushHistory()}.
     *
     * @return tableau {@code CellState[width][height]} des états avant le tick
     */
    private CellState[][] takeSnapshot() {
        int w = grid.getWidth();
        int h = grid.getHeight();
        CellState[][] snapshot = new CellState[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                snapshot[x][y] = grid.getCell(x, y).getState();
            }
        }
        return snapshot;
    }

    /**
     * Parcourt les cellules en feu du snapshot et collecte les voisins à
     * enflammer. Un {@link LinkedHashSet} garantit qu'une même cellule ne peut
     * être ajoutée qu'une seule fois, même si plusieurs voisins en feu la
     * ciblent simultanément.
     */
    private Set<Cell> collectIgnitions(CellState[][] snapshot) {
        Set<Cell> toIgnite = new LinkedHashSet<>();
        int w = grid.getWidth();
        int h = grid.getHeight();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (snapshot[x][y] == CellState.EN_FEU) {
                    for (int ndy = -1; ndy <= 1; ndy++) {
                        for (int ndx = -1; ndx <= 1; ndx++) {
                            if (ndx == 0 && ndy == 0) {
                                continue;
                            }
                            int nx = x + ndx;
                            int ny = y + ndy;
                            if (grid.isInBounds(nx, ny)) {
                                Cell neighbor = grid.getCell(nx, ny);
                                if (neighbor.getState() == CellState.SAIN) {
                                    double p = computeInflammationProbability(x, y, nx, ny, neighbor);
                                    if (random.nextDouble() < p) {
                                        toIgnite.add(neighbor);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return toIgnite;
    }

    /**
     * Décrémente les compteurs de combustion et éteint les cellules épuisées.
     */
    private void applyExtinctions(CellState[][] snapshot) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (snapshot[x][y] == CellState.EN_FEU) {
                    Cell cell = grid.getCell(x, y);
                    cell.decrementBurnTime();
                    if (cell.getRemainingBurnTime() == 0) {
                        cell.burnOut();
                    }
                }
            }
        }
    }

    /**
     * Envoie la notification de tick à tous les observateurs enregistrés.
     */
    private void notifyListeners(int tick) {
        for (SimulationListener listener : listeners) {
            listener.onTick(tick, grid);
        }
    }

    /**
     * Applique le facteur d'humidité à une probabilité intermédiaire.
     *
     * <pre>
     *   P_finale = p × (1.0 − humidity / 100.0)
     * </pre>
     *
     * @param p probabilité avant correction d'humidité
     * @return probabilité réduite par l'humidité ambiante
     */
    private double applyHumidityFactor(double p) {
        return p * (1.0 - environment.getHumidity() / 100.0);
    }

    /**
     * Calcule le facteur multiplicatif du vent pour une propagation dans la
     * direction {@code (pdx, pdy)}.
     *
     * <p>
     * Retourne {@code 1.0} si l'intensité du vent est nulle. Sinon, utilise le
     * cosinus de l'angle entre le vecteur vent et le vecteur de propagation
     * ({@code cosθ = dot(vent, propagation) / (|vent| × |propagation|)}) :</p>
     * <pre>
     *   windFactor = 1.0 + (windStrength / 5.0) × cosθ
     * </pre>
     *
     * @param pdx composante X du vecteur source→cible
     * @param pdy composante Y du vecteur source→cible
     * @return facteur vent (peut être &lt; 1 ou &gt; 1 ; le clamp est à la charge de l'appelant)
     */
    private double applyWindFactor(int pdx, int pdy) {
        int windStrength = environment.getWindStrength();
        if (windStrength == 0) {
            return 1.0;
        }
        int wdx = environment.getDirection().getDx();
        int wdy = environment.getDirection().getDy();
        double dot = wdx * pdx + wdy * pdy;
        double windMag = Math.sqrt(wdx * wdx + wdy * wdy);
        double propMag = Math.sqrt(pdx * pdx + pdy * pdy);
        double cosTheta = dot / (windMag * propMag);
        return 1.0 + (windStrength / MAX_WIND_FACTOR) * cosTheta;
    }
}
