package ru.karpenko;

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

    @PostMapping("/taskAccepted/{taskId}")
    public void taskAccepted(@PathVariable String taskId) {
        System.out.println("[DISTRIBUTOR] Подзадача №" + taskId + " воркером принята");
    }

    @PostMapping("/result/{taskId}")
    public void receiveResult(@PathVariable String taskId, @RequestBody byte[] result) {
        distributorService.saveResult(taskId, result);
        distributorService.freeWorker("http://localhost:" + System.getProperty("server.port"));
        System.out.println("[DISTRIBUTOR] Результат для задачи №" + taskId + " получен и сохранён");
    }
}
