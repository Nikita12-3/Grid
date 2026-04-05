package ru.karpenko;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@GrpcService
public class DistributorGrpcService extends DistributorServiceGrpc.DistributorServiceImplBase {
    @Autowired
    private DistributorService distributorService;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public void addTask(DistributorTaskRequest request, StreamObserver<TaskResponse> responseObserver) {
        try {
            String taskId = request.getTaskId();
            String subtaskId = taskId + "_" + UUID.randomUUID().toString();

            WorkerInfo freeWorker = distributorService.findFreeWorker();
            if (freeWorker != null) {
                String workerId = freeWorker.getId();
                sendTaskToWorker(request, freeWorker.getWorkerUrl(), subtaskId, workerId);
                TaskResponse response = TaskResponse.newBuilder()
                        .setSubtaskId(subtaskId)
                        .setWorkerId(workerId)
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } else {
                DistributorTaskRequest subtaskRequest = DistributorTaskRequest.newBuilder(request)
                        .setSubtaskId(subtaskId)
                        .build();
                distributorService.addTask(subtaskRequest);
                TaskResponse response = TaskResponse.newBuilder()
                        .setSubtaskId(subtaskId)
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        } catch (Exception e) {
            System.err.println("[DISTRIBUTOR-GRPC] Ошибка: " + e.getMessage());
            e.printStackTrace();
            responseObserver.onError(e);
        }
    }

    private void sendTaskToWorker(DistributorTaskRequest taskRequest, String workerUrl, String subtaskId, String workerId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("taskId", taskRequest.getTaskId());
            body.add("subtaskId", subtaskId);
            body.add("jar", new ByteArrayResource(taskRequest.getJarData().toByteArray()) {
                @Override
                public String getFilename() {
                    return "solver.jar";
                }
            });
            body.add("baseData", new ByteArrayResource(taskRequest.getBaseData().toByteArray()) {
                @Override
                public String getFilename() {
                    return "baseData.dat";
                }
            });
            body.add("subTaskData", new ByteArrayResource(taskRequest.getSubTaskData().toByteArray()) {
                @Override
                public String getFilename() {
                    return "subTaskData.dat";
                }
            });
            body.add("managerAddress", "http://localhost:8083");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            restTemplate.exchange(workerUrl + "/solveSubtask", HttpMethod.POST, requestEntity, String.class);

            distributorService.assignSubtaskToWorker(subtaskId, workerId);
            distributorService.setWorkerBusy(workerId, true);
            System.out.println("[DISTRIBUTOR] Подзадача " + subtaskId + " отправлена воркеру: " + workerUrl);
        } catch (Exception e) {
            System.err.println("[DISTRIBUTOR] Ошибка при отправке подзадачи воркеру: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void sendResult(ResultResponse request, StreamObserver<Empty> responseObserver) {
        try {
            distributorService.receiveResult(request);
            System.out.println("[DISTRIBUTOR] Результат для подзадачи " + request.getSubtaskId() + " получен и сохранён");
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception e) {
            System.err.println("[DISTRIBUTOR-GRPC] Ошибка при обработке результата: " + e.getMessage());
            e.printStackTrace();
            responseObserver.onError(e);
        }
    }
}