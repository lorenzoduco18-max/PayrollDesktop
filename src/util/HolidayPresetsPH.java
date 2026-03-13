package util;

import dao.HolidayDAO;
import model.Holiday;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;

/**
 * PH Holiday presets (government-ish baseline).
 *
 * IMPORTANT:
 * - Some PH holidays are movable / proclamation-based (e.g., Eid, special days).
 * - This seeder inserts a baseline set. You can always add/adjust inside HolidayReviewDialog.
 *
 * Rate multiplier here means: EXTRA premium on top of basic pay.
 * Examples (common):
 *  - Regular holiday worked: +100% => 1.00
 *  - Special non-working worked: +30% => 0.30
 *  - Special working: policy-based (often +30%) => 0.30
 */
public class HolidayPresetsPH {

    private static final HolidayDAO dao = new HolidayDAO();

    /**
     * Seeds the given year if no holiday rows exist for that year.
     */
    public static void seedYearIfEmpty(int year) {
        try {
            LocalDate y1 = LocalDate.of(year, 1, 1);
            LocalDate y2 = LocalDate.of(year, 12, 31);

            // If already seeded (any rows exist), do nothing
            int existing = dao.countBetween(y1, y2);
            if (existing > 0) return;

            // ===== Fixed-date holidays (baseline) =====
            upsert(y1, "New Year's Day", "REGULAR", "NATIONAL", "Preset", rateFor("REGULAR"));

            upsert(LocalDate.of(year, 4, 9), "Araw ng Kagitingan", "REGULAR", "NATIONAL", "Preset", rateFor("REGULAR"));
            upsert(LocalDate.of(year, 5, 1), "Labor Day", "REGULAR", "NATIONAL", "Preset", rateFor("REGULAR"));
            upsert(LocalDate.of(year, 6, 12), "Independence Day", "REGULAR", "NATIONAL", "Preset", rateFor("REGULAR"));

            // Ninoy Aquino Day is commonly Special Non-Working
            upsert(LocalDate.of(year, 8, 21), "Ninoy Aquino Day", "SPECIAL_NON_WORKING", "NATIONAL", "Preset", rateFor("SPECIAL_NON_WORKING"));

            // National Heroes Day = last Monday of August (movable but rule-based)
            LocalDate heroes = LocalDate.of(year, Month.AUGUST, 1)
                    .with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY));
            upsert(heroes, "National Heroes Day", "REGULAR", "NATIONAL", "Preset", rateFor("REGULAR"));

            // All Saints' Day usually Special Non-Working
            upsert(LocalDate.of(year, 11, 1), "All Saints' Day", "SPECIAL_NON_WORKING", "NATIONAL", "Preset", rateFor("SPECIAL_NON_WORKING"));

            // Bonifacio Day
            upsert(LocalDate.of(year, 11, 30), "Bonifacio Day", "REGULAR", "NATIONAL", "Preset", rateFor("REGULAR"));

            // Christmas Day
            upsert(LocalDate.of(year, 12, 25), "Christmas Day", "REGULAR", "NATIONAL", "Preset", rateFor("REGULAR"));

            // Rizal Day
            upsert(LocalDate.of(year, 12, 30), "Rizal Day", "REGULAR", "NATIONAL", "Preset", rateFor("REGULAR"));

            // New Year's Eve commonly Special Non-Working
            upsert(LocalDate.of(year, 12, 31), "New Year's Eve", "SPECIAL_NON_WORKING", "NATIONAL", "Preset", rateFor("SPECIAL_NON_WORKING"));

            // ===== Proclamation-based holidays (NOT auto-added here) =====
            // Examples: Eid'l Fitr, Eid'l Adha, special declared days, election holidays, etc.
            // Add them via HolidayReviewDialog when needed.

        } catch (Exception e) {
            // Don’t crash payroll if seeding fails
            e.printStackTrace();
        }
    }

    private static void upsert(LocalDate date, String name, String type, String scope, String notes, double rate) throws Exception {
        Holiday h = new Holiday();
        h.holDate = date;
        h.name = name;
        h.type = type;
        h.scope = scope;
        h.notes = notes;
        h.rateMultiplier = rate;
        dao.upsert(h);
    }

    private static double rateFor(String type) {
        if (type == null) return 0.0;
        String t = type.trim().toUpperCase();

        // EXTRA premium on top of basic pay
        if (t.equals("REGULAR")) return 1.00;
        if (t.equals("SPECIAL_NON_WORKING")) return 0.30;
        if (t.equals("SPECIAL_WORKING")) return 0.30;

        return 0.0;
    }
}
