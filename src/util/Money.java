package util;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

public class Money {
    private static final Locale PH = new Locale("en", "PH");
    private static final NumberFormat NF = NumberFormat.getCurrencyInstance(PH);

    static {
        NF.setCurrency(Currency.getInstance("PHP"));
    }

    public static String php(double amount) {
        return NF.format(amount); // ₱1,234.56
    }
}
