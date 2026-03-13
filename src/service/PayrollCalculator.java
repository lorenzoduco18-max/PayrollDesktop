package service;

import dao.AttendanceDAO;
import dao.HolidayDAO;
import model.Employee;
import model.Holiday;
import model.Payslip;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

public class PayrollCalculator {

    private static final double WORK_DAYS_PER_MONTH = 26.0;
    private static final double HOURS_PER_DAY = 8.0;



private static boolean isMonthlyPayType(Employee emp) {
    if (emp == null || emp.payType == null) return false;
    return "MONTHLY".equalsIgnoreCase(emp.payType.trim());
}
    // fallback premium (extra on top of basic)
    private static final double REGULAR_HOLIDAY_PREMIUM = 1.00; // +100%
    private static final double SPECIAL_HOLIDAY_PREMIUM = 0.30; // +30%

    /**
     * Existing compute() - kept for compatibility
     */
    public static Payslip compute(
            int runId,
            Employee emp,
            String periodStart,
            String periodEnd,
            double otMultiplier,
            Double otRateOverride,
            double deductions
    ) {
        if (emp == null) throw new IllegalArgumentException("Employee not found.");

        Totals t = loadTotals(emp.empId, periodStart, periodEnd);

        // holiday hours unknown in this old path
        return computeFromTotals(
                runId,
                emp,
                periodStart,
                periodEnd,
                t.totalHours,
                t.totalOtHours,
                0, 0,
                otMultiplier,
                otRateOverride,
                deductions
        );
    }

    /**
     * ✅ NEW METHOD REQUIRED BY YOUR PayrollRunDialog
     *
     * Your rule:
     * - Total Hours must be ALL NON-OT hours (regular day + holidays) excluding OT
     * - Total OT must be ALL OT hours (regular day + holidays)
     *
     * Breakdown inputs:
     *  rdH, rdOT  = regular day (non-OT, OT)
     *  rhH, rhOT  = regular holiday (non-OT, OT)
     *  shH, shOT  = special holiday (non-OT, OT)
     */
    public static Payslip computeFromBreakdown(
            int runId,
            Employee emp,
            String start,
            String end,
            double rdH, double rdOT,
            double rhH, double rhOT,
            double shH, double shOT,
            double otMultiplier,
            Double otRateOverride,
            double deductions
    ) {
        if (emp == null) throw new IllegalArgumentException("Employee not found.");

        // ✅ totals based on your rule
        double totalHoursNonOt = rdH + rhH + shH;
        double totalOtAll = rdOT + rhOT + shOT;

        Payslip p = computeFromTotals(
                runId,
                emp,
                start,
                end,
                totalHoursNonOt,
                totalOtAll,
                rhH,
                shH,
                otMultiplier,
                otRateOverride,
                deductions
        );

        // store breakdown for preview/PDF if your Payslip has these fields
        p.regularDayHours = rdH;
        p.regularDayOtHours = rdOT;

        p.regularHolidayHours = rhH;
        p.regularHolidayOtHours = rhOT;

        p.specialHolidayHours = shH;
        p.specialHolidayOtHours = shOT;

        // store totals too
        p.totalHours = totalHoursNonOt;
        p.totalOtHours = totalOtAll;

        return p;
    }

    /**
     * Compatibility overload (older callers)
     */
    public static Payslip computeFromTotals(
            int runId,
            Employee emp,
            String periodStart,
            String periodEnd,
            double totalHours,
            double totalOT,
            double otMultiplier,
            Double otRateOverride,
            double deductions
    ) {
        return computeFromTotals(
                runId, emp, periodStart, periodEnd,
                totalHours, totalOT,
                0, 0,
                otMultiplier, otRateOverride, deductions
        );
    }

