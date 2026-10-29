package com.example.copilot.aiassistant.tool.customer;

import com.example.copilot.aiassistant.port.AccessLevel;
import com.example.copilot.aiassistant.port.c360.Customer360Record;
import com.example.copilot.aiassistant.privacy.CustomerDeidentifier;
import com.example.copilot.aiassistant.privacy.PiiRedactor;
import com.example.copilot.aiassistant.tool.CitationDetail;
import com.example.copilot.aiassistant.tool.ToolContext;
import com.example.copilot.aiassistant.tool.ToolResult;
import com.example.copilot.aiassistant.tool.ToolStatus;
import com.example.copilot.aiassistant.tool.customer.sections.AssetLiabilitySection;
import com.example.copilot.aiassistant.tool.customer.sections.BehaviorSection;
import com.example.copilot.aiassistant.tool.customer.sections.ManagementSection;
import com.example.copilot.aiassistant.tool.customer.sections.MemberRankSection;
import com.example.copilot.aiassistant.tool.customer.sections.ProductIntentSection;
import com.example.copilot.aiassistant.tool.customer.sections.ProfileSection;
import com.example.copilot.aiassistant.tool.customer.sections.SectionSupport;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@code getCustomer(sections)}: the customer-360 platform API, narrowed to the sections the question needs. Section
 * order in the output and in the citation title is the {@link CustomerSection} order, never the caller's.
 */
@Component
public class GetCustomerTool extends AbstractCustomerTool<GetCustomerTool.CustomerArgs> {

    public record CustomerArgs(@NotEmpty @Size(max = 6) List<String> sections) {
    }

    private static final Logger log = LoggerFactory.getLogger(GetCustomerTool.class);

    private final ManagementSection management;

    public GetCustomerTool(PiiRedactor redactor) {
        this.management = new ManagementSection(redactor);
    }

    @Override
    public String name() {
        return "getCustomer";
    }

    @Override
    public String purpose() {
        return "查詢顧客 360 資料";
    }

    @Override
    public ToolSpecification specification() {
        String sectionList = Arrays.stream(CustomerSection.values()).map(CustomerSection::description)
                .collect(Collectors.joining("；"));
        JsonArraySchema sections = JsonArraySchema.builder()
                .description("依問題指定需要的資料區塊，只選必要的。各區塊內容：" + sectionList)
                .items(JsonEnumSchema.builder()
                        .enumValues(Arrays.stream(CustomerSection.values()).map(CustomerSection::key).toList())
                        .build())
                .build();
        return ToolSpecification.builder().name(name())
                .description("查詢本顧客的 360 資料（去識別化），依 sections 指定的區塊回傳，並附與同客群的比較。")
                .parameters(JsonObjectSchema.builder().addProperty("sections", sections).required("sections")
                        .additionalProperties(false).build())
                .build();
    }

    @Override
    public Class<CustomerArgs> argsType() {
        return CustomerArgs.class;
    }

    @Override
    public AccessLevel requiredAccessLevel() {
        return AccessLevel.LIMITED;
    }

    @Override
    public ToolResult execute(CustomerArgs args, ToolContext context) {
        Set<CustomerSection> requested = EnumSet.noneOf(CustomerSection.class);
        for (String key : args.sections()) {
            Optional<CustomerSection> section = CustomerSection.fromKey(key);
            if (section.isEmpty()) {
                return ToolResult.failure(ToolStatus.DENIED, "TOOL_ARGUMENT_INVALID", "參數格式不正確");
            }
            requested.add(section.get());
        }
        if (context.data() == null) {
            return unavailable();
        }
        Optional<Customer360Record> customer = context.data().customer360();
        if (customer.isEmpty()) {
            return unavailable();
        }
        return context.customer().accessLevel() == AccessLevel.FULL ? full(customer.get(), requested, context)
                : limited(customer.get(), requested, context);
    }

    private ToolResult full(Customer360Record record, Set<CustomerSection> requested, ToolContext context) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (record.dataDate() != null) {
            data.put("dataDate", record.dataDate());
        }
        List<CustomerSection> returned = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        for (CustomerSection section : requested) {
            Map<String, Object> rendered = renderOrNull(section, record, context);
            if (rendered == null) {
                SectionSupport.missing(section.key());
                unavailable.add(section.key());
            } else {
                data.put(section.key(), rendered);
                returned.add(section);
            }
        }
        if (returned.isEmpty()) {
            return unavailable();
        }
        if (!unavailable.isEmpty()) {
            data.put("unavailableSections", unavailable);
        }
        return success(data, returned, record.dataDate());
    }

    /** Whatever is asked, a LIMITED user gets the de-identified profile only; the other sections are unavailable. */
    private ToolResult limited(Customer360Record record, Set<CustomerSection> requested, ToolContext context) {
        Map<String, Object> profile = ProfileSection.render(record, context);
        if (profile == null) {
            return unavailable();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (record.dataDate() != null) {
            data.put("dataDate", record.dataDate());
        }
        data.put("profile", profile);
        data.put("accessNote", CustomerDeidentifier.LIMITED_NOTE);
        List<String> unavailable = requested.stream().filter(s -> s != CustomerSection.PROFILE)
                .map(CustomerSection::key).toList();
        if (!unavailable.isEmpty()) {
            data.put("unavailableSections", unavailable);
        }
        return success(data, List.of(CustomerSection.PROFILE), record.dataDate());
    }

    /** Null when the section is absent or fails to render: one malformed block must not fail the whole call. */
    private Map<String, Object> renderOrNull(CustomerSection section, Customer360Record record, ToolContext context) {
        try {
            return render(section, record, context);
        } catch (RuntimeException e) {
            // Section key and class name only: the exception message may carry customer data.
            log.warn("customer 360 section failed: {} {}", section.key(), e.getClass().getSimpleName());
            return null;
        }
    }

    private Map<String, Object> render(CustomerSection section, Customer360Record record, ToolContext context) {
        return switch (section) {
            case PROFILE -> ProfileSection.render(record, context);
            case MANAGEMENT -> management.render(record, context);
            case ASSET_LIABILITY -> AssetLiabilitySection.render(record);
            case MEMBER_RANK -> MemberRankSection.render(record);
            case PRODUCT_INTENT -> ProductIntentSection.render(record);
            case BEHAVIOR -> BehaviorSection.render(record);
        };
    }

    /** Internal data: the citation names the returned sections and the data date, never an excerpt. */
    private static ToolResult success(Map<String, Object> data, List<CustomerSection> returned, String dataDate) {
        String title = TITLE_PREFIX + returned.stream().map(CustomerSection::displayName)
                .collect(Collectors.joining("、"));
        String description = dataDate == null ? null : "資料日期 " + dataDate;
        return ToolResult.success(data, SOURCES, new CitationDetail(title, null, null, description, returned.size()));
    }
}
