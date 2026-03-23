package ru.karpenko;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class DistributorService {
    private final Map<String, WorkerInfo> workers = new ConcurrentHashMap<>();
    private final Map<String, byte[]> results = new HashMap<>();
    private final BlockingQueue<DistributorTaskRequest> taskQueue = new LinkedBlockingQueue<>();

    // Регистрация нового воркера
    public String registerWorker(String workerUrl) {
        String workerId = UUID.randomUUID().toString();
        WorkerInfo workerInfo = new WorkerInfo(workerId, workerUrl);
        workers.put(workerId, workerInfo);
        System.out.println("[DISTRIBUTOR] Воркер зарегистрирован: " + workerInfo);
        return workerId;
    }

    // Поиск свободного воркера
    public WorkerInfo findFreeWorker() {
        return workers.values().stream()
                .filter(worker -> !worker.isBusy())
                .findFirst()
                .orElse(null);
    }

    // Установка статуса воркера
    public void setWorkerBusy(String workerId, boolean isBusy) {
        WorkerInfo workerInfo = workers.get(workerId);
        if (workerInfo != null) {
            workerInfo.setBusy(isBusy);
            System.out.println("[DISTRIBUTOR] Статус воркера " + workerId + " изменён на " + (isBusy ? "занят" : "свободен"));
        } else {
            System.err.println("[DISTRIBUTOR] Воркер с ID " + workerId + " не найден!");
        }
    }

    // Освобождение воркера
    public void freeWorker(String workerId) {
        setWorkerBusy(workerId, false);
        System.out.println("[DISTRIBUTOR] Воркер освобождён: " + workerId);
    }

    // Сохранение результата задачи
    public void saveResult(String taskId, byte[] result) {
        results.put(taskId, result);
        System.out.println("[DISTRIBUTOR] Результат для задачи " + taskId + " сохранён");
    }

    // Получение результата задачи
    public byte[] getResult(String taskId) {
        return results.get(taskId);
    }

    // Вывод всех зарегистрированных воркеров (для отладки)
    public void printAllWorkers() {
        System.out.println("[DISTRIBUTOR] Список зарегистрированных воркеров:");
        workers.values().forEach(System.out::println);
    }
    public void addTaskToQueue(DistributorTaskRequest request) {
        taskQueue.add(request);
        System.out.println("[DISTRIBUTOR] Задача добавлена в очередь: " + request.getTaskId());
    }

    public DistributorTaskRequest pollTaskFromQueue() throws InterruptedException {
        return taskQueue.poll();
    }
}
