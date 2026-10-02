package com.simcel.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FireSimulatorTest {

    private static final double EPS = 1e-9;

    /** Tirage constant : 0.0 enflamme toute cellule de probabilité > 0. */
    private static Random fixedDraw(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    private static Grid filledGrid(int width, int height, CellType type) {
        Grid grid = new Grid(width, height);
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                grid.setCell(x, y, new Cell(type));
        grid.saveInitialState();
        return grid;
    }

    private static FireSimulator alwaysSpreading(Grid grid) {
        return new FireSimulator(grid, new Environment(WindDirection.N, 0, 0), fixedDraw(0.0));
    }

    @Nested
    class InflammationProbability {

        private double probability(Environment env, CellType type, int dx, int dy) {
            Grid grid = filledGrid(3, 3, type);
            FireSimulator sim = new FireSimulator(grid, env);
            return sim.computeInflammationProbability(1, 1, 1 + dx, 1 + dy, grid.getCell(1 + dx, 1 + dy));
        }

        @Test
        void withoutWindNorHumidityItIsTheBaseInflammability() {
            Environment env = new Environment(WindDirection.N, 0, 0);
            for (CellType type : CellType.values()) {
                assertEquals(type.getInflammability(), probability(env, type, 1, 0), EPS, type.name());
            }
        }

        @Test
        void humidityReducesItProportionally() {
            assertEquals(0.45, probability(new Environment(WindDirection.N, 0, 50), CellType.PRAIRIE, 1, 0), EPS);
            assertEquals(0.0, probability(new Environment(WindDirection.N, 0, 100), CellType.PRAIRIE, 1, 0), EPS);
        }

        @Test
        void tailwindDoublesItAtFullStrength() {
            Environment eastWind = new Environment(WindDirection.E, 5, 50);
            assertEquals(0.7, probability(eastWind, CellType.FORET, 1, 0), EPS);
        }

        @Test
        void headwindCancelsItAtFullStrength() {
            Environment eastWind = new Environment(WindDirection.E, 5, 50);
            assertEquals(0.0, probability(eastWind, CellType.FORET, -1, 0), EPS);
        }

        @Test
        void crosswindLeavesItUnchanged() {
            Environment eastWind = new Environment(WindDirection.E, 5, 50);
            assertEquals(0.35, probability(eastWind, CellType.FORET, 0, -1), EPS);
        }

        @Test
        void diagonalPropagationUsesTheCosineOfTheAngle() {
            Environment northWind = new Environment(WindDirection.N, 5, 0);
            double expected = 0.2 * (1.0 + Math.cos(Math.PI / 4));
            assertEquals(expected, probability(northWind, CellType.ZONE_HUMIDE, 1, -1), EPS);
        }

        @Test
        void partialWindScalesTheFactor() {
            Environment eastWind = new Environment(WindDirection.E, 2, 0);
            assertEquals(0.5 * 1.4, probability(eastWind, CellType.BROUSSAILLES, 1, 0), EPS);
        }

        @Test
        void itIsClampedToOne() {
            Environment eastWind = new Environment(WindDirection.E, 5, 0);
            assertEquals(1.0, probability(eastWind, CellType.PRAIRIE, 1, 0), EPS);
        }
    }

    @Nested
    class StateTransitions {

        @Test
        void fireSpreadsToTheEightHealthyNeighbours() {
            Grid grid = filledGrid(3, 3, CellType.FORET);
            grid.setFire(1, 1);

            alwaysSpreading(grid).tick();

            for (int x = 0; x < 3; x++)
                for (int y = 0; y < 3; y++)
                    assertEquals(CellState.EN_FEU, grid.getCell(x, y).getState(), x + "," + y);
        }

        @Test
        void fireDoesNotSpreadWhenTheDrawExceedsTheProbability() {
            Grid grid = filledGrid(3, 1, CellType.FORET);
            grid.setFire(0, 0);
            FireSimulator sim = new FireSimulator(grid, new Environment(WindDirection.N, 0, 0), fixedDraw(0.7));

            sim.tick();

            assertEquals(CellState.SAIN, grid.getCell(1, 0).getState());
        }

        @Test
        void newlyIgnitedCellsOnlySpreadFromTheNextTick() {
            Grid grid = filledGrid(3, 1, CellType.FORET);
            grid.setFire(0, 0);
            FireSimulator sim = alwaysSpreading(grid);

            sim.tick();
            assertEquals(CellState.EN_FEU, grid.getCell(1, 0).getState());
            assertEquals(CellState.SAIN, grid.getCell(2, 0).getState());

            sim.tick();
            assertEquals(CellState.EN_FEU, grid.getCell(2, 0).getState());
        }

        @Test
        void aCellBurnsForItsTypeDurationThenBurnsOut() {
            Grid grid = filledGrid(1, 1, CellType.PRAIRIE);
            grid.setFire(0, 0);
            FireSimulator sim = alwaysSpreading(grid);
            Cell cell = grid.getCell(0, 0);

            for (int i = 1; i < CellType.PRAIRIE.getBurnDuration(); i++) {
                sim.tick();
                assertEquals(CellState.EN_FEU, cell.getState(), "tick " + i);
                assertEquals(CellType.PRAIRIE.getBurnDuration() - i, cell.getRemainingBurnTime());
            }
            sim.tick();
            assertEquals(CellState.BRULE, cell.getState());
            assertEquals(0, cell.getRemainingBurnTime());
        }

        @Test
        void aNewlyIgnitedCellKeepsItsFullBurnTime() {
            Grid grid = filledGrid(2, 1, CellType.FORET);
            grid.setFire(0, 0);

            alwaysSpreading(grid).tick();

            assertEquals(CellType.FORET.getBurnDuration(), grid.getCell(1, 0).getRemainingBurnTime());
            assertEquals(CellType.FORET.getBurnDuration() - 1, grid.getCell(0, 0).getRemainingBurnTime());
        }

        @Test
        void emptyAndBurntCellsNeverIgnite() {
            Grid grid = filledGrid(3, 1, CellType.FORET);
            grid.getCell(0, 0).setState(CellState.VIDE);
            grid.getCell(2, 0).burnOut();
            grid.setFire(1, 0);

            alwaysSpreading(grid).tick();

            assertEquals(CellState.VIDE, grid.getCell(0, 0).getState());
            assertEquals(CellState.BRULE, grid.getCell(2, 0).getState());
        }

        @Test
        void aGridWithoutFireDoesNotChange() {
            Grid grid = filledGrid(4, 4, CellType.PRAIRIE);

            alwaysSpreading(grid).tick();

            for (int x = 0; x < 4; x++)
                for (int y = 0; y < 4; y++)
                    assertEquals(CellState.SAIN, grid.getCell(x, y).getState());
        }
    }

    @Nested
    class HistoryAndListeners {

        @Test
        void listenersReceiveEachTickNumber() {
            FireSimulator sim = alwaysSpreading(filledGrid(2, 2, CellType.FORET));
            List<Integer> ticks = new ArrayList<>();
            sim.addListener((tick, grid) -> ticks.add(tick));

            sim.tick();
            sim.tick();

            assertEquals(List.of(1, 2), ticks);
        }

        @Test
        void stepBackRestoresThePreviousGridAndTick() {
            Grid grid = filledGrid(3, 1, CellType.FORET);
            grid.setFire(0, 0);
            FireSimulator sim = alwaysSpreading(grid);
            List<Integer> ticks = new ArrayList<>();
            sim.addListener((tick, g) -> ticks.add(tick));

            sim.tick();
            assertTrue(sim.stepBack());

            assertEquals(CellState.EN_FEU, grid.getCell(0, 0).getState());
            assertEquals(CellType.FORET.getBurnDuration(), grid.getCell(0, 0).getRemainingBurnTime());
            assertEquals(CellState.SAIN, grid.getCell(1, 0).getState());
            assertEquals(List.of(1, 0), ticks);
        }

        @Test
        void stepBackWithoutHistoryDoesNothing() {
            FireSimulator sim = alwaysSpreading(filledGrid(2, 2, CellType.FORET));
            assertFalse(sim.stepBack());
        }

        @Test
        void clearHistoryForbidsGoingBack() {
            FireSimulator sim = alwaysSpreading(filledGrid(2, 2, CellType.FORET));
            sim.tick();
            sim.clearHistory();
            assertFalse(sim.stepBack());
        }

        @Test
        void historyIsBoundedToTheLastHundredTicks() {
            FireSimulator sim = alwaysSpreading(filledGrid(2, 2, CellType.FORET));
            for (int i = 0; i < 150; i++) sim.tick();

            int steps = 0;
            while (sim.stepBack()) steps++;

            assertEquals(100, steps);
        }
    }

    @Nested
    class Snapshots {

        @Test
        void aSnapshotRestoresGridEnvironmentAndTick() {
            Grid grid = filledGrid(3, 3, CellType.FORET);
            grid.setFire(1, 1);
            Environment env = new Environment(WindDirection.SE, 3, 20);
            FireSimulator sim = new FireSimulator(grid, env, fixedDraw(0.0));
            sim.tick();
            SimulationSnapshot snapshot = sim.createSnapshot();

            sim.tick();
            env.setDirection(WindDirection.O);
            env.setWindStrength(0);
            env.setHumidity(90);
            sim.applySnapshot(snapshot);

            assertEquals(CellType.FORET.getBurnDuration() - 1, grid.getCell(1, 1).getRemainingBurnTime());
            assertEquals(WindDirection.SE, env.getDirection());
            assertEquals(3, env.getWindStrength());
            assertEquals(20, env.getHumidity());
            assertEquals(1, sim.createSnapshot().getTick());
            assertFalse(sim.stepBack(), "applying a snapshot clears the history");
        }

        @Test
        void aSnapshotOfAnotherSizeIsRejected() {
            FireSimulator small = alwaysSpreading(filledGrid(2, 2, CellType.FORET));
            FireSimulator large = alwaysSpreading(filledGrid(3, 3, CellType.FORET));

            assertThrows(IllegalArgumentException.class, () -> small.applySnapshot(large.createSnapshot()));
        }
    }
}
