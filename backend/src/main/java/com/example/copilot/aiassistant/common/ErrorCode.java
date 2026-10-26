package com.example.copilot.aiassistant.common;

import org.springframework.http.HttpStatus;

/** API error codes and their fixed friendly messages (docs/spec/05-error-codes.md section 2). */
public enum ErrorCode {

    // 2.1 common
    AI_INPUT_INVALID(HttpStatus.BAD_REQUEST, "輸入內容不符合格式，請確認後再試"),
    AI_UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "登入已逾時，請重新登入"),
    AI_FORBIDDEN(HttpStatus.FORBIDDEN, "您沒有使用此功能的權限"),
    AI_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "提問太頻繁，請稍候一分鐘再試"),
    AI_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系統發生錯誤，請稍後再試"),

    // 2.2 chat
    AI_CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "查無此顧客或您沒有查看權限"),
    AI_TOKEN_BUDGET_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "問題涉及的資料太多，請縮小範圍後再問"),
    AI_LLM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "小幫手回應逾時，請稍後再試"),
    AI_REQUEST_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "處理時間過長，請簡化問題後再試"),
    AI_LLM_RATE_LIMITED(HttpStatus.SERVICE_UNAVAILABLE, "小幫手目前使用量較高，請稍後再試"),
    AI_LLM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "小幫手暫時無法使用，其他功能不受影響"),
    AI_ANSWER_FORMAT_INVALID(HttpStatus.BAD_GATEWAY, "回答格式異常，請重試"),
    AI_FEEDBACK_NOT_FOUND(HttpStatus.NOT_FOUND, "這則回答已無法評分"),

    // 2.3 prompt management
    AI_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "找不到指定的設定或版本"),
    AI_CONFIG_CONFLICT(HttpStatus.CONFLICT, "內容已被他人更新，請重新載入後再編輯"),
    AI_CONFIG_INVALID_STATE(HttpStatus.CONFLICT, "目前狀態無法執行此操作"),
    AI_CONFIG_OVERWRITE_PENDING(HttpStatus.CONFLICT, "此設定已有編輯中的版本，套用後它會被標為已捨棄"),
    AI_CONFIG_VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "內容未通過發布檢查，請依提示修正"),

    // 2.4 prompt evaluation
    AI_EVAL_RUNNING(HttpStatus.CONFLICT, "目前已有評測執行中，請稍後再試"),
    AI_EVAL_NOT_FOUND(HttpStatus.NOT_FOUND, "找不到指定的評測紀錄");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
