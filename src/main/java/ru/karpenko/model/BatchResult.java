package ru.karpenko.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

public class BatchResult implements Serializable {
    private final List<Integer> path;
    private final int cost;
    private final String taskId;

    @JsonCreator
    public BatchResult(
            @JsonProperty("path") List<Integer> path,
            @JsonProperty("cost") int cost,
            @JsonProperty("taskId") String taskId) {
        this.path = path;
        this.cost = cost;
        this.taskId = taskId;
    }

    public List<Integer> getPath() {
        return path;
    }

    public int getCost() {
        return cost;
    }

    public String getTaskId() {
        return taskId;
    }

    @Override
    public String toString() {
        return "BatchResult{" +
                "path=" + path +
                ", cost=" + cost +
                ", taskId=" + taskId +
                '}';
    }
}