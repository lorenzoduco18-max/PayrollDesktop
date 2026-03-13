package model;

/**
 * Work summary for the whole pay period.
 *
 * RULES (as you requested):
 * - totalHours = ALL non-OT hours (regular day + holidays), EXCLUDING OT
 * - otHours    = ALL OT hours (regular day + holidays)
 *
 * NEW:
 * - regularDayHours, regularDayOtHours
 * - regularHolidayHours, regularHolidayOtHours
 * - specialHolidayHours, specialHolidayOtHours
 *
 * NOTE: old fields remain so nothing else breaks.
 */
public class WorkSummary {
    // Existing fields (keep)
    public double totalHours;            // non-OT total (regular + holiday)
    public double otHours;               // total OT (regular + holiday)
    public double regularHolidayHours;   // non-OT reg holiday
    public double specialHolidayHours;   // non-OT special holiday

    // New breakdown fields
    public double regularDayHours;        // non-OT regular day
    public double regularDayOtHours;      // OT on regular day

    public double regularHolidayOtHours;  // OT on regular holiday
    public double specialHolidayOtHours;  // OT on special holiday

    public WorkSummary() {}

    // Keep old constructor (compat)
    public WorkSummary(double totalHours, double otHours,
                       double regularHolidayHours, double specialHolidayHours) {
        this.totalHours = totalHours;
        this.otHours = otHours;
        this.regularHolidayHours = regularHolidayHours;
        this.specialHolidayHours = specialHolidayHours;

        // Best-effort defaults
        this.regularDayHours = Math.max(0, totalHours - regularHolidayHours - specialHolidayHours);
        this.regularDayOtHours = otHours;
        this.regularHolidayOtHours = 0;
        this.specialHolidayOtHours = 0;
    }

    // New full breakdown constructor
    public WorkSummary(
            double regularDayHours,
            double regularDayOtHours,
            double regularHolidayHours,
            double regularHolidayOtHours,
            double specialHolidayHours,
            double specialHolidayOtHours
    ) {
        this.regularDayHours = regularDayHours;
        this.regularDayOtHours = regularDayOtHours;

        this.regularHolidayHours = regularHolidayHours;
        this.regularHolidayOtHours = regularHolidayOtHours;

        this.specialHolidayHours = specialHolidayHours;
        this.specialHolidayOtHours = specialHolidayOtHours;

        // Totals follow your rule
        this.totalHours = regularDayHours + regularHolidayHours + specialHolidayHours;
        this.otHours = regularDayOtHours + regularHolidayOtHours + specialHolidayOtHours;
    }
}
