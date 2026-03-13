package model;

public class Employee {

    public int empId;
    public String empNo;
    public String firstName;
    public String middleName;
    public String lastName;

    public String address;
    public String mobile; // legacy personal contact

    // Contact + emails (current)
    public String personalContactNo;
    public String companyContactNo;
    public String personalEmail;
    public String companyEmail;

    // NEW: government/HR info
    public String dateOfBirth;     // stored as text (e.g. 1999-02-01) to keep DB/simple
    public String civilStatus;     // Single/Married/Widowed/Separated
    public String philHealthNo;
    public String sssNo;
    public String tinNo;
    public String pagIbigNo;

    public String position;
    public String payType;    // DAILY / MONTHLY / HOURLY
    public double rate;       // daily or monthly depending on payType
    public double hourlyRate; // optional column (for HOURLY)

    // ✅ Employee-specific holiday day rates (PHP/day - 8h). Optional.
    public double regularHolidayRate;
    public double specialHolidayRate;
    public String status;     // ACTIVE / INACTIVE

    /** Full name helper used across UI */
    public String fullName() {
        String f = firstName == null ? "" : firstName.trim();
        String m = middleName == null ? "" : middleName.trim();
        String l = lastName == null ? "" : lastName.trim();
        String mid = m.isEmpty() ? "" : (" " + m + ".");
        return (f + mid + " " + l).trim().replaceAll("\s+", " ");
    }

    // ---- getters (keep for existing code) ----
    public int getEmpId() { return empId; }
    public String getEmpNo() { return empNo; }
    public String getFirstName() { return firstName; }
    public String getMiddleName() { return middleName; }
    public String getLastName() { return lastName; }
    public String getAddress() { return address; }
    public String getMobile() { return mobile; }

    public String getPersonalContactNo() { return personalContactNo; }
    public String getCompanyContactNo() { return companyContactNo; }
    public String getPersonalEmail() { return personalEmail; }
    public String getCompanyEmail() { return companyEmail; }

    public String getDateOfBirth() { return dateOfBirth; }
    public String getCivilStatus() { return civilStatus; }
    public String getPhilHealthNo() { return philHealthNo; }
    public String getSssNo() { return sssNo; }
    public String getTinNo() { return tinNo; }
    public String getPagIbigNo() { return pagIbigNo; }

    public String getPosition() { return position; }
    public String getPayType() { return payType; }
    public double getRate() { return rate; }
    public double getHourlyRate() { return hourlyRate; }
    public double getRegularHolidayRate() { return regularHolidayRate; }
    public double getSpecialHolidayRate() { return specialHolidayRate; }
    public String getStatus() { return status; }
}
