package ru.karpenko;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        // Подключаемся к формирователю на порту 8081
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 8081)
                .usePlaintext()
                .build();

        GridServiceGrpc.GridServiceBlockingStub stub = GridServiceGrpc.newBlockingStub(channel);

        /*int[][] adjacencyMatrix = {
                {0, 10, 15, 20},
                {10, 0, 35, 25},
                {15, 35, 0, 30},
                {20, 25, 30, 0}
        };*/
        int[][] adjacencyMatrix = {
                {0, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75},
                {10, 0, 35, 25, 30, 20, 45, 50, 55, 60, 65, 70, 75, 80, 85},
                {15, 35, 0, 30, 25, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85},
                {20, 25, 30, 0, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85},
                {25, 30, 25, 35, 0, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90},
                {30, 20, 40, 40, 45, 0, 55, 60, 65, 70, 75, 80, 85, 90, 95},
                {35, 45, 45, 45, 50, 55, 0, 65, 70, 75, 80, 85, 90, 95, 100},
                {40, 50, 50, 50, 55, 60, 65, 0, 75, 80, 85, 90, 95, 100, 105},
                {45, 55, 55, 55, 60, 65, 70, 75, 0, 85, 90, 95, 100, 105, 110},
                {50, 60, 60, 60, 65, 70, 75, 80, 85, 0, 95, 100, 105, 110, 115},
                {55, 65, 65, 65, 70, 75, 80, 85, 90, 95, 0, 105, 110, 115, 120},
                {60, 70, 70, 70, 75, 80, 85, 90, 95, 100, 105, 0, 115, 120, 125},
                {65, 75, 75, 75, 80, 85, 90, 95, 100, 105, 110, 115, 0, 125, 130},
                {70, 80, 80, 80, 85, 90, 95, 100, 105, 110, 115, 120, 125, 0, 135},
                {75, 85, 85, 85, 90, 95, 100, 105, 110, 115, 120, 125, 130, 135, 0}
        };

        TaskRequest request = TaskRequest.newBuilder()
                .setMatrixSize(15)
                .setPathLength(6)
                .addAllAdjacencyMatrix(
                        Arrays.stream(adjacencyMatrix)
                                .map(row -> MatrixRow.newBuilder()
                                        .addAllValues(Arrays.stream(row).boxed().collect(Collectors.toList()))
                                        .build())
                                .collect(Collectors.toList())
                )
                .build();

        System.out.println("Отправка задачи формирователю...");
        try {
            TaskResponse response = stub.addTask(request);
            String taskId = response.getTaskId();
            System.out.println("Задача отправлена. taskId: " + taskId);

            // Ждем завершения обработки
            Thread.sleep(5000);

            // Получаем результат
            ResultRequest resultRequest = ResultRequest.newBuilder()
                    .setTaskId(taskId)
                    .build();

            ResultResponse resultResponse = stub.getResult(resultRequest);
            System.out.println("Результат получен: " + resultResponse.getResultData().toStringUtf8());
        } catch (Exception e) {
            System.err.println("Ошибка при вызове сервера: " + e.getMessage());
            e.printStackTrace();
        } finally {
            channel.shutdown();
        }
    }
}
