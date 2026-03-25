package ru.karpenko;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
/*
//@SpringBootApplication
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }

    //@Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    //@Bean
    public CommandLineRunner registerWorker(ServletWebServerApplicationContext context) {
        return args -> {
            int port = context.getWebServer().getPort();
            String workerUrl = "http://localhost:" + port + "/worker";
            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.postForObject(
                    "http://localhost:8083/distributor/registerWorker",
                    workerUrl,
                    String.class
            );
            System.out.println("Регистрация воркера: " + response);
        };
    }

}*/
