package ru.karpenko.model;

import java.io.Serializable;

public class SubTask implements Serializable {
    private int[][] adjacencyMatrix;
    private int startCombination;
    private int combinationsCount;
    private int pathLength;

    public SubTask(int[][] adjacencyMatrix, int startCombination, int combinationsCount, int pathLength) {
        this.adjacencyMatrix = adjacencyMatrix;
        this.startCombination = startCombination;
        this.combinationsCount = combinationsCount;
        this.pathLength = pathLength;
    }

    public int[][] getAdjacencyMatrix() {
        return adjacencyMatrix;
    }

    public int getStartCombination() {
        return startCombination;
    }

    public int getCombinationsCount() {
        return combinationsCount;
    }

    public int getPathLength() {
        return pathLength;
    }
}
