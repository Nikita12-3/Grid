package ru.karpenko;


import ru.karpenko.model.BatchResult;

import java.util.ArrayList;
import java.util.List;

public class MatrixSearch {
    private int[][] adjacencyMatrix;
    private int pathLength;

    @MainAnnotation
    public BatchResult findCheapestPath(
            @Param("adjacencyMatrix") int[][] adjacencyMatrix,
            @Param("pathLength") int pathLength,
            @Param("startCombination") int startCombination,
            @Param("combinationsCount") int combinationsCount) {

        this.adjacencyMatrix = adjacencyMatrix;
        this.pathLength = pathLength;

        int minCost = Integer.MAX_VALUE;
        List<Integer> minPath = new ArrayList<>();

        for (int i = startCombination; i < startCombination + combinationsCount; i++) {
            List<Integer> currentPath = getCombinationByNumber(i, adjacencyMatrix.length, pathLength);
            int currentCost = calculatePathCost(currentPath);

            if (currentCost < minCost) {
                minCost = currentCost;
                minPath = currentPath;
            }
        }

        return new BatchResult(minPath, minCost);
    }

    private List<Integer> getCombinationByNumber(int combinationNumber, int matrixSize, int pathLength) {
        List<Integer> combination = new ArrayList<>();
        boolean[] used = new boolean[matrixSize];

        for (int i = 0; i < pathLength; i++) {
            int factorial = factorial(matrixSize - i - 1);
            int index = combinationNumber / factorial;
            combinationNumber %= factorial;

            int city = 0;
            while (index >= 0) {
                if (!used[city]) {
                    if (index == 0) {
                        used[city] = true;
                        combination.add(city);
                        break;
                    }
                    index--;
                }
                city++;
            }
        }

        return combination;
    }

    private int factorial(int n) {
        int result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    private int calculatePathCost(List<Integer> path) {
        int cost = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            cost += adjacencyMatrix[path.get(i)][path.get(i + 1)];
        }
        return cost;
    }
}
