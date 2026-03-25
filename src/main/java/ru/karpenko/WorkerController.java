package ru.karpenko;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

//@RestController
//@RequestMapping("/worker")
public class WorkerController {
    @Autowired
    private WorkerService workerService;

    @PostMapping("/solveSubtask")
    public String solveSubtask(
            @RequestParam String taskId,
            @RequestParam MultipartFile jar,
            @RequestParam MultipartFile baseData,
            @RequestParam MultipartFile subTaskData,
            @RequestParam String managerAddress
    ) {
        // Отправляем подтверждение о принятии задачи
        workerService.sendTaskAccepted(taskId, managerAddress);

        // Запускаем обработку задачи в отдельном потоке
        MyThread thread = new MyThread(
                taskId, jar, baseData, subTaskData, managerAddress, workerService
        );
        thread.start();

        return "Задача " + taskId + " принята в обработку";
    }
}
