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

        int[][] adjacencyMatrix = {
                {0, 10, 15, 20},
                {10, 0, 35, 25},
                {15, 35, 0, 30},
                {20, 25, 30, 0}
        };

        TaskRequest request = TaskRequest.newBuilder()
                .setMatrixSize(4)
                .setPathLength(3)
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
