package com.example.copilot.aiassistant.privacy;

/** Structured-field de-identification (03 section 1.6, layer one). Stateless. */
public final class CustomerDeidentifier {

    public static final String SUBJECT = "本顧客";
    public static final String LIMITED_NOTE = "依您的權限僅能查看去識別化基本輪廓";

    private CustomerDeidentifier() {
    }

    public static String ageRange(int age) {
        if (age < 25) {
            return "18-24";
        } else if (age < 35) {
            return "25-34";
        } else if (age < 45) {
            return "35-44";
        } else if (age < 55) {
            return "45-54";
        } else if (age < 65) {
            return "55-64";
        }
        return "65+";
    }
}
