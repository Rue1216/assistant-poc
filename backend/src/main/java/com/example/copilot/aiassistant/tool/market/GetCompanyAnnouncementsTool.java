package com.example.copilot.aiassistant.tool.market;

import com.example.copilot.aiassistant.tool.ToolContext;
import com.example.copilot.aiassistant.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class GetCompanyAnnouncementsTool extends AbstractMarketTool<GetCompanyAnnouncementsTool.AnnouncementArgs> {

    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_SUBJECT_CHARS = 200;
    private static final int EXCERPT_ITEMS = 3;
    private static final int EXCERPT_SUBJECT_CHARS = 60;

    public record AnnouncementArgs(@NotNull @Pattern(regexp = "^\\d{4,6}$") String stockCode,
                                   @Min(1) @Max(5) Integer limit) {
    }

    public record Announcement(String date, String time, String subject, String factDate) {
    }

    public record AnnouncementsData(String stockCode, String companyName, List<Announcement> announcements) {
    }

    public GetCompanyAnnouncementsTool(TwseClient twse) {
        super(twse);
    }

    @Override
    public String name() {
        return "getCompanyAnnouncements";
    }

    @Override
    public String purpose() {
        return "查詢上市公司重大訊息";
    }

    @Override
    public ToolSpecification specification() {
        return spec(name(), "查詢上市公司「當日」公告的重大訊息。只有當天的資料，查不到代表今天沒有公告。")
                .parameters(schema().addStringProperty("stockCode", "上市股票代號，例如 2330")
                        .addIntegerProperty("limit", "最多回傳幾則，1 到 5，預設 3")
                        .required("stockCode").build()).build();
    }

    @Override
    public Class<AnnouncementArgs> argsType() {
        return AnnouncementArgs.class;
    }

    @Override
    public ToolResult execute(AnnouncementArgs args, ToolContext context) {
        List<Map<String, String>> rows;
        try {
            rows = twse.fetch(TwseClient.COMPANY_ANNOUNCEMENTS);
        } catch (TwseException e) {
            return failure(e);
        }
        int limit = args.limit() == null ? DEFAULT_LIMIT : args.limit();
        Comparator<Map<String, String>> newestFirst = Comparator
                .comparing((Map<String, String> r) -> nullToEmpty(get(r, "發言日期")))
                .thenComparing(r -> nullToEmpty(TwseParsing.time(get(r, "發言時間")))).reversed();
        List<Map<String, String>> mine = rows.stream().filter(r -> args.stockCode().equals(get(r, "公司代號")))
                .sorted(newestFirst).limit(limit).toList();
        String companyName = mine.isEmpty() ? null : companyName(get(mine.get(0), "公司名稱"));
        LocalDate dataDate = mine.isEmpty() ? null : TwseParsing.rocDate(get(mine.get(0), "發言日期"));
        List<Announcement> items = mine.stream().map(GetCompanyAnnouncementsTool::announcement).toList();
        String target = companyName == null ? args.stockCode() : companyName + " " + args.stockCode();
        return ToolResult.success(new AnnouncementsData(args.stockCode(), companyName, items), sources(dataDate),
                citation("上市公司重大訊息", target, excerpt(items), items.size()));
    }

    private static String excerpt(List<Announcement> items) {
        if (items.isEmpty()) {
            return "查無重大訊息";
        }
        return items.stream().limit(EXCERPT_ITEMS).map(a -> shorten(a.subject(), EXCERPT_SUBJECT_CHARS))
                .collect(Collectors.joining("；"));
    }

    private static Announcement announcement(Map<String, String> row) {
        String subject = nullToEmpty(get(row, "主旨")).replaceAll("[\\r\\n]+", "");
        return new Announcement(isoDate(TwseParsing.rocDate(get(row, "發言日期"))),
                TwseParsing.time(get(row, "發言時間")), shorten(subject, MAX_SUBJECT_CHARS),
                isoDate(TwseParsing.rocDate(get(row, "事實發生日"))));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