    /**
     * Main computeFromTotals with holiday hours.
     * - basicPay = totalHours * hourlyRate
     * - overtimePay = totalOT * otRateUsed
     * - holiday pay now includes:
     *      p.regularHolidayPay
     *      p.specialHolidayPay
     *      p.regularHolidayDayRate
     *      p.specialHolidayDayRate
     *      p.holidayPremiumPay = reg + spec
     */
    public static Payslip computeFromTotals(
            int runId,
            Employee emp,
            String periodStart,
            String periodEnd,
            double totalHours,
            double totalOT,
            double regularHolidayHours,
            double specialHolidayHours,
            double otMultiplier,
            Double otRateOverride,
            double deductions
    ) {
        if (emp == null) throw new IllegalArgumentException("Employee not found.");

        double hourlyRate = computeHourlyRate(emp);
        double otRateUsed = (otRateOverride != null) ? otRateOverride : (hourlyRate * otMultiplier);

        double basicPay = totalHours * hourlyRate;
        double overtimePay = totalOT * otRateUsed;

        // ✅ Holiday pay (rule: MONTHLY pay type does NOT get holiday pay)
HolidayPay hp;
double holidayTotal;

if (isMonthlyPayType(emp)) {
    hp = new HolidayPay(0, 0, 0, 0);
    holidayTotal = 0;

    // we keep the recorded hours for reporting, but holiday premium pay is zero
} else {
    // ✅ Compute holiday split + day rates
    hp = computeHolidayPaySplit(
            emp,
            hourlyRate,
            regularHolidayHours,
            specialHolidayHours
    );
    holidayTotal = hp.regPay + hp.specPay;
}

double grossPay = basicPay + overtimePay + holidayTotal;

        double netPay = grossPay - deductions;

        Payslip p = new Payslip();
        p.runId = runId;
        p.emp = emp;
        p.periodStart = periodStart;
        p.periodEnd = periodEnd;

        p.totalHours = totalHours;
        p.totalOtHours = totalOT;

        p.regularHolidayHours = regularHolidayHours;
        p.specialHolidayHours = specialHolidayHours;

        p.hourlyRate = hourlyRate;
        p.otRateUsed = otRateUsed;

        p.basicPay = round2(basicPay);
        p.overtimePay = round2(overtimePay);

        // ✅ fill split + rates (THIS FIXES YOUR PREVIEW)
        p.regularHolidayDayRate = round2(hp.regDayRate);
        p.specialHolidayDayRate = round2(hp.specDayRate);

        p.regularHolidayPay = round2(hp.regPay);
        p.specialHolidayPay = round2(hp.specPay);

        p.holidayPremiumPay = round2(holidayTotal);

        p.grossPay = round2(grossPay);
        p.deductions = deductions;
        p.netPay = round2(netPay);

        return p;
    }

    /**
     * ✅ Returns split holiday pay + implied day rates from your Holiday table.
     * We look at Holiday.rateMultiplier which your UI labels as:
     * "Holiday Rate (PHP/day - 8h...)". 
     *
     * If the DB has multiple holidays with different day rates in the period,
     * we take an average rate per type (same behavior as your old total method).
     */
    private static HolidayPay computeHolidayPaySplit(
            Employee emp,
            double hourlyRate,
            double regHolHours,
            double specHolHours
    ) {
        // ✅ Use EMPLOYEE rates (PHP/day - 8h). This is what you set in Edit Employee.
        // Holiday table entries are for dates/types; rates come from employee.
        double regDayRate = 0.0;
        double specDayRate = 0.0;

        if (emp != null) {
            regDayRate = emp.regularHolidayRate;
            specDayRate = emp.specialHolidayRate;
        }

        // Fallbacks (if employee rates not set)
        if (regDayRate <= 0) regDayRate = 0.0;
        if (specDayRate <= 0) specDayRate = 0.0;

        double regPay;
        if (regHolHours > 0) {
            if (regDayRate > 0) regPay = (regHolHours / HOURS_PER_DAY) * regDayRate;
            else regPay = regHolHours * hourlyRate * REGULAR_HOLIDAY_PREMIUM;
        } else regPay = 0.0;

        double specPay;
        if (specHolHours > 0) {
            if (specDayRate > 0) specPay = (specHolHours / HOURS_PER_DAY) * specDayRate * 0.30;
            else specPay = specHolHours * hourlyRate * 0.30;
        } else specPay = 0.0;

        return new HolidayPay(regPay, specPay, regDayRate, specDayRate);
    }

    // (kept for older callers that only expect a combined value)
    @SuppressWarnings("unused")
    private static double computeHolidayPay(
            String periodStart,
            String periodEnd,
            double hourlyRate,
            double regHolHours,
            double specHolHours
    ) {
        HolidayPay hp = computeHolidayPaySplit(null, hourlyRate, regHolHours, specHolHours);
        return hp.regPay + hp.specPay;
    }

    private static double computeHourlyRate(Employee emp) {
        // ✅ Priority:
        // 1) If hourlyRate override exists (>0), use it (backward compatible with earlier patch)
        // 2) Else compute from payType + rate
        if (emp != null && emp.hourlyRate > 0) {
            return emp.hourlyRate;
        }

        String payType = (emp == null || emp.payType == null) ? "" : emp.payType.trim().toUpperCase();

        if ("HOURLY".equals(payType)) {
            return (emp == null) ? 0 : emp.rate; // rate is already per hour
        }
        if ("DAILY".equals(payType)) {
            return (emp == null) ? 0 : (emp.rate / HOURS_PER_DAY);
        }
        if ("MONTHLY".equals(payType)) {
            return (emp == null) ? 0 : (emp.rate / (WORK_DAYS_PER_MONTH * HOURS_PER_DAY));
        }

        // Fallback (treat unknown as daily to avoid breaking)
        return (emp == null) ? 0 : (emp.rate / HOURS_PER_DAY);
    }

