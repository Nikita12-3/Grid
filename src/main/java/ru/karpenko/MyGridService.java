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
import java.net.InetSocketAddress;
import java.net.Socket;
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

                    // Сохраняем результаты для задачи
                    results.computeIfAbsent(taskId, k -> new ArrayList<>()).addAll(taskResults);

                    // Уменьшаем счетчик CountDownLatch
                    if (latches.containsKey(taskId)) {
                        latches.get(taskId).countDown();
                        System.out.println("[GRID] Осталось подзадач для задачи " + taskId + ": " + latches.get(taskId).getCount());
                    }

                    // Проверяем, все ли результаты получены
                    if (latches.containsKey(taskId) && latches.get(taskId).getCount() == 0) {
                        byte[] cheapestPathData = findCheapestPath(taskId);
                        if (isClientAvailable()) {
                            sendResultToClient(taskId, cheapestPathData);
                        } else {
                            System.err.println("[GRID] Клиентский сервер недоступен на порту 8085");
                        }
                    }
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

    private byte[] findCheapestPath(String taskId) throws IOException {
        List<byte[]> resultDataList = results.get(taskId);
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
    private boolean isClientAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 8085), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }


    private void sendResultToClient(String taskId, byte[] resultData) {
        int maxAttempts = 3;
        int attempt = 0;
        while (attempt < maxAttempts) {
            try {
                System.out.println("[GRID] Отправка результата для задачи " + taskId + " клиенту, попытка " + (attempt + 1));
                ManagedChannel channel = ManagedChannelBuilder
                        .forTarget("localhost:8085")
                        .usePlaintext()
                        .build();

                ClientServiceGrpc.ClientServiceStub clientStub = ClientServiceGrpc.newStub(channel);

                StreamObserver<Empty> responseObserver = new StreamObserver<Empty>() {
                    @Override
                    public void onNext(Empty empty) {
                        System.out.println("[GRID] Результат успешно отправлен клиенту");
                    }

                    @Override
                    public void onError(Throwable t) {
                        System.err.println("[GRID] Ошибка при отправке результата клиенту: " + t.getMessage());
                    }

                    @Override
                    public void onCompleted() {
                        System.out.println("[GRID] Отправка результата клиенту завершена");
                        channel.shutdown();
                    }
                };

                clientStub.sendResult(ResultResponse.newBuilder()
                        .setTaskId(taskId)
                        .setResultData(ByteString.copyFrom(resultData))
                        .build(), responseObserver);

                return; // Успешная отправка, выходим из цикла
            } catch (Exception e) {
                System.err.println("[GRID] Ошибка при отправке результата клиенту: " + e.getMessage());
                attempt++;
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(1000); // Пауза перед повторной попыткой
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
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