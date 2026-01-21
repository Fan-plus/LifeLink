package com.example.lifelink.data.health;

public class HealthData {
    public long id;
    public long timestamp; // epoch millis
    public int heartRate;
    public int bpSys;
    public int bpDia;
    public int spo2;

    public HealthData(long id, long timestamp, int heartRate, int bpSys, int bpDia, int spo2) {
        this.id = id;
        this.timestamp = timestamp;
        this.heartRate = heartRate;
        this.bpSys = bpSys;
        this.bpDia = bpDia;
        this.spo2 = spo2;
    }
}
