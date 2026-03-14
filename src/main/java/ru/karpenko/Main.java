package ru.karpenko;

import io.grpc.Server;
import io.grpc.ServerBuilder;

public class Main
{
    public static void main(String[] args) throws Exception{
        Server server = ServerBuilder
                .forPort(8081)
                .addService(new MyGridService())
                .build();

        server.start();
        System.out.println("Формирователь запущен на порту 8081");
        server.awaitTermination();
    }
}