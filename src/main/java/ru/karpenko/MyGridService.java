package ru.karpenko;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import com.google.protobuf.ByteString;
import ru.karpenko.model.Task;
import ru.karpenko.model.BatchResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class MyGridService extends GridServiceGrpc.GridServiceImplBase {
    private final Map<String, Task> tasks = new HashMap<>();
    private final Map<String, List<BatchResult>> batchResults = new HashMap<>();
    private final Map<String, byte[]> results = new HashMap<>();
    private byte[] solverJarBytes;

    public MyGridService() throws Exception {
        solverJarBytes = Files.readAllBytes(Paths.get("libs/gRPC-Solver-1.0-SNAPSHOT.jar"));
    }

    @Override
    public void addTask(TaskRequest request, StreamObserver<TaskResponse> responseObserver) {
        try {
            String taskId = UUID.randomUUID().toString();
            int[][] adjacencyMatrix = request.getAdjacencyMatrixList().stream()
                    .map(row -> row.getValuesList().stream().mapToInt(Integer::intValue).toArray())
                    .toArray(int[][]::new);
            Task task = new Task(adjacencyMatrix, request.getMatrixSize(), request.getPathLength());
            tasks.put(taskId, task);
            batchResults.put(taskId, new ArrayList<>());
            sendTasksToDistributor(taskId, solverJarBytes, task.getBaseData(), task.getSubTaskDataList());
            responseObserver.onNext(TaskResponse.newBuilder().setTaskId(taskId).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            System.err.println("Ошибка при обработке задачи: " + e.getMessage());
            e.printStackTrace();
            responseObserver.onError(e);
        }
    }

    private void sendTasksToDistributor(String taskId, byte[] jarData, byte[] baseData, List<byte[]> subTaskDataList) {
        try {
            ManagedChannel distributorChannel = ManagedChannelBuilder
                    .forAddress("localhost", 8082)
                    .usePlaintext()
                    .build();
            DistributorServiceGrpc.DistributorServiceBlockingStub distributorStub =
                    DistributorServiceGrpc.newBlockingStub(distributorChannel);

            for (byte[] subTaskData : subTaskDataList) {
                DistributorTaskRequest taskRequest = DistributorTaskRequest.newBuilder()
                        .setTaskId(taskId)
                        .setJarData(ByteString.copyFrom(jarData))
                        .setBaseData(ByteString.copyFrom(baseData))
                        .setSubTaskData(ByteString.copyFrom(subTaskData))
                        .build();
                TaskResponse response = distributorStub.addTask(taskRequest);
                System.out.println("Подзадача отправлена на распределитель: " + response.getTaskId());
            }

            Thread.sleep(3000);
            collectResultsFromDistributor(taskId, distributorStub);
            distributorChannel.shutdown();
        } catch (Exception e) {
            System.err.println("Ошибка при отправке подзадач на распределитель: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void collectResultsFromDistributor(String taskId, DistributorServiceGrpc.DistributorServiceBlockingStub distributorStub) {
        try {
            ResultRequest resultRequest = ResultRequest.newBuilder().setTaskId(taskId).build();
            ResultResponse resultResponse = distributorStub.getResult(resultRequest);
            byte[] resultData = resultResponse.getResultData().toByteArray();
            saveResult(taskId, resultData);
            System.out.println("Результат для задачи " + taskId + " успешно получен и сохранён");
        } catch (Exception e) {
            System.err.println("Ошибка при попытке получить результат с распределителя: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveResult(String taskId, byte[] resultData) {
        results.put(taskId, resultData);
        System.out.println("Результат для задачи " + taskId + " успешно сохранен");
    }

    @Override
    public void getResult(ResultRequest request, StreamObserver<ResultResponse> responseObserver) {
        try {
            String taskId = request.getTaskId();
            byte[] resultData = results.get(taskId);
            if (resultData == null) {
                responseObserver.onError(new RuntimeException("Результат не найден для задачи " + taskId));
                return;
            }
            responseObserver.onNext(ResultResponse.newBuilder()
                    .setResultData(ByteString.copyFrom(resultData))
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            System.err.println("Ошибка при обработке запроса на результат: " + e.getMessage());
            e.printStackTrace();
            responseObserver.onError(e);
        }
    }

    // Метод для десериализации BatchResult из байтов
    private BatchResult deserializeBatchResult(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ObjectInputStream is = new ObjectInputStream(in);
        return (BatchResult) is.readObject();
    }

    // Метод для вывода всех результатов
    private void printAllResults(String taskId) {
        List<BatchResult> batchResultList = batchResults.get(taskId);
        if (batchResultList == null || batchResultList.isEmpty()) {
            System.out.println("Нет результатов для задачи: " + taskId);
            return;
        }
        System.out.println("Результаты для задачи " + taskId + ":");
        batchResultList.forEach(batchResult -> {
            System.out.println("Путь: " + batchResult.getPath() + ", Стоимость: " + batchResult.getCost());
        });
    }

    // Метод для поиска самого дешевого пути
    private BatchResult findCheapestPath(String taskId) {
        List<BatchResult> batchResultList = batchResults.get(taskId);
        if (batchResultList == null || batchResultList.isEmpty()) {
            throw new RuntimeException("Нет результатов для задачи: " + taskId);
        }
        return batchResultList.stream()
                .min(Comparator.comparingInt(BatchResult::getCost))
                .orElseThrow(() -> new RuntimeException("Нет результатов для задачи: " + taskId));
    }

    // Метод для запуска сервера
    public static void main(String[] args) throws Exception {
        Server server = ServerBuilder.forPort(8081)
                .addService(new MyGridService())
                .build()
                .start();
        System.out.println("Формирователь запущен на порту 8081");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Остановка сервера формирователя...");
            server.shutdown();
        }));
        server.awaitTermination();
    }
}