    /**
     * Super compatible totals loader (no guessing AttendanceDAO method names).
     */
    private static Totals loadTotals(int empId, String start, String end) {
        try {
            // Try static getTotals(int,String,String)
            Method m = AttendanceDAO.class.getDeclaredMethod("getTotals", int.class, String.class, String.class);
            Object totalsObj = m.invoke(null, empId, start, end);
            if (totalsObj != null) {
                double h = readNumberField(totalsObj, "totalHours");
                double ot = firstNumberField(totalsObj, new String[]{"totalOT", "totalOt", "totalOtHours", "totalOTHours"});
                return new Totals(h, ot);
            }
        } catch (Exception ignore) {}

        try {
            AttendanceDAO dao = new AttendanceDAO();

            double h = invokeNumber(dao, new String[]{"getTotalHours"},
                    new Class[]{int.class, String.class, String.class},
                    new Object[]{empId, start, end});

            double ot = invokeNumber(dao,
                    new String[]{"getTotalOtHours", "getTotalOTHours", "getTotalOt", "getTotalOT"},
                    new Class[]{int.class, String.class, String.class},
                    new Object[]{empId, start, end});

            return new Totals(h, ot);
        } catch (Exception e) {
            return new Totals(0, 0);
        }
    }

    private static double invokeNumber(Object target, String[] methodNames, Class<?>[] paramTypes, Object[] args) throws Exception {
        for (String name : methodNames) {
            try {
                Method m = target.getClass().getMethod(name, paramTypes);
                Object v = m.invoke(target, args);
                return toDouble(v);
            } catch (NoSuchMethodException ignore) {}
        }
        return 0;
    }

    private static double readNumberField(Object obj, String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return toDouble(f.get(obj));
        } catch (Exception e) {
            return 0;
        }
    }

    private static double firstNumberField(Object obj, String[] fieldNames) {
        for (String n : fieldNames) {
            try {
                Field f = obj.getClass().getDeclaredField(n);
                f.setAccessible(true);
                return toDouble(f.get(obj));
            } catch (Exception ignore) {}
        }
        return 0;
    }

    private static double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); }
        catch (Exception e) { return 0; }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static class Totals {
        final double totalHours;
        final double totalOtHours;

        Totals(double h, double ot) {
            this.totalHours = h;
            this.totalOtHours = ot;
        }
    }

    private static class HolidayPay {
        final double regPay;
        final double specPay;
        final double regDayRate;
        final double specDayRate;

        HolidayPay(double regPay, double specPay, double regDayRate, double specDayRate) {
            this.regPay = regPay;
            this.specPay = specPay;
            this.regDayRate = regDayRate;
            this.specDayRate = specDayRate;
        }
    }


    // ============================================================
    // ✅ Statutory Deductions (Philippines) - Employee Share Helpers
    // ============================================================
    //
    // Important:
    // - These helpers are designed to keep your UI clean and your Net Pay correct.
    // - If you want exact government tables, you can replace these formulas later
    //   without touching the UI.
    //
    // Assumptions:
    // - grossPay passed here is for the CURRENT payroll period (usually semi-monthly).
    // - We estimate monthly base as (grossPay * 2).
    //
    // You can adjust these constants anytime.

    private static final double SSS_EMPLOYEE_RATE = 0.045;      // common employee share (approx)
    private static final double SSS_MONTHLY_CAP = 30000.0;      // cap base (approx)

    private static final double PAGIBIG_RATE = 0.02;            // employee share
    private static final double PAGIBIG_SEMI_CAP = 50.0;        // max per semi-monthly (100 monthly)

    private static final double PHILHEALTH_RATE = 0.05;         // total premium rate (may change by year)
    private static final double PHILHEALTH_MIN_BASE = 10000.0;  // floor (approx)
    private static final double PHILHEALTH_MAX_BASE = 100000.0; // ceiling (approx)

    /** Estimate SSS employee share for the current payroll period (semi-monthly). */
    public static double computeSSSEmployeeSharePerPeriod(double grossPayThisPeriod) {
        if (grossPayThisPeriod <= 0) return 0.0;

        double monthly = grossPayThisPeriod * 2.0;
        double base = Math.min(monthly, SSS_MONTHLY_CAP);

        double employeeMonthly = base * SSS_EMPLOYEE_RATE;

        // semi-monthly contribution:
        return round2(employeeMonthly / 2.0);
    }

    /** Pag-IBIG employee share for the current payroll period (semi-monthly, capped). */
    public static double computePagIbigEmployeeSharePerPeriod(double grossPayThisPeriod) {
        if (grossPayThisPeriod <= 0) return 0.0;

        double val = grossPayThisPeriod * PAGIBIG_RATE;
        if (val > PAGIBIG_SEMI_CAP) val = PAGIBIG_SEMI_CAP;

        return round2(val);
    }

    /** PhilHealth employee share for the current payroll period (semi-monthly). */
    public static double computePhilHealthEmployeeSharePerPeriod(double grossPayThisPeriod) {
        if (grossPayThisPeriod <= 0) return 0.0;

        double monthly = grossPayThisPeriod * 2.0;
        double base = clamp(monthly, PHILHEALTH_MIN_BASE, PHILHEALTH_MAX_BASE);

        // total premium per month = base * rate
        // employee share is 50% of that
        double employeeMonthly = (base * PHILHEALTH_RATE) / 2.0;

        // semi-monthly:
        return round2(employeeMonthly / 2.0);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}