package ru.karpenko.model;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Task implements Serializable {
    private int[][] adjacencyMatrix;
    private int matrixSize;
    private int pathLength;
    private byte[] baseData;
    private List<byte[]> subTaskDataList;
    private List<BatchResult> batchResults = new ArrayList<>();

    public Task(int[][] adjacencyMatrix, int matrixSize, int pathLength) {
        this.adjacencyMatrix = adjacencyMatrix;
        this.matrixSize = matrixSize;
        this.pathLength = pathLength;
        this.baseData = serializeBaseData();
        this.subTaskDataList = generateSubTasks();
    }

    private byte[] serializeBaseData() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(adjacencyMatrix);
            oos.writeInt(matrixSize);
            oos.writeInt(pathLength);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка сериализации исходных данных", e);
        }
    }

    private List<byte[]> generateSubTasks() {
        List<byte[]> subTasks = new ArrayList<>();
        int totalCombinations = calculateTotalCombinations();
        int batchSize = 2000000;
        for (int start = 0; start < totalCombinations; start += batchSize) {
            int currentBatchSize = Math.min(batchSize, totalCombinations - start);
            byte[] subTaskData = serializeSubTask(start, currentBatchSize);
            subTasks.add(subTaskData);
        }
        return subTasks;
    }

    public int calculateTotalCombinations() {
        return (int) Math.pow(matrixSize, pathLength);
    }

    public byte[] serializeSubTask(int start, int batchSize) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(adjacencyMatrix);
            oos.writeInt(matrixSize);
            oos.writeInt(pathLength);
            oos.writeInt(start);
            oos.writeInt(batchSize);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка сериализации подзадачи", e);
        }
    }

    public void addBatchResult(int startCombination, byte[] resultData) {
        batchResults.add(new BatchResult(startCombination, resultData));
    }

    public byte[] aggregateResults() {
        try {
            // Здесь должна быть логика агрегации результатов
            // Например, поиск самого дешевого пути
            // Для простоты возвращаем первый полученный результат
            if (!batchResults.isEmpty()) {
                return batchResults.get(0).getResultData();
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка агрегации результатов", e);
        }
    }

    public byte[] getBaseData() {
        return baseData;
    }

    public List<byte[]> getSubTaskDataList() {
        return subTaskDataList;
    }
}
