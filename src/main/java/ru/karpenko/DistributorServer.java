package ru.karpenko;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;

public class DistributorServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        Server server = ServerBuilder.forPort(8082)
                .addService(new DistributorService())
                .build();
        server.start();
        System.out.println("Распределитель запущен на порту 8082");
        server.awaitTermination();
    }
}
