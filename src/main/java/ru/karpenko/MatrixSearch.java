package ru.karpenko;

import java.util.ArrayList;
import java.util.List;

public class MatrixSearch {
    private int[][] adjacencyMatrix;
    private int pathLength;
    private int minCost;
    private List<Integer> minPath;

    @MainAnnotation
    public List<Integer> findCheapestPath(SubTask subTask) {
        this.adjacencyMatrix = subTask.getAdjacencyMatrix();
        this.pathLength = subTask.getPathLength();
        this.minCost = Integer.MAX_VALUE;
        this.minPath = new ArrayList<>();

        for (int startCity = 0; startCity < adjacencyMatrix.length; startCity++) {
            List<Integer> currentPath = new ArrayList<>();
            currentPath.add(startCity);
            findPath(startCity, 0, currentPath, new boolean[adjacencyMatrix.length]);
        }

        return minPath;
    }

    private void findPath(int currentCity, int currentCost, List<Integer> currentPath, boolean[] visited) {
        if (currentPath.size() == pathLength) {
            if (currentCost < minCost) {
                minCost = currentCost;
                minPath = new ArrayList<>(currentPath);
            }
            return;
        }

        visited[currentCity] = true;

        for (int nextCity = 0; nextCity < adjacencyMatrix.length; nextCity++) {
            if (!visited[nextCity] && adjacencyMatrix[currentCity][nextCity] > 0) {
                int newCost = currentCost + adjacencyMatrix[currentCity][nextCity];
                if (newCost < minCost) {
                    currentPath.add(nextCity);
                    findPath(nextCity, newCost, currentPath, visited);
                    currentPath.remove(currentPath.size() - 1);
                }
            }
        }

        visited[currentCity] = false;
    }
}
