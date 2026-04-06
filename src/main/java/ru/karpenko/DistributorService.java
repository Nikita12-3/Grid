package ru.karpenko;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
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
    private final Set<String> processedSubtasks = new HashSet<>();
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
        taskRequest = taskRequest.toBuilder().setSubtaskId(subtaskId).build(); // Устанавливаем subtaskId
        taskQueue.add(taskRequest);
        countDownLatches.put(subtaskId, new CountDownLatch(1)); // Инициализируем CountDownLatch для подзадачи
        System.out.println("[DISTRIBUTOR] Инициализирован CountDownLatch для подзадачи: " + subtaskId);
        distributeTasks();
    }

    private void distributeTasks() {
        while (!taskQueue.isEmpty()) {
            WorkerInfo freeWorker = findFreeWorker();
            if (freeWorker != null) {
                DistributorTaskRequest taskRequest = taskQueue.poll();
                if (taskRequest != null) {
                    String subtaskId = taskRequest.getSubtaskId(); // Используем уже установленный subtaskId
                    if (processedSubtasks.contains(subtaskId)) {
                        System.out.println("[DISTRIBUTOR] Подзадача " + subtaskId + " уже была обработана");
                        continue;
                    }
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
        if (processedSubtasks.contains(subtaskId)) {
            System.out.println("[DISTRIBUTOR] Подзадача " + subtaskId + " уже была обработана");
            return;
        }

        worker.setBusy(true);
        subtaskWorkerMap.put(subtaskId, worker.getId());
        countDownLatches.put(subtaskId, new CountDownLatch(1));
        processedSubtasks.add(subtaskId); // Добавляем подзадачу в множество обработанных

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
            worker.setBusy(false);
            subtaskWorkerMap.remove(subtaskId);
            countDownLatches.remove(subtaskId);
            processedSubtasks.remove(subtaskId); // Удаляем подзадачу из множества обработанных
        }
    }

    public void receiveResult(String subtaskId, byte[] result) {
        System.out.println("[DISTRIBUTOR] Результат получен для подзадачи: " + subtaskId + ", размер результата: " + result.length);
        results.put(subtaskId, result);

        if (countDownLatches.containsKey(subtaskId)) {
            System.out.println("[DISTRIBUTOR] CountDownLatch найден для подзадачи: " + subtaskId);
            long countBefore = countDownLatches.get(subtaskId).getCount();
            System.out.println("[DISTRIBUTOR] Текущий счетчик CountDownLatch до уменьшения: " + countBefore);
            countDownLatches.get(subtaskId).countDown();
            long countAfter = countDownLatches.get(subtaskId).getCount();
            System.out.println("[DISTRIBUTOR] Текущий счетчик CountDownLatch после уменьшения: " + countAfter);

            if (countAfter == 0) {
                countDownLatches.remove(subtaskId);
                processedSubtasks.add(subtaskId); // Добавляем в множество обработанных
                System.out.println("[DISTRIBUTOR] Подзадача " + subtaskId + " выполнена");

                String taskId = subtaskId.split("_")[0];

                // Проверяем, выполнены ли все подзадачи для данной задачи
                boolean allSubtasksCompleted = true;
                for (Map.Entry<String, byte[]> entry : results.entrySet()) {
                    if (entry.getKey().startsWith(taskId + "_")) {
                        if (!processedSubtasks.contains(entry.getKey())) {
                            System.out.println("[DISTRIBUTOR] Подзадача " + entry.getKey() + " ещё не выполнена");
                            allSubtasksCompleted = false;
                            break;
                        }
                    }
                }

                if (allSubtasksCompleted) {
                    System.out.println("[DISTRIBUTOR] Все подзадачи для задачи " + taskId + " выполнены");
                    sendResultsToFormatter(taskId);
                }
            }
        } else {
            System.err.println("[DISTRIBUTOR] CountDownLatch не найден для подзадачи: " + subtaskId);
        }

        markWorkerAsFree(getWorkerIdBySubtask(subtaskId));
        distributeTasks();
    }

    private void sendResultsToFormatter(String taskId) {
        List<byte[]> taskResults = new ArrayList<>();

        // Собираем все результаты для данной задачи
        for (Map.Entry<String, byte[]> entry : results.entrySet()) {
            if (entry.getKey().startsWith(taskId + "_")) {
                taskResults.add(entry.getValue());
            }
        }

        if (!taskResults.isEmpty()) {
            try {
                // Сериализуем результаты в один массив байтов
                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                try (ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
                    objectStream.writeObject(taskResults);
                    objectStream.flush();
                }
                byte[] serializedResults = byteStream.toByteArray();

                // Логируем размер сериализованных данных
                System.out.println("[DISTRIBUTOR] Сериализованные результаты для задачи " + taskId + ", размер: " + serializedResults.length);

                // Проверяем, что массив не пустой
                if (serializedResults.length == 0) {
                    System.err.println("[DISTRIBUTOR] Сериализованные результаты пустые!");
                    return;
                }

                // Создаем канал для связи с формирователем
                ManagedChannel channel = ManagedChannelBuilder
                        .forTarget("localhost:8081")
                        .usePlaintext()
                        .build();

                // Создаем stub для отправки результатов
                GridServiceGrpc.GridServiceBlockingStub formatterStub = GridServiceGrpc.newBlockingStub(channel);

                // Отправляем результат на формирователь
                formatterStub.sendResult(ResultResponse.newBuilder()
                        .setTaskId(taskId)
                        .setResultData(ByteString.copyFrom(serializedResults))
                        .build());

                System.out.println("[DISTRIBUTOR] Результаты для задачи " + taskId + " успешно отправлены на формирователь");
                channel.shutdown();
            } catch (IOException e) {
                System.err.println("[DISTRIBUTOR] Ошибка при сериализации результатов: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("[DISTRIBUTOR] Ошибка при отправке результатов на формирователь: " + e.getMessage());
            }
        } else {
            System.err.println("[DISTRIBUTOR] Нет результатов для задачи " + taskId);
        }
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