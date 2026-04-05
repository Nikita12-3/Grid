package ru.karpenko;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Service
public class DistributorService {
    private final Queue<DistributorTaskRequest> taskQueue = new LinkedList<>();
    private final Map<String, WorkerInfo> workers = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> countDownLatches = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    public String registerWorker(String workerUrl) {
        String workerId = UUID.randomUUID().toString();
        WorkerInfo worker = new WorkerInfo(workerId, workerUrl);
        workers.put(workerId, worker);
        System.out.println("[DISTRIBUTOR] Воркер зарегистрирован: " + worker);
        return workerId;
    }

    public void addTask(DistributorTaskRequest taskRequest) {
        String taskId = taskRequest.getTaskId();
        String subtaskId = taskId + "_" + UUID.randomUUID().toString();
        System.out.println("[DISTRIBUTOR] Добавлена подзадача: " + subtaskId);
        taskQueue.add(taskRequest);
        countDownLatches.put(subtaskId, new CountDownLatch(1));
        System.out.println("[DISTRIBUTOR] Инициализирован CountDownLatch для подзадачи: " + subtaskId);
        distributeTasks();
    }

    private void distributeTasks() {
        while (!taskQueue.isEmpty() && workers.values().stream().anyMatch(w -> !w.isBusy())) {
            DistributorTaskRequest taskRequest = taskQueue.peek();
            WorkerInfo freeWorker = findFreeWorker();
            if (freeWorker != null) {
                sendTaskToWorker(taskRequest, freeWorker);
                taskQueue.poll();
            } else {
                System.out.println("[DISTRIBUTOR] Нет свободных воркеров. Подзадача добавлена в очередь.");
                break;
            }
        }
    }

    public WorkerInfo findFreeWorker() {
        return workers.values().stream()
                .filter(w -> !w.isBusy())
                .findFirst()
                .orElse(null);
    }

    private void sendTaskToWorker(DistributorTaskRequest taskRequest, WorkerInfo worker) {
        worker.setBusy(true);
        String subtaskId = taskRequest.getTaskId() + "_" + UUID.randomUUID().toString();
        System.out.println("[DISTRIBUTOR] Подзадача " + subtaskId + " отправлена воркеру: " + worker.getWorkerUrl());
        executor.submit(() -> processTask(taskRequest, worker, subtaskId));
    }

    private void processTask(DistributorTaskRequest taskRequest, WorkerInfo worker, String subtaskId) {
        System.out.println("[WORKER " + worker.getId() + "] Получена подзадача: " + subtaskId);
        // Симуляция обработки подзадачи
        ResultResponse result = ResultResponse.newBuilder()
                .setTaskId(taskRequest.getTaskId())
                .setSubtaskId(subtaskId)
                .setWorkerId(worker.getId())
                .setResultData(com.google.protobuf.ByteString.copyFromUtf8("Processed: " + taskRequest.getSubTaskData().toStringUtf8()))
                .build();
        receiveResult(result);
    }

    public void receiveResult(ResultResponse result) {
        System.out.println("[DISTRIBUTOR] Результат получен для подзадачи: " + result.getSubtaskId());
        System.out.println("[DISTRIBUTOR] Задача: " + result.getTaskId() + ", Воркер: " + result.getWorkerId());
        if (countDownLatches.containsKey(result.getSubtaskId())) {
            countDownLatches.get(result.getSubtaskId()).countDown();
            if (countDownLatches.get(result.getSubtaskId()).getCount() == 0) {
                countDownLatches.remove(result.getSubtaskId());
                System.out.println("[DISTRIBUTOR] Все подзадачи задачи " + result.getTaskId() + " выполнены");
            }
        }
        markWorkerAsFree(result.getWorkerId());
        distributeTasks();
    }

    private void markWorkerAsFree(String workerId) {
        if (workers.containsKey(workerId)) {
            workers.get(workerId).setBusy(false);
            System.out.println("[DISTRIBUTOR] Статус воркера " + workerId + " изменён на свободен");
        }
    }

    public void assignSubtaskToWorker(String subtaskId, String workerId) {
        System.out.println("[DISTRIBUTOR] Подзадача " + subtaskId + " назначена воркеру: " + workerId);
    }

    public void setWorkerBusy(String workerId, boolean busy) {
        if (workers.containsKey(workerId)) {
            workers.get(workerId).setBusy(busy);
        }
    }
}