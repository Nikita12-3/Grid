package ru.karpenko;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Empty;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class MyGridService extends GridServiceGrpc.GridServiceImplBase {
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final Map<String, List<byte[]>> results = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> latches = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();
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
            results.put(taskId, new ArrayList<>());

            List<byte[]> subTaskDataList = task.getSubTaskDataList();
            CountDownLatch latch = new CountDownLatch(subTaskDataList.size());
            latches.put(taskId, latch);

            sendTasksToDistributor(taskId, solverJarBytes, task.getBaseData(), subTaskDataList);

            responseObserver.onNext(TaskResponse.newBuilder().setTaskId(taskId).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            System.err.println("Ошибка при обработке задачи: " + e.getMessage());
            e.printStackTrace();
            responseObserver.onError(e);
        }
    }

    @Override
    public void sendResult(ResultResponse request, StreamObserver<Empty> responseObserver) {
        try {
            String taskId = request.getTaskId();
            byte[] resultData = request.getResultData().toByteArray();

            System.out.println("[GRID] Получены результаты для задачи " + taskId + ", размер: " + resultData.length);

            if (resultData.length > 0) {
                ByteArrayInputStream byteStream = new ByteArrayInputStream(resultData);
                try (ObjectInputStream objectStream = new ObjectInputStream(byteStream)) {
                    List<byte[]> taskResults = (List<byte[]>) objectStream.readObject();
                    System.out.println("[GRID] Десериализовано " + taskResults.size() + " результатов");

                    byte[] cheapestPathData = findCheapestPath(taskId, taskResults);
                    sendResultToClient(taskId, cheapestPathData);
                } catch (ClassNotFoundException | IOException e) {
                    System.err.println("[GRID] Ошибка при десериализации: " + e.getMessage());
                    responseObserver.onError(e);
                    return;
                }
            } else {
                System.err.println("[GRID] Получен пустой массив байтов для задачи " + taskId);
            }

            responseObserver.onNext(Empty.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            System.err.println("[GRID] Ошибка при обработке результата: " + e.getMessage());
            responseObserver.onError(e);
        }
    }

    private byte[] findCheapestPath(String taskId, List<byte[]> resultDataList) throws IOException {
        if (resultDataList == null || resultDataList.isEmpty()) {
            throw new RuntimeException("Нет результатов для задачи " + taskId);
        }

        BatchResult cheapestPath = resultDataList.stream()
                .map(data -> {
                    try {
                        return deserializeBatchResult(data);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .min(Comparator.comparingInt(BatchResult::getCost))
                .orElseThrow(() -> new RuntimeException("Не удалось найти самый дешевый путь"));

        return objectMapper.writeValueAsBytes(cheapestPath);
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
                String subtaskId = UUID.randomUUID().toString();
                DistributorTaskRequest taskRequest = DistributorTaskRequest.newBuilder()
                        .setTaskId(taskId)
                        .setSubtaskId(subtaskId)
                        .setJarData(ByteString.copyFrom(jarData))
                        .setBaseData(ByteString.copyFrom(baseData))
                        .setSubTaskData(ByteString.copyFrom(subTaskData))
                        .build();

                distributorStub.addTask(taskRequest);
                System.out.println("Подзадача " + subtaskId + " отправлена на распределитель");
            }

            distributorChannel.shutdown();
        } catch (Exception e) {
            System.err.println("Ошибка при отправке подзадач на распределитель: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void sendResultToClient(String taskId, byte[] resultData) {
        try {
            ManagedChannel channel = ManagedChannelBuilder
                    .forAddress("localhost", 8085)
                    .usePlaintext()
                    .build();

            ClientServiceGrpc.ClientServiceStub clientStub = ClientServiceGrpc.newStub(channel);

            clientStub.sendResult(ResultResponse.newBuilder()
                            .setResultData(ByteString.copyFrom(resultData))
                            .build(),
                    new StreamObserver<com.google.protobuf.Empty>() {
                        @Override
                        public void onNext(com.google.protobuf.Empty empty) {
                            System.out.println("Результат успешно отправлен клиенту");
                        }

                        @Override
                        public void onError(Throwable t) {
                            System.err.println("Ошибка при отправке результата клиенту: " + t.getMessage());
                        }

                        @Override
                        public void onCompleted() {
                            System.out.println("Отправка результата клиенту завершена");
                            channel.shutdown();
                        }
                    });
        } catch (Exception e) {
            System.err.println("Ошибка при отправке результата клиенту: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private BatchResult deserializeBatchResult(byte[] data) throws IOException {
        return objectMapper.readValue(new String(data, StandardCharsets.UTF_8), BatchResult.class);
    }

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