package ru.karpenko.model;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;

public class BatchResult {
    private int startCombination;
    private byte[] resultData;

    public BatchResult(int startCombination, byte[] resultData) {
        this.startCombination = startCombination;
        this.resultData = resultData;
    }

    public int getStartCombination() {
        return startCombination;
    }

    public byte[] getResultData() {
        return resultData;
    }
}
