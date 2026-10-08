package com.mullagaliev.aqpbalancer.model;

/**
 * Модель вычислительного сервера.
 * Хранит текущее состояние (очередь), не выполняет запросы по-настоящему.
 */
public class BackendServer {
    private String id;
    private int queueSize;

    public BackendServer(String id, int queueSize) {
        this.id = id;
        this.queueSize = Math.max(0, queueSize);
    }

    public String getId() { return id; }
    public int getQueueSize() { return queueSize; }
    public void onRequestDispatched() { queueSize++; }
    public void onRequestCompleted() {
        if (queueSize > 0) {
            queueSize--;
        }
    }

    public double loadScore() {
        return queueSize;
    }

    @Override
    public String toString() {
        return "BackendServer[" + id + ", queue=" + queueSize + "]";
    }
}
