package com.simcel.controller;

import java.io.File;
import java.io.IOException;

import com.simcel.model.FireSimulator;
import com.simcel.model.SimulationIO;
import com.simcel.model.SimulationSnapshot;
import com.simcel.model.SimulationState;

/**
 * Contrôleur de la boucle de simulation.
 *
 * <p>Orchestre le cycle de vie du {@link FireSimulator} en gérant un thread
 * qui appelle {@code tick()} à intervalle régulier.</p>
 *
 * <pre>
 *   IDLE ──start()──► RUNNING ──pause()──► PAUSED
 *    ▲                   │                     │
 *    └────stop()─────────┘◄────────────────────┘
 * </pre>
 */
public class SimulationController {

    private final FireSimulator simulator;
    private SimulationState state;
    private int tickDelay;
    private Thread simulationThread;

    /**
     * Crée un contrôleur pour le simulateur donné.
     *
     * @param simulator simulateur à piloter, non {@code null}
     * @param tickDelay intervalle entre deux ticks en millisecondes (&gt; 0)
     */
    public SimulationController(FireSimulator simulator, int tickDelay) {
        this.simulator = simulator;
        this.state     = SimulationState.IDLE;
        this.tickDelay = tickDelay;
    }

    /**
     * Lance la boucle de simulation dans un thread séparé. Sans effet si
     * déjà {@link SimulationState#RUNNING}.
     */
    public void start() {
        if (state == SimulationState.RUNNING) return;
        state = SimulationState.RUNNING;
        startThread();
    }

    /**
     * Suspend la boucle sans modifier la grille. Sans effet si la simulation
     * n'est pas {@link SimulationState#RUNNING}.
     */
    public void pause() {
        if (state != SimulationState.RUNNING) return;
        state = SimulationState.PAUSED;
        stopThread();
    }

    /**
     * Exécute exactement un tick manuellement. Sans effet si la simulation
     * est {@link SimulationState#RUNNING}.
     */
    public void step() {
        if (state == SimulationState.RUNNING) return;
        simulator.tick();
    }

    /**
     * Arrête la boucle et repasse en {@link SimulationState#IDLE}. La grille
     * n'est pas modifiée ; utiliser {@link #reset()} pour revenir à l'état
     * initial.
     */
    public void stop() {
        stopThread();
        state = SimulationState.IDLE;
    }

    /**
     * Arrête la boucle, remet la grille dans son état initial et repasse en
     * {@link SimulationState#IDLE}.
     */
    public void reset() {
        stop();
        simulator.getGrid().reset();
        simulator.clearHistory();
    }

    /**
     * Recule d'un tick. Sans effet si la simulation est
     * {@link SimulationState#RUNNING} ou si l'historique est vide.
     *
     * @return {@code true} si un état précédent a été restauré
     */
    public boolean stepBack() {
        if (state == SimulationState.RUNNING) return false;
        return simulator.stepBack();
    }

    /**
     * Modifie l'intervalle entre deux ticks. Redémarre le thread si la
     * simulation est en cours.
     *
     * @param ms nouvel intervalle en millisecondes (&gt; 0)
     */
    public void setTickDelay(int ms) {
        tickDelay = ms;
        if (state == SimulationState.RUNNING) {
            stopThread();
            startThread();
        }
    }

    /**
     * Retourne l'intervalle courant entre deux ticks.
     *
     * @return délai en millisecondes
     */
    public int getTickDelay() {
        return tickDelay;
    }

    /**
     * Retourne l'état courant de la simulation.
     *
     * @return état, jamais {@code null}
     */
    public SimulationState getState() {
        return state;
    }

    /**
     * Sauvegarde l'état courant dans un fichier binaire.
     *
     * @param file fichier destination
     * @throws IOException si l'écriture échoue
     */
    public void saveToFile(File file) throws IOException {
        SimulationIO.save(file, simulator.createSnapshot());
    }

    /**
     * Restaure l'état depuis un fichier binaire. Met en pause si en cours,
     * puis applique le snapshot.
     *
     * @param file fichier source
     * @throws IOException            si la lecture échoue
     * @throws ClassNotFoundException si le format est incompatible
     * @throws IllegalArgumentException si les dimensions de la grille diffèrent
     */
    public void loadFromFile(File file) throws IOException, ClassNotFoundException {
        if (state == SimulationState.RUNNING) pause();
        SimulationSnapshot snapshot = SimulationIO.load(file);
        simulator.applySnapshot(snapshot);
        state = SimulationState.PAUSED;
    }

    // -------------------------------------------------------------------------

    private void startThread() {
        simulationThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                simulator.tick();
                try {
                    Thread.sleep(tickDelay);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        simulationThread.setDaemon(true);
        simulationThread.start();
    }

    private void stopThread() {
        if (simulationThread != null) {
            simulationThread.interrupt();
            simulationThread = null;
        }
    }
}
