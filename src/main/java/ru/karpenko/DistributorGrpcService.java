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
            distributorService.addTask(request);
            TaskResponse response = TaskResponse.newBuilder()
                    .setTaskId(taskId)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            System.err.println("[DISTRIBUTOR-GRPC] Ошибка: " + e.getMessage());
            e.printStackTrace();
            responseObserver.onError(e);
        }
    }

    @Override
    public void sendResult(ResultResponse request, StreamObserver<Empty> responseObserver) {
        try {
            distributorService.receiveResult(request.getSubtaskId(), request.getResultData().toByteArray());
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