package ru.karpenko;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import com.google.protobuf.ByteString;
import ru.karpenko.model.Task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class MyGridService extends GridServiceGrpc.GridServiceImplBase {
    private final Map<String, Task> tasks = new HashMap<>();
    private final Map<String, byte[]> results = new HashMap<>();
    private byte[] solverJarBytes;

    public MyGridService() throws Exception {
        // Загружаем JAR с решателем из правильного пути
        solverJarBytes = Files.readAllBytes(Paths.get("libs/gRPC-Solver-1.0-SNAPSHOT.jar"));
    }

    @Override
    public void addTask(TaskRequest request, StreamObserver<TaskResponse> responseObserver) {
        try {
            // Генерируем уникальный идентификатор задачи
            String taskId = UUID.randomUUID().toString();

            // Преобразуем матрицу из запроса в двумерный массив
            int[][] adjacencyMatrix = request.getAdjacencyMatrixList().stream()
                    .map(row -> row.getValuesList().stream().mapToInt(Integer::intValue).toArray())
                    .toArray(int[][]::new);

            // Создаем новую задачу
            Task task = new Task(adjacencyMatrix, request.getMatrixSize(), request.getPathLength());
            tasks.put(taskId, task);

            // Отправляем подзадачи на распределитель и сохраняем результат
            sendTasksToDistributor(taskId, solverJarBytes, task.getBaseData(), task.getSubTaskDataList());

            // Возвращаем клиенту идентификатор задачи
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
            // Создаем канал связи с распределителем
            ManagedChannel distributorChannel = ManagedChannelBuilder
                    .forAddress("localhost", 8082)
                    .usePlaintext()
                    .build();

            // Создаем клиент для распределителя
            DistributorServiceGrpc.DistributorServiceBlockingStub distributorStub =
                    DistributorServiceGrpc.newBlockingStub(distributorChannel);

            // Отправляем каждую подзадачу на распределитель
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
            ResultRequest resultRequest = ResultRequest.newBuilder()
                    .setTaskId(taskId)
                    .build();
            try {
                ResultResponse resultResponse = distributorStub.getResult(resultRequest);
                byte[] resultData = resultResponse.getResultData().toByteArray();

                // Сохраняем результат
                saveResult(taskId, resultData);
            }
            catch (Exception e)
            {
                System.err.println("Ошибка при попытке получить результат с распределятора: " + e.getMessage());
            }
            distributorChannel.shutdown();
        } catch (Exception e) {
            System.err.println("Ошибка при отправке подзадач на распределитель: " + e.getMessage());
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
            System.out.println("Получен запрос на результат для задачи: " + taskId);

            byte[] resultData = results.get(taskId);
            System.out.println("Результат для задачи " + taskId + ": " + (resultData != null ? "найден" : "не найден"));

            if (resultData == null) {
                System.err.println("Ошибка: результат не найден для задачи " + taskId);
                responseObserver.onError(new RuntimeException("Результат не найден для задачи " + taskId));
                return;
            }

            System.out.println("Отправка результата для задачи " + taskId);
            responseObserver.onNext(ResultResponse.newBuilder()
                    .setResultData(ByteString.copyFrom(resultData))
                    .build());
            responseObserver.onCompleted();
            System.out.println("Результат для задачи " + taskId + " успешно отправлен");
        } catch (Exception e) {
            System.err.println("Ошибка при обработке запроса на результат: " + e.getMessage());
            e.printStackTrace();
            responseObserver.onError(e);
        }
    }


    // Метод для запуска сервера
    public static void main(String[] args) throws Exception {
        // Создаем сервер на порту 8081
        Server server = ServerBuilder.forPort(8081)
                .addService(new MyGridService())
                .build()
                .start();

        System.out.println("Формирователь запущен на порту 8081");

        // Обработчик завершения
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Остановка сервера формирователя...");
            server.shutdown();
        }));

        // Ожидаем завершения
        server.awaitTermination();
    }
}
