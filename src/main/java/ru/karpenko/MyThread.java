package ru.karpenko;

import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

public class MyThread extends Thread {
    private final String taskId;
    private final MultipartFile jar;
    private final MultipartFile baseData;
    private final MultipartFile subTaskData;
    private final String managerAddress;
    private final WorkerService workerService;

    public MyThread(
            String taskId,
            MultipartFile jar,
            MultipartFile baseData,
            MultipartFile subTaskData,
            String managerAddress,
            WorkerService workerService
    ) {
        this.taskId = taskId;
        this.jar = jar;
        this.baseData = baseData;
        this.subTaskData = subTaskData;
        this.managerAddress = managerAddress;
        this.workerService = workerService;
    }

    //@Override
    public void run() {
        try {
            System.out.println("[WORKER-THREAD-" + taskId + "] Начало выполнения задачи");
            byte[] result = workerService.solveSubtask(taskId, jar, baseData, subTaskData, managerAddress);
            System.out.println("[WORKER-THREAD-" + Arrays.toString(result) + "] Результат");
            workerService.sendResult(taskId, result, managerAddress);
            System.out.println("[WORKER-THREAD-" + taskId + "] Задача выполнена успешно");
        } catch (Exception e) {
            System.err.println("[WORKER-THREAD-" + taskId + "] Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
