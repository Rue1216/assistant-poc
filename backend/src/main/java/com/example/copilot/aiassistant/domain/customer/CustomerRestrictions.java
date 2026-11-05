package com.example.copilot.aiassistant.domain.customer;

import com.example.copilot.aiassistant.domain.customer.c360.CustomFlagging;
import com.example.copilot.aiassistant.domain.customer.c360.FlagType;
import com.example.copilot.aiassistant.domain.customer.c360.RealTimeStatusRecord;
import com.example.copilot.aiassistant.domain.privacy.CustomerContext;
import com.example.copilot.aiassistant.domain.tool.TurnCustomerData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What the customer's notes forbid or require, decided in code. Notes are always treated as in force: the expiry
 * date is shown but never compared with today. When the notes cannot be read it fails closed.
 */
public record CustomerRestrictions(boolean notesUnavailable, boolean withholdMarketingData, List<String> notices) {

    private static final Logger log = LoggerFactory.getLogger(CustomerRestrictions.class);

    private static final Pattern EXPIRY_DATE = Pattern.compile("^\\d{4}/\\d{2}/\\d{2}$");

    private static final String NO_MARKETING_NOTICE = "本顧客有不宜行銷註記，以下不提供商品機會、推薦產品或行銷話術。";
    private static final String UNAVAILABLE_NOTICE = "無法取得註記，商品與行銷資料暫不提供，請先參考平台畫面上的註記。";

    public CustomerRestrictions {
        notices = List.copyOf(notices);
    }

    /** No restrictions for anyone below FULL (the real-time data is not read). Never throws. */
    public static CustomerRestrictions of(CustomerContext customer, TurnCustomerData data) {
        if (customer.accessLevel() != AccessLevel.FULL) {
            return new CustomerRestrictions(false, false, List.of());
        }
        try {
            Optional<RealTimeStatusRecord> status = data.realTime();
            if (status.isEmpty()) {
                log.warn("customer restrictions unavailable reason=no-record");
                return unavailable();
            }
            return fromStatus(status.get());
        } catch (RuntimeException e) {
            // Class name only: the exception message may carry customer data.
            log.warn("customer restrictions unavailable errorClass={}", e.getClass().getSimpleName());
            return unavailable();
        }
    }

    /** An expiry date is shown only in the platform's yyyy/MM/dd form. */
    public static boolean validExpiry(String expiryDate) {
        return expiryDate != null && EXPIRY_DATE.matcher(expiryDate).matches();
    }

    private static CustomerRestrictions unavailable() {
        return new CustomerRestrictions(true, true, List.of(UNAVAILABLE_NOTICE));
    }

    private static CustomerRestrictions fromStatus(RealTimeStatusRecord status) {
        List<CustomFlagging> flags = status.customerFlaggingList() == null ? List.of()
                : status.customerFlaggingList();
        List<String> notices = new ArrayList<>();
        boolean noMarketing = flags.stream().anyMatch(f -> f.type() == FlagType.NO_MARKETING);
        if (noMarketing) {
            notices.add(NO_MARKETING_NOTICE);
        }
        flags.stream().filter(f -> f.type() == FlagType.COMPLAINT).forEach(f -> notices.add(
                "注意事項：本顧客有客訴註記" + (validExpiry(f.expiryDate()) ? "（到期日 " + f.expiryDate() + "）" : "")
                        + "，請依規定程序處理。"));
        if (status.specialCareCustomer()) {
            notices.add("注意事項：本顧客為特殊客群，請依規定程序處理，並確認商品適合度。");
        }
        if (status.attentionCustomer()) {
            notices.add("注意事項：本顧客有應注意名單註記，請依規定程序處理。");
        }
        return new CustomerRestrictions(false, noMarketing, notices);
    }
}
