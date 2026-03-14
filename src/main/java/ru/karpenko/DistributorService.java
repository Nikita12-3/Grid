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

import java.io.IOException;
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

            // Сохраняем данные задачи
            taskData.put(taskId, jarData);

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
            // Создаем правильный заголовок
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // Создаем тело запроса
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("taskId", taskId);
            body.add("jar", new ByteArrayResource(jarData) {
                @Override
                public String getFilename() {
                    return "solver.jar";
                }
            });
            body.add("jsonBase", new String(baseData, "UTF-8"));
            body.add("jsonSubTask", new String(subTaskData, "UTF-8"));
            body.add("managerAddress", "localhost:8082");

            // Создаем запрос
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // Отправляем на воркера
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
