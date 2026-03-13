package model;

/**
 * Simple data carrier for a computed payslip.
 *
 * Notes:
 *  - Public fields to match your current style and make PDFUtil/Preview easy.
 */
public class Payslip {

    public int payslipId;
    public int runId;

    public Employee emp;

    public String periodStart;
    public String periodEnd;

    // Rates
    public double dailyRate;
    public double hourlyRate;

    public double otMultiplier;
    public double otRateUsed;

    
    // ✅ UI toggle: whether OT pay is enabled (controls PDF rows)
    public boolean otEnabled;

// ✅ Holiday day rates (what you want to show in preview + green tab + table column)
    public double regularHolidayDayRate;
    public double specialHolidayDayRate;

    // Hours breakdown (decimal hours)
    public double regularDayHours;
    public double regularDayOtHours;

    public double regularHolidayHours;
    public double regularHolidayOtHours;

    public double specialHolidayHours;
    public double specialHolidayOtHours;

    // Totals shown in account summary
    public double totalHours;     // regular + holiday non-OT (if you want) OR regular only (your UI decides)
    public double totalOtHours;

    // Earnings
    public double basicPay;

    // ✅ Incentives / bonuses (added to gross pay)
    public double incentives;

    // ✅ OT breakdown (so PDF can show holiday OT amount WITHOUT double counting)
    public double regularDayOtPay;
    public double regularHolidayOtPay;
    public double specialHolidayOtPay;

    public double overtimePay;

    // ✅ Holiday pays (full holiday pay amounts based on holiday day rate)
    public double regularHolidayPay;
    public double specialHolidayPay;

    // Summary
    public double holidayPremiumPay;        // = regularHolidayPay + specialHolidayPay
    public double grossPay;

    // Deductions total
    public double deductions;

    // ✅ Deductions breakdown + note (for PDF)
    public double cashAdvanceDeduction;
    public double smartBillingDeduction;
    public double otherDeduction;
    public String deductionNote;

    // ✅ Statutory deductions (employee share)
    public double sssDeduction;
    public double pagibigDeduction;
    public double philhealthDeduction;

    public double netPay;
}
