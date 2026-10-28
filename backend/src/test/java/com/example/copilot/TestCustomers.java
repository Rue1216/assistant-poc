package com.example.copilot;

/** Cert numbers of the mock customers, named by what the customer is used for in tests (fictional values). */
public final class TestCustomers {

    /** 王小明 */
    public static final String RETIREE = "A123456789";
    /** 林美君 */
    public static final String COMPLAINT = "F223456780";
    /** 陳志華 */
    public static final String ATTENTION = "H123456781";
    /** 黃俊豪 */
    public static final String NO_MARKETING = "B124567892";
    /** 周雅婷 */
    public static final String HIGH_ASSET = "E224567893";
    /** 吳建德 */
    public static final String SPECIAL_CARE = "N125678904";
    /** 蔡宗翰 (added to the mock data by a later task) */
    public static final String STOP_USE = "A129876543";
    /** Valid format, no such customer. */
    public static final String UNKNOWN = "Z199999999";

    private TestCustomers() {
    }
}
