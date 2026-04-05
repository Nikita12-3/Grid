package ru.karpenko;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

@SpringBootApplication
public class DistributorServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        ConfigurableApplicationContext context = SpringApplication.run(DistributorServer.class, args);

        // Старт gRPC сервера
        Server grpcServer = ServerBuilder.forPort(8082)
                .addService(context.getBean(DistributorGrpcService.class))
                .build()
                .start();

        System.out.println("[DISTRIBUTOR] gRPC сервер запущен на порту 8082");
        System.out.println("[DISTRIBUTOR] REST сервер запущен на порту " + context.getEnvironment().getProperty("server.port", "8083"));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[DISTRIBUTOR] Остановка серверов...");
            grpcServer.shutdown();
            context.close();
        }));

        grpcServer.awaitTermination();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
