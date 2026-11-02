package com.example.copilot.aiassistant.domain.tool.customer;

import java.util.Arrays;
import java.util.Optional;

/** The vocabulary of {@code getCustomer(sections)}; the declaration order is the output and citation order. */
public enum CustomerSection {
    PROFILE("profile", "顧客名片", "年齡區間、客群、主要往來分行"),
    MANAGEMENT("management", "顧客經營", "專屬活動、建議商品、經營洞察"),
    ASSET_LIABILITY("assetLiability", "資產負債",
            "資產 AuM、負債 LuM 與信用卡已使用額度（金額、占比、與同客群及前十大比較）、負債對資產比、產品結構（存放比、活存占比、財富管理資產占比）"),
    MEMBER_RANK("memberRank", "會員等級", "理財會員等級、升降等進度、距下一級與防降等線差距、目前與下一級權益"),
    PRODUCT_INTENT("productIntent", "產品意圖", "財富潛力、資產產品意圖與負債外匯意圖指數（與同客群比較）"),
    BEHAVIOR("behavior", "往來與行為", "帳戶往來深度、消費行為、通路使用、數位行為指數（與同客群比較）");

    private final String key;
    private final String displayName;
    private final String description;

    CustomerSection(String key, String displayName, String description) {
        this.key = key;
        this.displayName = displayName;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    /** {@code key（displayName）：contents}; starts with the value the model sends, for the parameter description. */
    public String description() {
        return key + "（" + displayName + "）：" + description;
    }

    public static Optional<CustomerSection> fromKey(String key) {
        return Arrays.stream(values()).filter(s -> s.key.equals(key)).findFirst();
    }
}
