package ru.karpenko;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/distributor")
public class DistributorRestController {
    private final DistributorService distributorService;

    public DistributorRestController(DistributorService distributorService) {
        this.distributorService = distributorService;
    }

    @PostMapping("/registerWorker")
    public ResponseEntity<String> registerWorker(@RequestBody String workerUrl) {
        String workerId = distributorService.registerWorker(workerUrl);
        return ResponseEntity.ok(workerId);
    }

    @PostMapping("/taskAccepted/{subtaskId}")
    public void taskAccepted(@PathVariable String subtaskId) {
        System.out.println("[DISTRIBUTOR] Подзадача №" + subtaskId + " воркером принята");
    }

    @PostMapping(value = "/result", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void receiveResult(@RequestBody ResultResponse result) {
        try {
            System.out.println("[DISTRIBUTOR] Результат получен для подзадачи: " + result.getSubtaskId());
            System.out.println("[DISTRIBUTOR] Задача: " + result.getTaskId() + ", Воркер: " + result.getWorkerId());
            distributorService.receiveResult(result);
            System.out.println("[DISTRIBUTOR] Результат для подзадачи №" + result.getSubtaskId() +
                    ", Задача: " + result.getTaskId() +
                    ", Воркер: " + result.getWorkerId() +
                    " получен и сохранён");
        } catch (Exception e) {
            System.err.println("[DISTRIBUTOR] Ошибка при обработке результата: " + e.getMessage());
            e.printStackTrace();
        }
    }
}