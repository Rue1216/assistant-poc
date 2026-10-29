package com.example.copilot.aiassistant.tool.customer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Display formats for values handed to the model (spec 2.4). Percent-like inputs are already percent numbers. */
public final class DisplayFormat {

    private DisplayFormat() {}

    public static String amount(BigDecimal yuan) {
        // DecimalFormat is not thread-safe, so build one per call.
        DecimalFormat df = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        df.setRoundingMode(RoundingMode.HALF_UP);
        return df.format(yuan) + " 元";
    }

    public static String amount(long yuan) {
        return amount(BigDecimal.valueOf(yuan));
    }

    public static String percent(BigDecimal value) {
        return fixed(value, 2) + "%";
    }

    public static String percentPoints(BigDecimal diff) {
        // The sign follows the rounded value, so 0.001 reads 0.00 without a plus.
        BigDecimal rounded = diff.setScale(2, RoundingMode.HALF_UP);
        return (rounded.signum() > 0 ? "+" : "") + fixed(rounded, 2) + " 個百分點";
    }

    public static String rate(BigDecimal value) {
        return fixed(value, 3) + "%";
    }

    public static String ratio(BigDecimal value) {
        return fixed(value, 2);
    }

    public static String yesNo(boolean value) {
        return value ? "是" : "否";
    }

    private static String fixed(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }
}
