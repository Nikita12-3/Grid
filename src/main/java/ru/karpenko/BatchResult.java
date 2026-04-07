package ru.karpenko;

import java.util.List;

public class BatchResult {
    private List<Integer> path;
    private int cost;

    // Конструкторы
    public BatchResult() {}

    public BatchResult(List<Integer> path, int cost) {
        this.path = path;
        this.cost = cost;
    }

    // Геттеры и сеттеры
    public List<Integer> getPath() {
        return path;
    }

    public void setPath(List<Integer> path) {
        this.path = path;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    @Override
    public String toString() {
        return "BatchResult{" +
                "path=" + path +
                ", cost=" + cost +
                '}';
    }
}