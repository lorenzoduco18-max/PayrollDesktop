package model;

import java.time.LocalDate;

public class Holiday {
    public int id;
    public LocalDate holDate;
    public String name;

    // REGULAR, SPECIAL_NON_WORKING, SPECIAL_WORKING
    public String type;

    // NATIONAL, LOCAL
    public String scope;

    public String notes;

    // Apply in payroll (DB column: enabled TINYINT(1), default 1)
    public boolean enabled = true;

    // Stored in DB column "rate_multiplier" but used as PHP/day (8 hours). 0 = no override.
    public double rateMultiplier;

    @Override
    public String toString() {
        return holDate + " - " + name + " (" + type + ", " + scope + ", apply=" + (enabled ? "YES" : "NO") + ")";
    }
}
