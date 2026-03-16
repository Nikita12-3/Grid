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

    @Override
    public void addTask(DistributorTaskRequest request, StreamObserver<TaskResponse> responseObserver) {
        try {
            String taskId = request.getTaskId();
            byte[] jarData = request.getJarData().toByteArray();
            byte[] baseData = request.getBaseData().toByteArray();
            byte[] subTaskData = request.getSubTaskData().toByteArray();

            System.out.println("Полученные данные на распределителе:");
            System.out.println("JAR данные (размер: " + jarData.length + " байт)");
            System.out.println("Base данные (размер: " + baseData.length + " байт):");
            System.out.println("Первые 100 байт base данных: " + Arrays.toString(Arrays.copyOf(baseData, Math.min(baseData.length, 100))));
            System.out.println("Подзадача данные (размер: " + subTaskData.length + " байт):");
            System.out.println("Первые 100 байт данных подзадачи: " + Arrays.toString(Arrays.copyOf(subTaskData, Math.min(subTaskData.length, 100))));

            // Отправляем подзадачу воркеру
            byte[] result = sendToWorker(taskId, jarData, baseData, subTaskData);

            // Возвращаем успешный ответ
            responseObserver.onNext(TaskResponse.newBuilder().setTaskId(taskId).build());
            responseObserver.onCompleted();
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
            byte[] resultData = taskData.get(taskId);

            if (resultData == null) {
                responseObserver.onError(new RuntimeException("Результат не найден для taskId: " + taskId));
                return;
            }

            responseObserver.onNext(ResultResponse.newBuilder()
                    .setResultData(ByteString.copyFrom(resultData))
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
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
