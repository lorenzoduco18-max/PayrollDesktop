package model;

import java.time.LocalDate;
import java.time.LocalTime;

public class TimeLogRecord {

    // ✅ DB primary key from time_logs.id
    private int logId;

    private int empId;
    private String empName;

    private LocalDate logDate;
    private LocalTime timeIn;
    private LocalTime lunchOut;
    private LocalTime lunchIn;
    private LocalTime timeOut;

    public TimeLogRecord() {}

    // ✅ New constructor with logId
    public TimeLogRecord(int logId, LocalDate logDate, int empId, String empName, LocalTime timeIn, LocalTime timeOut) {
        this(logId, logDate, empId, empName, timeIn, null, null, timeOut);
    }

    public TimeLogRecord(int logId, LocalDate logDate, int empId, String empName, LocalTime timeIn, LocalTime lunchOut, LocalTime lunchIn, LocalTime timeOut) {
        this.logId = logId;
        this.logDate = logDate;
        this.empId = empId;
        this.empName = empName;
        this.timeIn = timeIn;
        this.lunchOut = lunchOut;
        this.lunchIn = lunchIn;
        this.timeOut = timeOut;
    }

    // ✅ Keep your old constructor so other code won’t break
    public TimeLogRecord(LocalDate logDate, int empId, String empName, LocalTime timeIn, LocalTime timeOut) {
        this(0, logDate, empId, empName, timeIn, null, null, timeOut);
    }

    public int getLogId() { return logId; }
    public void setLogId(int logId) { this.logId = logId; }

    public int getEmpId() { return empId; }
    public void setEmpId(int empId) { this.empId = empId; }

    public String getEmpName() { return empName; }
    public void setEmpName(String empName) { this.empName = empName; }

    public LocalDate getLogDate() { return logDate; }
    public void setLogDate(LocalDate logDate) { this.logDate = logDate; }

    public LocalTime getTimeIn() { return timeIn; }
    public void setTimeIn(LocalTime timeIn) { this.timeIn = timeIn; }

    public LocalTime getLunchOut() { return lunchOut; }
    public void setLunchOut(LocalTime lunchOut) { this.lunchOut = lunchOut; }

    public LocalTime getLunchIn() { return lunchIn; }
    public void setLunchIn(LocalTime lunchIn) { this.lunchIn = lunchIn; }

    public LocalTime getTimeOut() { return timeOut; }
    public void setTimeOut(LocalTime timeOut) { this.timeOut = timeOut; }
}
