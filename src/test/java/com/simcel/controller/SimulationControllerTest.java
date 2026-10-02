package com.simcel.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.simcel.model.Cell;
import com.simcel.model.CellState;
import com.simcel.model.CellType;
import com.simcel.model.Environment;
import com.simcel.model.FireSimulator;
import com.simcel.model.Grid;
import com.simcel.model.SimulationState;
import com.simcel.model.WindDirection;

class SimulationControllerTest {

    private Grid grid;
    private FireSimulator simulator;
    private SimulationController controller;
    private final AtomicInteger ticks = new AtomicInteger();

    @BeforeEach
    void setUp() {
        grid = new Grid(3, 1);
        for (int x = 0; x < 3; x++) grid.setCell(x, 0, new Cell(CellType.FORET));
        grid.saveInitialState();
        grid.setFire(0, 0);
        // Humidité 100 % : rien ne se propage, la grille n'évolue que par combustion
        simulator = new FireSimulator(grid, new Environment(WindDirection.N, 0, 100));
        simulator.addListener((tick, g) -> ticks.incrementAndGet());
        controller = new SimulationController(simulator, 5);
    }

    @AfterEach
    void tearDown() {
        controller.stop();
    }

    @Test
    void aNewControllerIsIdle() {
        assertEquals(SimulationState.IDLE, controller.getState());
        assertEquals(5, controller.getTickDelay());
    }

    @Test
    void stepAdvancesExactlyOneTickWhenNotRunning() {
        controller.step();

        assertEquals(1, ticks.get());
        assertEquals(SimulationState.IDLE, controller.getState());
    }

    @Test
    void startRunsTicksInTheBackground() throws InterruptedException {
        CountDownLatch threeTicks = new CountDownLatch(3);
        simulator.addListener((tick, g) -> threeTicks.countDown());

        controller.start();

        assertEquals(SimulationState.RUNNING, controller.getState());
        assertTrue(threeTicks.await(5, TimeUnit.SECONDS));
    }

    @Test
    void pauseStopsTheTicks() throws InterruptedException {
        controller.start();
        controller.pause();
        assertEquals(SimulationState.PAUSED, controller.getState());

        // Un tick déjà engagé peut encore aboutir, mais plus aucun ensuite
        Thread.sleep(50);
        int afterPause = ticks.get();
        Thread.sleep(100);

        assertEquals(afterPause, ticks.get());
    }

    @Test
    void pauseOnlyAppliesToARunningSimulation() {
        controller.pause();
        assertEquals(SimulationState.IDLE, controller.getState());
    }

    @Test
    void stepAndStepBackAreIgnoredWhileRunning() throws InterruptedException {
        controller.step();
        CountDownLatch running = new CountDownLatch(1);
        simulator.addListener((tick, g) -> running.countDown());
        controller.start();
        assertTrue(running.await(5, TimeUnit.SECONDS));

        assertFalse(controller.stepBack());
    }

    @Test
    void stepBackUndoesAManualStep() {
        controller.step();

        assertTrue(controller.stepBack());
        assertEquals(CellType.FORET.getBurnDuration(), grid.getCell(0, 0).getRemainingBurnTime());
    }

    @Test
    void stopReturnsToIdle() {
        controller.start();
        controller.stop();
        assertEquals(SimulationState.IDLE, controller.getState());
    }

    @Test
    void resetRestoresTheInitialGridAndClearsTheHistory() {
        controller.step();

        controller.reset();

        assertEquals(SimulationState.IDLE, controller.getState());
        for (int x = 0; x < 3; x++) assertEquals(CellState.SAIN, grid.getCell(x, 0).getState());
        assertFalse(controller.stepBack());
    }

    @Test
    void setTickDelayIsKeptWhileRunning() {
        controller.start();
        controller.setTickDelay(20);

        assertEquals(20, controller.getTickDelay());
        assertEquals(SimulationState.RUNNING, controller.getState());
    }

    @Test
    void aSavedSimulationCanBeLoadedBack(@TempDir File dir) throws Exception {
        File file = new File(dir, "run.simcel");
        controller.step();
        controller.saveToFile(file);
        controller.step();
        controller.step();

        controller.loadFromFile(file);

        assertEquals(SimulationState.PAUSED, controller.getState());
        assertEquals(CellType.FORET.getBurnDuration() - 1, grid.getCell(0, 0).getRemainingBurnTime());
        assertEquals(CellState.EN_FEU, grid.getCell(0, 0).getState());
    }
}
