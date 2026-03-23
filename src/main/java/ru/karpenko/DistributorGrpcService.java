package ru.karpenko;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import ru.karpenko.DistributorServiceGrpc.DistributorServiceImplBase;

@GrpcService
public class DistributorGrpcService extends DistributorServiceImplBase {

    @Autowired
    private DistributorService distributorService;
    @Autowired
    private RestTemplate restTemplate;

    @Override
    public void addTask(DistributorTaskRequest request, StreamObserver<TaskResponse> responseObserver) {
        try {
            String taskId = request.getTaskId();
            WorkerInfo freeWorker = distributorService.findFreeWorker();

            if (freeWorker != null) {
                // Отправляем задачу воркеру через HTTP
                String workerUrl = freeWorker.getWorkerUrl() + "/solveSubtask";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("taskId", taskId);
                body.add("jar", new ByteArrayResource(request.getJarData().toByteArray()) {
                    @Override
                    public String getFilename() {
                        return "solver.jar";
                    }
                });
                body.add("baseData", new ByteArrayResource(request.getBaseData().toByteArray()) {
                    @Override
                    public String getFilename() {
                        return "baseData.dat";
                    }
                });
                body.add("subTaskData", new ByteArrayResource(request.getSubTaskData().toByteArray()) {
                    @Override
                    public String getFilename() {
                        return "subTaskData.dat";
                    }
                });
                body.add("managerAddress", "http://localhost:8083"); // адрес распределителя

                HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
                ResponseEntity<String> response = restTemplate.exchange(workerUrl, HttpMethod.POST, requestEntity, String.class);

                System.out.println("[DISTRIBUTOR] Задача " + taskId + " отправлена воркеру: " + freeWorker.getWorkerUrl());
                distributorService.setWorkerBusy(freeWorker.getId(), true);

                responseObserver.onNext(TaskResponse.newBuilder().setTaskId(taskId).build());
                responseObserver.onCompleted();
            } else {
                // Добавляем задачу в очередь
                distributorService.addTaskToQueue(request);
                System.out.println("[DISTRIBUTOR] Нет свободных воркеров. Задача " + taskId + " добавлена в очередь.");
                responseObserver.onNext(TaskResponse.newBuilder().setTaskId(taskId).build());
                responseObserver.onCompleted();
            }

        } catch (Exception e) {
            System.err.println("[DISTRIBUTOR-GRPC] Ошибка: " + e.getMessage());
            e.printStackTrace();
            responseObserver.onError(e);
        }
    }

    @Override
    public void getResult(ResultRequest request, StreamObserver<ResultResponse> responseObserver) {
        try {
            String taskId = request.getTaskId();
            byte[] result = distributorService.getResult(taskId);

            if (result == null) {
                responseObserver.onError(new RuntimeException("Результат не найден для задачи: " + taskId));
                return;
            }

            responseObserver.onNext(ResultResponse.newBuilder()
                    .setResultData(com.google.protobuf.ByteString.copyFrom(result))
                    .build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            System.err.println("[DISTRIBUTOR-GRPC] Ошибка при получении результата: " + e.getMessage());
            e.printStackTrace();
            responseObserver.onError(e);
        }
    }
}
