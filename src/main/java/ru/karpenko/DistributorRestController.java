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
    public ResponseEntity<String> taskAccepted(@PathVariable String subtaskId) {
        System.out.println("[DISTRIBUTOR] Подзадача №" + subtaskId + " воркером принята");
        return ResponseEntity.ok("Подзадача №" + subtaskId + " принята");
    }

    @PostMapping(value = "/result/{subtaskId}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<String> receiveResult(@PathVariable String subtaskId, @RequestBody byte[] result) {
        try {
            System.out.println("[DISTRIBUTOR] Результат получен для подзадачи: " + subtaskId);
            distributorService.receiveResult(subtaskId, result);
            return ResponseEntity.ok("Результат для подзадачи №" + subtaskId + " получен и сохранён");
        } catch (Exception e) {
            System.err.println("[DISTRIBUTOR] Ошибка при обработке результата: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Ошибка при обработке результата: " + e.getMessage());
        }
    }
}