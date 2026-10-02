package com.simcel.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GridTest {

    @Test
    void aNewGridIsEmpty() {
        Grid grid = new Grid(4, 3);

        assertEquals(4, grid.getWidth());
        assertEquals(3, grid.getHeight());
        for (int x = 0; x < 4; x++)
            for (int y = 0; y < 3; y++)
                assertEquals(CellState.VIDE, grid.getCell(x, y).getState());
    }

    @ParameterizedTest
    @CsvSource({"0,0,true", "3,2,true", "-1,0,false", "0,-1,false", "4,0,false", "0,3,false"})
    void boundsFollowWidthAndHeight(int x, int y, boolean inBounds) {
        assertEquals(inBounds, new Grid(4, 3).isInBounds(x, y));
    }

    @Test
    void cellsAreAddressedByColumnThenRow() {
        Grid grid = new Grid(4, 3);
        Cell cell = new Cell(CellType.FORET);

        grid.setCell(3, 1, cell);

        assertSame(cell, grid.getCell(3, 1));
    }

    @ParameterizedTest
    @CsvSource({"0,0,3", "1,0,5", "0,1,5", "1,1,8", "2,2,3"})
    void neighbourhoodIsMooreClippedAtTheEdges(int x, int y, int expected) {
        assertEquals(expected, new Grid(3, 3).getNeighbors(x, y).size());
    }

    @Test
    void neighboursExcludeTheCellItself() {
        Grid grid = new Grid(3, 3);
        assertFalse(grid.getNeighbors(1, 1).contains(grid.getCell(1, 1)));
    }

    @Test
    void randomInitWithFullForestDensityFillsTheGridWithHealthyForest() {
        Grid grid = new Grid(10, 10);

        grid.initRandom(1.0, 0, 0, 0, 0);

        for (int x = 0; x < 10; x++)
            for (int y = 0; y < 10; y++) {
                assertEquals(CellType.FORET, grid.getCell(x, y).getType());
                assertEquals(CellState.SAIN, grid.getCell(x, y).getState());
            }
    }

    @Test
    void randomInitWithZeroDensityLeavesTheGridEmpty() {
        Grid grid = new Grid(10, 10);

        grid.initRandom(0, 0, 0, 0, 0);

        for (int x = 0; x < 10; x++)
            for (int y = 0; y < 10; y++)
                assertEquals(CellState.VIDE, grid.getCell(x, y).getState());
    }

    @Test
    void randomInitRejectsDensitiesAboveOne() {
        assertThrows(IllegalArgumentException.class, () -> new Grid(2, 2).initRandom(0.6, 0.5, 0, 0, 0));
    }

    @Test
    void setFireIgnitesAHealthyCellAndIgnoresOutOfBounds() {
        Grid grid = new Grid(2, 2);
        grid.setCell(0, 0, new Cell(CellType.PRAIRIE));

        grid.setFire(0, 0);
        grid.setFire(5, 5);

        assertEquals(CellState.EN_FEU, grid.getCell(0, 0).getState());
    }

    @Test
    void resetRestoresTheSavedInitialState() {
        Grid grid = new Grid(2, 1);
        grid.setCell(0, 0, new Cell(CellType.FORET));
        grid.saveInitialState();
        grid.setFire(0, 0);

        grid.reset();

        assertEquals(CellState.SAIN, grid.getCell(0, 0).getState());
        assertEquals(CellState.VIDE, grid.getCell(1, 0).getState());
    }

    @Test
    void copiesAreDeep() {
        Grid grid = new Grid(1, 1);
        grid.setCell(0, 0, new Cell(CellType.FORET));

        Cell[][] copy = grid.copyCells();
        copy[0][0].burnOut();

        assertNotSame(grid.getCell(0, 0), copy[0][0]);
        assertEquals(CellState.SAIN, grid.getCell(0, 0).getState());
    }

    @Test
    void restoreStateCopiesBothCurrentAndInitialCells() {
        Grid source = new Grid(2, 1);
        source.setCell(0, 0, new Cell(CellType.FORET));
        source.saveInitialState();
        source.setFire(0, 0);
        Grid target = new Grid(2, 1);

        target.restoreState(source.copyCells(), source.copyInitialCells());

        assertEquals(CellState.EN_FEU, target.getCell(0, 0).getState());
        target.reset();
        assertEquals(CellState.SAIN, target.getCell(0, 0).getState());
        assertTrue(target.getCell(0, 0).getState().isFlammable());
    }
}
