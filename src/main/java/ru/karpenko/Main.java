package ru.karpenko;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import com.google.protobuf.Empty;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        // Создаем канал для связи с формирователем
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 8081)
                .usePlaintext()
                .build();

        // Создаем stub для отправки задачи
        GridServiceGrpc.GridServiceStub stub = GridServiceGrpc.newStub(channel);

        // Матрица смежности
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

        // Создаем запрос задачи
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

        // Создаем CountDownLatch для ожидания завершения
        CountDownLatch finishLatch = new CountDownLatch(1);

        // Запускаем gRPC сервер на клиенте для получения результата
        Server server = ServerBuilder.forPort(8085)
                .addService(new ClientServiceImpl(finishLatch))
                .build()
                .start();

        System.out.println("Клиентский сервер запущен на порту 8085");

        // Отправляем задачу формирователю
        System.out.println("Отправка задачи формирователю...");
        stub.addTask(request, new StreamObserver<TaskResponse>() {
            @Override
            public void onNext(TaskResponse response) {
                String taskId = response.getTaskId();
                System.out.println("Задача отправлена. taskId: " + taskId);
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Ошибка при отправке задачи: " + t.getMessage());
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("Задача успешно отправлена");
            }
        });

        // Ожидаем завершения
        finishLatch.await(20, TimeUnit.SECONDS);

        System.out.println("Завершаем");
        // Завершаем канал и сервер
        channel.shutdown();
        server.shutdown();
    }

    static class ClientServiceImpl extends ClientServiceGrpc.ClientServiceImplBase {
        private final CountDownLatch finishLatch;

        ClientServiceImpl(CountDownLatch finishLatch) {
            this.finishLatch = finishLatch;
        }

        @Override
        public void sendResult(ResultResponse request, StreamObserver<Empty> responseObserver) {
            try {
                String taskId = request.getTaskId();
                byte[] resultData = request.getResultData().toByteArray();

                System.out.println("Результат получен для задачи " + taskId + ", размер: " + resultData.length);

                // Десериализация результата
                String resultString = new String(resultData, java.nio.charset.StandardCharsets.UTF_8);
                System.out.println("Результат: " + resultString);

                responseObserver.onNext(Empty.getDefaultInstance());
                responseObserver.onCompleted();
            } catch (Exception e) {
                System.err.println("Ошибка при обработке результата: " + e.getMessage());
                responseObserver.onError(e);
            }
        }
    }
}