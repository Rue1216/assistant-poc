package com.example.copilot.aiassistant.domain.prompt;

import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.customer.CustomerRestrictions;
import com.example.copilot.aiassistant.domain.customer.c360.CustomFlagging;
import com.example.copilot.aiassistant.domain.customer.c360.FlagType;
import com.example.copilot.aiassistant.domain.customer.c360.RealTimeStatusRecord;
import com.example.copilot.aiassistant.domain.privacy.CustomerContext;
import com.example.copilot.aiassistant.domain.tool.TurnCustomerData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The code-only "本顧客註記" section of the system prompt: the notes that restrict what may be said about products
 * or marketing. Like the safety rules it is never stored in config and never exposed by any API.
 */
public class CustomerNotes {

    private static final Logger log = LoggerFactory.getLogger(CustomerNotes.class);

    private static final String HEADING = "# 本顧客註記\n";
    /**
     * The program only adds its notices when the turn called a customer tool, so the model is told to keep the
     * notes out of such answers and to describe the note categories briefly otherwise.
     */
    private static final String PROGRAM_LISTS_NOTES =
            "本題查詢顧客資料時，程式會在回答最前面列出以上註記，回答中不要重複；未查詢顧客資料時，簡短說明註記類別。";
    private static final String UNAVAILABLE =
            HEADING + "- 無法取得註記，涉及商品或行銷的回答請先參考平台畫面上的註記\n" + PROGRAM_LISTS_NOTES;

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
        if (lines.isEmpty()) {
            return HEADING + "- 無";
        }
        return HEADING + String.join("\n", lines) + "\n" + PROGRAM_LISTS_NOTES;
    }

    private static String line(String name, String expiryDate) {
        return CustomerRestrictions.validExpiry(expiryDate) ? "- " + name + "（到期日 " + expiryDate + "）" : "- " + name;
    }
}
