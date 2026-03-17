package ru.karpenko;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import com.google.protobuf.ByteString;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DistributorService extends DistributorServiceGrpc.DistributorServiceImplBase {
    private final Map<String, byte[]> taskData = new HashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, byte[]> results = new HashMap<>();

    @Override
    public void addTask(DistributorTaskRequest request, StreamObserver<TaskResponse> responseObserver) {
        try {
            String taskId = request.getTaskId();
            System.out.println("Получен запрос на обработку задачи: " + taskId);

            byte[] jarData = request.getJarData().toByteArray();
            byte[] baseData = request.getBaseData().toByteArray();
            byte[] subTaskData = request.getSubTaskData().toByteArray();

            byte[] result = sendToWorker(taskId, jarData, baseData, subTaskData);

            // Сохраняем результат
            results.put(taskId, result);
            System.out.println("Результат для задачи " + taskId + " успешно сохранен на распределителе");

            responseObserver.onNext(TaskResponse.newBuilder().setTaskId(taskId).build());
            responseObserver.onCompleted();
            System.out.println("Ответ клиенту для задачи " + taskId + " успешно отправлен");
        } catch (Exception e) {
            System.err.println("Ошибка в addTask: " + e.getMessage());
            e.printStackTrace();
            responseObserver.onError(e);
        }
    }



    private byte[] sendToWorker(String taskId, byte[] jarData, byte[] baseData, byte[] subTaskData) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("taskId", taskId);
            body.add("jar", new ByteArrayResource(jarData) {
                @Override
                public String getFilename() {
                    return "solver.jar";
                }
            });
            body.add("baseData", new ByteArrayResource(baseData) {
                @Override
                public String getFilename() {
                    return "baseData.bin";
                }
            });
            body.add("subTaskData", new ByteArrayResource(subTaskData) {
                @Override
                public String getFilename() {
                    return "subTaskData.bin";
                }
            });

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    "http://localhost:8080/solveSubtask",
                    HttpMethod.POST,
                    requestEntity,
                    byte[].class
            );

            return response.getBody();
        } catch (Exception e) {
            System.err.println("Ошибка при отправке на воркер: " + e.getMessage());
            e.printStackTrace();
            return new byte[0];
        }
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


    public static void main(String[] args) throws IOException, InterruptedException {
        Server server = ServerBuilder.forPort(8082)
                .addService(new DistributorService())
                .build()
                .start();

        System.out.println("Распределитель запущен на порту 8082");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Остановка сервера распределителя...");
            server.shutdown();
        }));

        server.awaitTermination();
    }
}
