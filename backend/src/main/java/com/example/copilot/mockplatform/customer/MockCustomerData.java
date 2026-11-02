package com.example.copilot.mockplatform.customer;

import com.example.copilot.aiassistant.domain.customer.CustomerRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Fictional customer master data (docs/spec/03-tool-catalog.md section 5). All values are made up. The A-M card data
 * lives in {@code mock/customer-360.json}.
 */
@Component
public class MockCustomerData {

    private static final String STOP_USE_CERT_NO = "A129876543";

    private final List<CustomerRecord> customers = List.of(
            new CustomerRecord("A123456789", "王小明", "0912-000-111", "xiaoming.wang@example.com",
                    List.of("0123456789012"), LocalDate.of(1968, 3, 14), "宏達電子股份有限公司退休",
                    "台北市中正區忠孝西路一段100號", "R01-退休樂活客群",
                    "B101", "B101", "B101", "ICRM00028", "B101", null),
            new CustomerRecord("F223456780", "林美君", "0922-111-222", "meijun.lin@example.com",
                    List.of("0234567890123"), LocalDate.of(1975, 7, 2), "晨光國際貿易公司",
                    "新北市板橋區文化路二段200號", "F02-家庭教育客群",
                    "B102", "B102", "B101", "ICRM00029", "B102", null),
            new CustomerRecord("H123456781", "陳志華", "0933-333-444", "chihua.chen@example.com",
                    List.of("0345678901234"), LocalDate.of(1982, 11, 21), "遠東機械工業股份有限公司",
                    "台中市西區台灣大道二段300號", "G03-積極成長客群",
                    "B201", "B201", "B201", "ICRM00028", "B101", null),
            new CustomerRecord("B124567892", "黃俊豪", "0955-444-555", "junhao.huang@example.com",
                    List.of("0456789012345"), LocalDate.of(1990, 5, 30), "新創數位科技有限公司",
                    "台中市南屯區公益路二段400號", "D04-數位新世代客群",
                    "B201", "B101", "B201", "ICRM00041", "B201", "MU-N1"),
            new CustomerRecord("E224567893", "周雅婷", "0966-555-666", "yating.chou@example.com",
                    List.of("0567890123456"), LocalDate.of(1978, 9, 9), "海灣醫療器材股份有限公司",
                    "高雄市前鎮區中山三路500號", "W05-穩健保守客群",
                    "B301", "B301", "B301", "ICRM00030", "B301", "MU-S1"),
            new CustomerRecord("N125678904", "吳建德", "0977-666-777", "jiande.wu@example.com",
                    List.of("0678901234567"), LocalDate.of(1965, 1, 18), "自營商",
                    "台中市北區崇德路一段600號", "B06-小型企業主客群",
                    "B201", "B201", "B201", "ICRM00041", "B201", "MU-N1"),
            // Stopped data use: identity and scope only, no 360 data (spec 3).
            new CustomerRecord(STOP_USE_CERT_NO, "蔡宗翰", "0988-777-888", "tsunghan.tsai@example.com",
                    List.of("0789012345678"), LocalDate.of(1959, 4, 2), "榮泰貿易公司退休",
                    "台北市大安區和平東路二段700號", "R01-退休樂活客群",
                    "B101", "B101", "B101", "ICRM00028", "B101", null));

    /** Customers that asked the bank to stop collecting and using their personal data (CIP flag "Y"). */
    private final Set<String> stopUse = Set.of(STOP_USE_CERT_NO);

    public List<CustomerRecord> customers() {
        return customers;
    }

    public boolean stopUseCustomerData(String certNo) {
        return stopUse.contains(certNo);
    }
}
