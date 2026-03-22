package com.example.lifelink.data.health;

public class HealthData {
    public long id;
    public long timestamp; 
    public int heartRate;
    public int bpSys;
    public int bpDia;
    public int spo2;
    public float gasLevel;
    public int steps;

    public HealthData(long id, long timestamp, int heartRate, int bpSys, int bpDia, int spo2, float gasLevel, int steps) {
        this.id = id;
        this.timestamp = timestamp;
        this.heartRate = heartRate;
        this.bpSys = bpSys;
        this.bpDia = bpDia;
        this.spo2 = spo2;
        this.gasLevel = gasLevel;
        this.steps = steps;
    }

    public long getId() { return id; }
    public long getTimestamp() { return timestamp; }
    public int getHeartRate() { return heartRate; }
    public int getBpSys() { return bpSys; }
    public int getBpDia() { return bpDia; }
    public int getSpo2() { return spo2; }
    public float getGasLevel() { return gasLevel; }
    public int getSteps() { return steps; }
}
