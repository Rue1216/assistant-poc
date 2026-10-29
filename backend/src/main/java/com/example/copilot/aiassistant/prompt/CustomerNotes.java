package com.example.copilot.aiassistant.prompt;

import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.port.c360.CustomFlagging;
import com.example.copilot.aiassistant.port.c360.FlagType;
import com.example.copilot.aiassistant.port.c360.RealTimeStatusRecord;
import com.example.copilot.aiassistant.privacy.CustomerContext;
import com.example.copilot.aiassistant.tool.TurnCustomerData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The code-only "本顧客註記" section of the system prompt: the notes that restrict what may be said about products
 * or marketing. Like the safety rules it is never stored in config and never exposed by any API.
 */
@Component
public class CustomerNotes {

    private static final Logger log = LoggerFactory.getLogger(CustomerNotes.class);

    private static final String HEADING = "# 本顧客註記\n";
    private static final String UNAVAILABLE = HEADING + "- 無法取得註記，涉及商品或行銷的回答請先參考平台畫面上的註記";

    /** An expiry date is put into the system message only in the platform's yyyy/MM/dd form. */
    private static final Pattern EXPIRY_DATE = Pattern.compile("^\\d{4}/\\d{2}/\\d{2}$");

    /** Flagging types that are listed, in this order (high asset, zone and professional-investor flags are not). */
    private static final List<FlagType> LISTED_FLAGS = List.of(FlagType.NO_MARKETING, FlagType.COMPLAINT);

    /**
     * The section for a FULL customer, or null when the access level is below FULL. Never throws. The real-time
     * data comes from the question's {@link TurnCustomerData}: read once for the prompt and the tools together.
     */
    public String section(CustomerContext customer, TurnCustomerData data) {
        if (customer.accessLevel() != AccessLevel.FULL) {
            return null;
        }
        try {
            Optional<RealTimeStatusRecord> status = data.realTime();
            if (status.isEmpty()) {
                // Fail closed: no record is not "no notes", the same case getRealTimeCustomer reports as unavailable.
                log.warn("customer notes unavailable reason=no-record");
                return UNAVAILABLE;
            }
            return render(status.get());
        } catch (RuntimeException e) {
            // Class name only: the exception message may carry customer data.
            log.warn("customer notes unavailable errorClass={}", e.getClass().getSimpleName());
            return UNAVAILABLE;
        }
    }

    private static String render(RealTimeStatusRecord status) {
        List<String> lines = new ArrayList<>();
        List<CustomFlagging> flags = status.customerFlaggingList() == null ? List.of()
                : status.customerFlaggingList();
        for (FlagType type : LISTED_FLAGS) {
            flags.stream().filter(f -> f.type() == type).forEach(f -> lines.add(line(type.displayName(), f.expiryDate())));
        }
        if (status.specialCareCustomer()) {
            lines.add(line("特殊客群", null));
        }
        if (status.attentionCustomer()) {
            lines.add(line("應注意名單", null));
        }
        return HEADING + (lines.isEmpty() ? "- 無" : String.join("\n", lines));
    }

    private static String line(String name, String expiryDate) {
        return expiryDate == null || !EXPIRY_DATE.matcher(expiryDate).matches() ? "- " + name
                : "- " + name + "（到期日 " + expiryDate + "）";
    }
}
