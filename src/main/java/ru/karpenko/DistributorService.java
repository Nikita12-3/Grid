package ru.karpenko;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.io.ByteArrayResource;

import java.util.*;
import java.util.concurrent.*;


import java.util.*;
import java.util.concurrent.*;

@Service
public class DistributorService {
    private final Queue<DistributorTaskRequest> taskQueue = new LinkedList<>();
    private final Map<String, WorkerInfo> workers = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> countDownLatches = new ConcurrentHashMap<>();
    private final Map<String, byte[]> results = new ConcurrentHashMap<>();
    private final Map<String, String> subtaskWorkerMap = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate;

    public DistributorService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String registerWorker(String workerUrl) {
        if (workerUrl == null || !workerUrl.startsWith("http://") && !workerUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Некорректный URL воркера: " + workerUrl);
        }

        String workerId = UUID.randomUUID().toString();
        WorkerInfo worker = new WorkerInfo(workerId, workerUrl);
        workers.put(workerId, worker);
        System.out.println("[DISTRIBUTOR] Воркер зарегистрирован: " + worker);
        distributeTasks();
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
        while (!taskQueue.isEmpty()) {
            WorkerInfo freeWorker = findFreeWorker();
            if (freeWorker != null) {
                DistributorTaskRequest taskRequest = taskQueue.poll(); // Удаляем задачу из очереди
                if (taskRequest != null) {
                    String subtaskId = taskRequest.getTaskId() + "_" + UUID.randomUUID().toString();
                    System.out.println("[DISTRIBUTOR] Подзадача " + subtaskId + " отправлена воркеру: " + freeWorker.getWorkerUrl());
                    sendTaskToWorker(taskRequest, freeWorker, subtaskId);
                }
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

    private void sendTaskToWorker(DistributorTaskRequest taskRequest, WorkerInfo worker, String subtaskId) {
        worker.setBusy(true);
        subtaskWorkerMap.put(subtaskId, worker.getId()); // Сохраняем маппинг subtaskId -> workerId
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
                    return "baseData.bin";
                }
            });
            body.add("subTaskData", new ByteArrayResource(taskRequest.getSubTaskData().toByteArray()) {
                @Override
                public String getFilename() {
                    return "subTaskData.bin";
                }
            });
            body.add("managerAddress", "http://localhost:8083");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            restTemplate.exchange(worker.getWorkerUrl() + "/solveSubtask", HttpMethod.POST, requestEntity, String.class);

            System.out.println("[DISTRIBUTOR] Подзадача " + subtaskId + " отправлена воркеру: " + worker.getWorkerUrl());
        } catch (Exception e) {
            System.err.println("[DISTRIBUTOR] Ошибка при отправке подзадачи воркеру: " + e.getMessage());
            e.printStackTrace();
            taskQueue.add(taskRequest); // Возвращаем задачу в очередь
            worker.setBusy(false);
            subtaskWorkerMap.remove(subtaskId); // Удаляем маппинг, если задача не была отправлена
        }
    }

    public void receiveResult(String subtaskId, byte[] result) {
        System.out.println("[DISTRIBUTOR] Результат получен для подзадачи: " + subtaskId);
        results.put(subtaskId, result);
        if (countDownLatches.containsKey(subtaskId)) {
            countDownLatches.get(subtaskId).countDown();
            if (countDownLatches.get(subtaskId).getCount() == 0) {
                countDownLatches.remove(subtaskId);
                System.out.println("[DISTRIBUTOR] Все подзадачи задачи выполнены");
            }
        }
        markWorkerAsFree(getWorkerIdBySubtask(subtaskId));
        distributeTasks();
    }

    private String getWorkerIdBySubtask(String subtaskId) {
        return subtaskWorkerMap.get(subtaskId);
    }

    private void markWorkerAsFree(String workerId) {
        if (workers.containsKey(workerId)) {
            workers.get(workerId).setBusy(false);
            System.out.println("[DISTRIBUTOR] Статус воркера " + workerId + " изменён на свободен");
        }
    }
}