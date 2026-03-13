package model;

public class AttendanceSummary {
    private int daysWorked;
    private long totalMinutes;
    private long otMinutes;

    public AttendanceSummary() {}

    public AttendanceSummary(int daysWorked, long totalMinutes, long otMinutes) {
        this.daysWorked = daysWorked;
        this.totalMinutes = totalMinutes;
        this.otMinutes = otMinutes;
    }

    public int getDaysWorked() { return daysWorked; }
    public long getTotalMinutes() { return totalMinutes; }
    public long getOtMinutes() { return otMinutes; }

    public long getTotalHoursPart() { return totalMinutes / 60; }
    public long getTotalMinsPart()  { return totalMinutes % 60; }

    public long getOtHoursPart() { return otMinutes / 60; }
    public long getOtMinsPart()  { return otMinutes % 60; }
}
