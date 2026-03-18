package ru.karpenko.model;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
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
        int totalCombinations = calculateTotalCombinations(matrixSize, pathLength);
        int batchSize = 2000000;
        for (int start = 0; start < totalCombinations; start += batchSize) {
            int currentBatchSize = Math.min(batchSize, totalCombinations - start);
            byte[] subTaskData = serializeSubTask(start, currentBatchSize);
            subTasks.add(subTaskData);
        }
        return subTasks;
    }

    public int calculateTotalCombinations(int matrixSize, int pathLength) {
        int totalCombinations = 1;
        for (int i = 0; i < pathLength; i++) {
            totalCombinations *= (matrixSize - i);
        }
        return totalCombinations;
    }

    public byte[] serializeSubTask(int start, int batchSize) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            SubTask subTask = new SubTask(adjacencyMatrix, start, batchSize, pathLength);
            oos.writeObject(subTask);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка сериализации подзадачи", e);
        }
    }


    public byte[] getBaseData() {
        return baseData;
    }

    public List<byte[]> getSubTaskDataList() {
        return subTaskDataList;
    }
}
