package ru.karpenko;

import ru.karpenko.model.BatchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

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

        int numThreads = Runtime.getRuntime().availableProcessors();
        int batchSize = combinationsCount / numThreads;

        ConcurrentLinkedQueue<BatchResult> resultsQueue = new ConcurrentLinkedQueue<>();

        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            int start = startCombination + i * batchSize;
            int end = (i == numThreads - 1) ? startCombination + combinationsCount : start + batchSize;

            int finalI = i;
            int finalStart = start;
            int finalEnd = end;

            Thread thread = new Thread(() -> {
                System.out.println("Thread " + finalI + " processing combinations from " + finalStart + " to " + finalEnd);
                BatchResult result = processCombinations(finalStart, finalEnd - finalStart);
                resultsQueue.add(result);
            });

            threads.add(thread);
            thread.start();
        }

        // Ожидание завершения всех потоков
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Поиск минимального результата
        BatchResult minResult = null;
        for (BatchResult result : resultsQueue) {
            if (minResult == null || result.getCost() < minResult.getCost()) {
                minResult = result;
            }
        }

        return minResult;
    }

    private BatchResult processCombinations(int startCombination, int combinationsCount) {
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