package com.example.copilot.aiassistant.prompt;

/**
 * How the final answer maps onto the JSON answer schema (answer-citations spec 5). Code-only like
 * {@link SafetyRules}: never stored in config, never exposed by any API.
 */
public final class AnswerFormatRules {

    public static final String TEXT = """
            # 回答格式規則
            - 最終回答必須符合指定的 JSON 格式，由 blocks 陣列組成
            - 問題分類套版中的 ### 小節對應 heading，條列對應 bullet 或 numbered，其他文字對應 paragraph
            - 套版只提供結構與要點；每個區塊的 text 要寫出依工具結果整理的實際內容（例如「客群：VIP、年齡 55-64 歲、往來 8 年」），不可只照抄套版文字
            - 每個 paragraph、bullet、numbered 的 sources 填入支持該段內容的工具結果 citationId
            - 沒有工具依據的內容（例如一般說明、建議話術）sources 留空
            - 只能使用本次工具結果中出現的 citationId，不得自行編造""";

    private AnswerFormatRules() {
    }
}
