package ru.karpenko;

public class WorkerInfo {
    private final String id;
    private final String workerUrl;
    private boolean isBusy;

    public WorkerInfo(String id, String workerUrl) {
        this.id = id;
        this.workerUrl = workerUrl;
        this.isBusy = false;
    }

    public String getId() {
        return id;
    }

    public String getWorkerUrl() {
        return workerUrl;
    }

    public boolean isBusy() {
        return isBusy;
    }

    public void setBusy(boolean busy) {
        isBusy = busy;
    }

    @Override
    public String toString() {
        return "WorkerInfo{" +
                "id='" + id + '\'' +
                ", workerUrl='" + workerUrl + '\'' +
                ", isBusy=" + isBusy +
                '}';
    }
}
