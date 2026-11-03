# Prompt 管理 review 修正 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 依 review 修正 Prompt 管理的驗證、發布排程、錯誤細節、新增設定規則與列表查詢，並同步 01、02、04、05、06 規格。

**Architecture:** 在已完成的四層結構上修改：domain 放錯誤細節 record、驗證器與 repository 介面；application 的命令服務負責規則；infrastructure 實作條件更新、列表查詢與 Mongo validator；interfaces 只做 JSON 轉換與 HTTP 狀態。三個 Phase，每個 Phase 結束時後端測試全綠。

**Tech Stack:** Java 25、Spring Boot 4.1、Spring Data MongoDB、Jackson 3、JUnit 5、Testcontainers（MongoDB 8）；前端 Vue 3、Vitest 4。

**Spec:** [docs/superpowers/specs/2026-11-02-prompt-admin-review-fixes-design.md](../specs/2026-11-02-prompt-admin-review-fixes-design.md)（v0.2）

## Global Constraints

- 路徑縮寫：`{main}` = `backend/src/main/java/com/example/copilot/aiassistant/`，`{test}` = `backend/src/test/java/com/example/copilot/aiassistant/`，`{fe}` = `frontend/src/features/ai-assistant/`。
- `ArchitectureRulesTest` 必須維持全綠：domain 不得 import Spring、MongoDB、`interfaces`、`application`；application 不得 import Spring Data、`interfaces`、`infrastructure`；interfaces 不得 import `domain.repository`、`infrastructure`。
- 錯誤細節的 JSON 欄位名稱、順序與 null 值和今日相同（spec 4.1 表）。
- 禁止內容訊息格式：`{欄位}疑似包含{類型}，請移除`，欄位為 `名稱` 或 `修改說明`。
- 操作者常數 `"system"`；System Prompt 的 key `"system.main"`。
- 不改 REST 路徑、錯誤碼、collection 與 index 名稱。套用版本的 HTTP 狀態是唯一的回應碼變更（200 改 201）。
- 使用者不要自動 commit：subagent 跳過每個 task 的 commit 步驟。每個 Phase 結束時提供 `git add` 指令與 commit message，**停下來等使用者確認已 commit** 再開始下一個 Phase。
- 後端驗證：`cd backend && mvn -q test`（需要 Docker）；單一類別：`mvn -q test -Dtest=類別名`。前端驗證：`cd frontend && npm test`。

## Review Focus

1. 既有草稿的修改說明已含禁止內容：使用者在同一次儲存裡改掉它，應該成功，因為檢查的是這次送出的值，不是資料庫裡的舊值（Task 2）。
2. 管理者開著一份已排程版本，期間排程器把它退回草稿：管理者用舊的 `docVersion` 儲存，應得到 409 `AI_CONFIG_CONFLICT`，不是覆寫成功（Task 4）。
3. 預約後才調整驗證規則，例如把某個網址 host 移出白名單：到期時應退回草稿，不應上線（Task 4）。
4. 同一輪有兩個到期版本，一個不合格、一個合格：合格的那一個仍要發布，快取仍要清除（Task 4）。
5. 套用時既有的編輯中版本是 `DRAFT`：覆寫確認的細節仍要輸出 `"scheduledAt": null`，不能省略這個欄位（Task 1）。

---

## Phase A：錯誤細節與驗證

### Task 1: 具型別的錯誤細節

**Files:**
- Create: `{main}domain/exception/ErrorDetail.java`、`FieldDetail.java`、`WorkingCopyDetail.java`、`RunningEvalDetail.java`
- Create: `{main}interfaces/rest/ErrorDetailJson.java`
- Modify: `{main}domain/exception/AssistantException.java`、`{main}domain/config/ValidationError.java`
- Modify: `{main}interfaces/rest/ApiError.java`、`{main}interfaces/rest/AssistantExceptionHandler.java`
- Modify: `{main}interfaces/rest/config/AdminConfigController.java`（移除 `withOffsets` 與套用的 try/catch，`invalidInput` 改用 `FieldDetail`）
- Modify: `{main}interfaces/rest/chat/ChatController.java`（三處 `Map.of("field", …)`）
- Modify: `{main}application/config/ConfigCommandService.java`（`pendingDetail`、`invalidInput`）
- Modify: `{main}application/evaluation/EvalRunService.java`（`runningConflict`）
- Test: `{test}interfaces/rest/AssistantExceptionHandlerTest.java`、`{test}interfaces/rest/testsupport/ThrowingController.java`、`{test}application/config/ConfigCommandServiceTest.java`、`{test}application/evaluation/EvalRunServiceTest.java`

**Interfaces:**
- Produces（domain，純 Java）：
  - `public interface ErrorDetail {}`，不宣告 sealed
  - `public record FieldDetail(String field) implements ErrorDetail`
  - `public record WorkingCopyDetail(ConfigStatus status, Instant scheduledAt, Instant updatedAt, long docVersion) implements ErrorDetail`
  - `public record RunningEvalDetail(String runId, String configKey, String triggeredBy, int completedCases, int totalCases) implements ErrorDetail`
  - `ValidationError(String rule, String message) implements ErrorDetail`
  - `AssistantException(ErrorCode code, List<? extends ErrorDetail> details)`；`List<ErrorDetail> details()`
- Produces（interfaces）：`static Object ErrorDetailJson.of(ErrorDetail d)`：`WorkingCopyDetail` 轉成 `record WorkingCopy(String status, OffsetDateTime scheduledAt, OffsetDateTime updatedAt, long docVersion)`，時間用 `AdminTime.at`；其他原樣回傳。`ApiError.of(ErrorCode, List<? extends ErrorDetail>)` 內部以 `ErrorDetailJson.of` 轉成 `List<Object>`。

- [ ] **Step 1: 寫失敗的測試**

`ThrowingController` 新增三個端點，並把既有 `/details` 改成丟 `ValidationError`：

- `/field-detail`：`AI_INPUT_INVALID`，`List.of(new FieldDetail("scheduledAt"))`
- `/working-copy`：`AI_CONFIG_OVERWRITE_PENDING`，`new WorkingCopyDetail(ConfigStatus.DRAFT, null, Instant.parse("2026-10-20T02:00:00Z"), 3L)`
- `/running-eval`：`AI_EVAL_RUNNING`，`new RunningEvalDetail("6719f0c2a4b5e6f7a8b9c0d1", "instruction.contact-prep", "EEE22222", 7, 34)`

`AssistantExceptionHandlerTest` 新增 `detailsKeepTheirJsonShape()`，對四個端點各斷言回應本體（UTF-8）包含 `"details":` 加上下列字串，藉此同時鎖定欄位名稱、順序與 null：

```
[{"field":"scheduledAt"}]
[{"status":"DRAFT","scheduledAt":null,"updatedAt":"2026-10-20T10:00:00+08:00","docVersion":3}]
[{"runId":"6719f0c2a4b5e6f7a8b9c0d1","configKey":"instruction.contact-prep","triggeredBy":"EEE22222","completedCases":7,"totalCases":34}]
[{"rule":"NOT_EMPTY","message":"內容不可為空"}]
```

既有 `validationFailureBecomesInputInvalid`、`typeMismatchBecomesInputInvalid`、`missingParameterBecomesInputInvalid`、`invalidPathVariableBecomesInputInvalid` 不改斷言。

- [ ] **Step 2: 執行，確認失敗**

Run: `mvn -q test -Dtest=AssistantExceptionHandlerTest`
Expected: 編譯失敗，`FieldDetail`、`WorkingCopyDetail`、`RunningEvalDetail` 不存在

- [ ] **Step 3: 實作上列 Interfaces**

- 例外處理器的 Spring 驗證分支改產生 `FieldDetail`。
- `ConfigCommandService.pendingDetail` 回傳 `WorkingCopyDetail`，`invalidInput` 用 `FieldDetail`。
- `EvalRunService.runningConflict` 用 `RunningEvalDetail`。
- `ChatController` 與 `AdminConfigController.invalidInput` 用 `FieldDetail`。
- 刪除 `AdminConfigController.withOffsets`，以及 `apply` 的 try/catch。

- [ ] **Step 4: 更新既有測試的斷言型別**

- `ConfigCommandServiceTest.createKeyWithMismatchedTypeIsInputInvalid`：`containsExactly(new FieldDetail("configType"))`。欄位名稱在 Task 5 才改。
- `EvalRunServiceTest.secondStartIsRejectedWhileRunning`：改為 `isEqualTo(List.of(new RunningEvalDetail(first.id(), KEY, ACTOR, 0, dataset.cases().size())))`。

- [ ] **Step 5: 執行，確認通過**

Run: `mvn -q test -Dtest='AssistantExceptionHandlerTest,AdminConfigControllerTest,EvalControllerTest,ConfigCommandServiceTest,EvalRunServiceTest,ChatControllerTest,ArchitectureRulesTest'`
Expected: PASS。`AdminConfigControllerTest.applyOverwriteHandshake` 的 `+08:00` 斷言仍通過。

### Task 2: 名稱與修改說明在儲存時檢查

**Files:**
- Modify: `{main}domain/config/ConfigValidator.java`
- Modify: `{main}application/config/ConfigCommandService.java`（`saveWorkingCopy`、`publish`、`validateWorkingCopy`、`requireValid`）
- Modify: `{main}application/evaluation/EvalRunService.java`（`start` 的驗證）
- Test: `{test}domain/config/ConfigValidatorTest.java`、`{test}application/config/ConfigCommandServiceTest.java`、`{test}application/evaluation/EvalRunServiceTest.java`、`{test}interfaces/rest/config/AdminConfigControllerTest.java`

**Interfaces:**
- Consumes：Task 1 的 `AssistantException(ErrorCode, List<? extends ErrorDetail>)`
- Produces：
  - `List<ValidationError> ConfigValidator.validateMetadata(String name, String changeNote)`：名稱與修改說明的禁止內容檢查；null 或空字串略過；名稱在前。
  - `List<ValidationError> ConfigValidator.validate(ConfigType type, String name, String changeNote, String content)`：內容錯誤在前，接著 `validateMetadata`。
  - 移除三參數的 `validate(ConfigType, String name, String content)`；兩參數的 `validate(ConfigType, String content)` 保留。
  - `ConfigCommandService` 內部：`requireValid(ConfigType, String name, String changeNote, String content)`、`requireMetadataValid(String name, String changeNote)`，失敗都丟 422 `AI_CONFIG_VALIDATION_FAILED`。

- [ ] **Step 1: 寫失敗的測試**

`ConfigValidatorTest`：
- `changeNoteWithForbiddenContentIsReported`：`validateMetadata("名稱", "聯絡 0912-345-678")` 等於 `List.of(new ValidationError("FORBIDDEN_CONTENT", "修改說明疑似包含手機號碼，請移除"))`。
- `blankChangeNoteIsSkipped`：`validateMetadata("名稱", null)` 與 `validateMetadata("名稱", "")` 都是空清單。
- `errorsAreOrderedContentNameChangeNote`：`validate(SYSTEM_INSTRUCTION, "名稱 0912-345-678", "password: x", "## 其他\n只有一個章節")` 的 `extracting(ValidationError::message)` 為 `containsExactly("缺少必要章節「## 適用問題」", "名稱疑似包含手機號碼，請移除", "修改說明疑似包含密碼，請移除")`。

`ConfigCommandServiceTest`：
- `saveDraftRejectsForbiddenNameOrChangeNote`：`DRAFT` 以 changeNote `"0912-345-678"` 儲存，回 `AI_CONFIG_VALIDATION_FAILED`，`details` 含 `修改說明疑似包含手機號碼，請移除`；資料庫內容不變（`reload(2).docVersion()` 不變）。
- `saveDraftStillAllowsForbiddenContent`：`DRAFT` 以 content 含 `10.1.2.3` 儲存成功。
- `staleForbiddenChangeNoteCanBeFixedInTheSameSave`（Review Focus 1）：直接以 `repository.save` 寫入一份 changeNote 含 `0912-345-678` 的草稿，再以乾淨的 changeNote 呼叫 `saveWorkingCopy`，成功。
- `publishRejectsForbiddenChangeNote`：同上的舊草稿直接 `publishNow`，回 422。

`EvalRunServiceTest.forbiddenChangeNoteFailsValidation`：草稿的 changeNote 含 `0912-345-678`，`start` 回 422。

`AdminConfigControllerTest.saveWithForbiddenChangeNoteIs422`：`PUT .../draft` 帶 changeNote `"0912-345-678"`，`status().isUnprocessableEntity()`、`$.details[0].rule` 為 `FORBIDDEN_CONTENT`。

- [ ] **Step 2: 執行，確認失敗**

Run: `mvn -q test -Dtest='ConfigValidatorTest,ConfigCommandServiceTest,EvalRunServiceTest,AdminConfigControllerTest'`
Expected: 編譯失敗，`validateMetadata` 不存在

- [ ] **Step 3: 實作**

- 驗證器沿用既有的 `checkForbidden(field, new String[] {value}, errors, false)`。
- `saveWorkingCopy`：先 `requireWorkingCopy`，維持 404 與 409 的順序。`SCHEDULED` 且 `KEEP_STATUS` 時呼叫 `requireValid(..., cmd.changeNote(), cmd.content())`，其他情況呼叫 `requireMetadataValid(cmd.name(), cmd.changeNote())`。
- `publish`、`validateWorkingCopy`、`EvalRunService.start` 改用四參數 `validate`，帶入工作副本的 `changeNote()`。

- [ ] **Step 4: 執行，確認通過**

Run: `mvn -q test`
Expected: PASS

- [ ] **Step 5: Phase A 結束：提供 commit 指令並停下**

```bash
git add backend/src
git commit -m "fix(config): typed error details; check name and change note on every save" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

等使用者確認已 commit，再開始 Phase B。

---

## Phase B：發布、排程、新增設定、列表

### Task 3: 立即發布清空預約時間、建立草稿記錄來源、套用回 201

**Files:**
- Modify: `{main}application/config/ConfigCommandService.java`（`publish`、`createDraft`）
- Modify: `{main}interfaces/rest/config/AdminConfigController.java`（`apply` 加 `@ResponseStatus(HttpStatus.CREATED)`）
- Test: `{test}application/config/ConfigCommandServiceTest.java`、`{test}interfaces/rest/config/AdminConfigControllerTest.java`

**Interfaces:**
- Consumes：既有 `AssistantConfig.withStatus(ConfigStatus, Instant)`、`withBasedOnVersion(Integer)`

- [ ] **Step 1: 寫失敗的測試**

- `publishImmediateOnScheduledPublishesNow` 加斷言 `assertThat(published.scheduledAt()).isNull()`，並斷言 `reload(published.version()).scheduledAt()` 為 null。
- `publishDuePublishesAndKeepsScheduler` 已斷言保留 `scheduledAt`，若沒有就補上。
- `createDraftRecordsSourceVersion`：有已發布 v1 時 `createDraft` 的 `basedOnVersion()` 為 1；`publishV2()` 之後再建立草稿為 2。
- `createDraftWithoutPublishedUsesLatestAsSource`：以 `createKey` 建立 `instruction.new-topic` 並捨棄 v1，`createDraft` 的 `version()` 為 2、`basedOnVersion()` 為 1。
- `AdminConfigControllerTest.applyOverwriteHandshake` 與 `applyWithoutWorkingCopyReturnsDraft`：成功的套用改為 `status().isCreated()`。

- [ ] **Step 2: 執行，確認失敗**

Run: `mvn -q test -Dtest='ConfigCommandServiceTest,AdminConfigControllerTest'`
Expected: FAIL：`scheduledAt` 不為 null、`basedOnVersion` 為 null、套用回 200

- [ ] **Step 3: 實作**

- `publish` 的立即發布改為 `withStatus(ConfigStatus.PUBLISHED, null)`，並刪除「scheduledAt is kept as it was」那行註解。
- `createDraft` 在 `newDraft(...)` 之後接 `.withBasedOnVersion(base.version())`。
- `apply` 加上 201。

- [ ] **Step 4: 執行，確認通過**

Run: `mvn -q test -Dtest='ConfigCommandServiceTest,AdminConfigControllerTest'`
Expected: PASS

### Task 4: 排程到期重新驗證，不合格退回草稿

**Files:**
- Modify: `{main}domain/config/AssistantConfig.java`（新增常數）
- Modify: `{main}domain/repository/AssistantConfigRepository.java`、`{main}infrastructure/mongo/AssistantConfigRepositoryImpl.java`
- Modify: `{main}application/config/ConfigCommandService.java`（`publishDue`）
- Modify: `{main}infrastructure/seed/SeedLoader.java`（改用常數）
- Test: `{test}infrastructure/mongo/AssistantConfigRepositoryImplTest.java`、`{test}application/config/ConfigCommandServiceTest.java`

**Interfaces:**
- Consumes：Task 2 的四參數 `ConfigValidator.validate`
- Produces：
  - `public static final String AssistantConfig.SYSTEM_ACTOR = "system"`
  - `boolean AssistantConfigRepository.revertScheduled(AssistantConfig due, String actor, Instant now)`：條件為 `id`、`status = SCHEDULED`、`docVersion` 都相符；更新 `status = DRAFT`、unset `scheduledAt`、`updatedBy = actor`、`updatedAt = now`、`docVersion` 加 1；命中回 true，否則 false。不使用交易。

- [ ] **Step 1: 寫失敗的測試**

`AssistantConfigRepositoryImplTest`：
- `revertScheduledTurnsItBackIntoADraft`：插入一份 `SCHEDULED` 草稿，`revertScheduled(due, "system", NOW)` 回 true；重新讀取為 `DRAFT`，`scheduledAt` 為 null，`updatedBy` 為 `system`，`docVersion` 為原值加 1。
- `revertScheduledSkipsAChangedVersion`：先 `save` 一次讓 `docVersion` 增加，再用舊物件呼叫，回 false，文件不變。

`ConfigCommandServiceTest`：
- `publishDueRevertsInvalidScheduledVersion`：用 `repository.save` 讓已排程版本的 content 不合格（`INVALID_CONTENT`），`clock.advance` 過預約時間後 `publishDue()` 回 0；該版本為 `DRAFT`、`updatedBy` 為 `AssistantConfig.SYSTEM_ACTOR`；`reload(1)` 仍為 `PUBLISHED`；`verify(cache, never()).invalidate()`。
- `publishDueRevertsWhenRulesChangedAfterScheduling`（Review Focus 3）：用一個允許 `docs.example.com` 的 `ConfigValidator` 建立服務並預約一份含 `https://docs.example.com/x` 的版本，再用 `List.of()` 白名單的驗證器建立服務呼叫 `publishDue()`，結果為退回草稿。
- `publishDuePublishesValidOnesInTheSameRun`（Review Focus 4）：另一個 key `instruction.market-brief` 預約合格版本，`KEY` 預約後改成不合格；`publishDue()` 回 1，`market-brief` 為 `PUBLISHED`，`KEY` 的工作副本為 `DRAFT`，`verify(cache).invalidate()`。
- `saveAfterRevertWithStaleDocVersionConflicts`（Review Focus 2）：退回後，用退回前的物件呼叫 `save(...)`，回 `AI_CONFIG_CONFLICT`。

- [ ] **Step 2: 執行，確認失敗**

Run: `mvn -q test -Dtest='AssistantConfigRepositoryImplTest,ConfigCommandServiceTest'`
Expected: 編譯失敗，`revertScheduled` 與 `SYSTEM_ACTOR` 不存在

- [ ] **Step 3: 實作**

- `publishDue` 的每一筆先跑四參數 `validate`。有錯誤時呼叫 `revertScheduled(due, SYSTEM_ACTOR, now)`。回 true 就記 WARN，只含 `configKey`、`version` 與去重後的規則代碼：

```java
log.warn("scheduled publish rejected configKey={} version={} rules={}", due.configKey(), due.version(),
        errors.stream().map(ValidationError::rule).distinct().toList());
```

  不論回 true 或 false 都 `continue`，不計入發布數。沒有錯誤時照舊呼叫 `publishScheduled`。
- `SeedLoader` 的私有 `SYSTEM_USER` 改用 `AssistantConfig.SYSTEM_ACTOR`。

- [ ] **Step 4: 執行，確認通過**

Run: `mvn -q test -Dtest='AssistantConfigRepositoryImplTest,ConfigCommandServiceTest,SeedLoaderTest,ScheduledPublishJobTest,ArchitectureRulesTest'`
Expected: PASS

### Task 5: 新增設定的 key 規則與 Mongo validator

**Files:**
- Modify: `{main}domain/config/AssistantConfig.java`（新增常數）
- Modify: `{main}application/config/ConfigCommandService.java`（`createKey`）
- Modify: `{main}domain/repository/AssistantConfigRepository.java`、`{main}infrastructure/mongo/AssistantConfigRepositoryImpl.java`、`{main}infrastructure/mongo/AssistantConfigMongoRepository.java`（移除 `existsByConfigType`）
- Modify: `backend/src/main/resources/mongo/config-validator.json`
- Test: `{test}application/config/ConfigCommandServiceTest.java`、`{test}infrastructure/mongo/AssistantConfigRepositoryImplTest.java`、`{test}infrastructure/mongo/MongoSchemaTest.java`、`{test}interfaces/rest/config/AdminConfigControllerTest.java`

**Interfaces:**
- Produces：`public static final String AssistantConfig.SYSTEM_PROMPT_KEY = "system.main"`

- [ ] **Step 1: 寫失敗的測試**

`ConfigCommandServiceTest`（取代 `createKeyRejectsSecondSystemPrompt` 與 `createKeyWithMismatchedTypeIsInputInvalid`）：
- `createKeySystemPromptMustBeSystemMain`：`createKey("system.second", SYSTEM_PROMPT, …)` 回 `AI_INPUT_INVALID`，`details` 為 `[FieldDetail("configKey")]`；`findLatest("system.second")` 為空。
- `createKeyInstructionMustHaveInstructionPrefix`：`createKey("system.x", SYSTEM_INSTRUCTION, …)` 回 `AI_INPUT_INVALID`，`details` 為 `[FieldDetail("configKey")]`。
- `createKeyExistingKeyIsInvalidState`：`system.main` 與 `KEY` 各建立一次，都回 `AI_CONFIG_INVALID_STATE`。
- `createKeyLosingTheInsertRaceIsInvalidState`：用 `mock(AssistantConfigRepository.class)`，讓 `findLatest` 回空、`insert` 丟 `new AssistantException(ErrorCode.AI_CONFIG_CONFLICT)`，結果為 `AI_CONFIG_INVALID_STATE`。

`MongoSchemaTest`：
- `validatorRejectsSystemPromptOtherThanSystemMain`：`rawDoc("DRAFT", null).append("configType", "SYSTEM_PROMPT").append("configKey", "system.other")`，插入時丟 `MongoWriteException`；同樣寫法但 key 為 `system.main` 時插入成功。
- `validatorRejectsInstructionWithoutInstructionPrefix`：`rawDoc("DRAFT", null).append("configKey", "system.raw")`，插入時丟 `MongoWriteException`。
- 既有的 `validatorRequiresScheduledAtForScheduled` 不改，確認 `$and` 保留了原本的規則。

`AdminConfigControllerTest.createKeyReturns201AndRejectsDuplicates`：最後一段加 `jsonPath("$.details[0].field").value("configKey")`。

`AssistantConfigRepositoryImplTest`：刪掉 `existsByConfigType` 那一行斷言。

- [ ] **Step 2: 執行，確認失敗**

Run: `mvn -q test -Dtest='ConfigCommandServiceTest,MongoSchemaTest,AdminConfigControllerTest'`
Expected: FAIL：`system.second` 回 `INVALID_STATE`、欄位為 `configType`、validator 接受不一致的文件

- [ ] **Step 3: 實作**

- `createKey` 的順序：
  1. `SYSTEM_PROMPT` 且 key 不等於 `SYSTEM_PROMPT_KEY`，或 `SYSTEM_INSTRUCTION` 且 key 不以 `instruction.` 開頭，回 400，欄位為 `configKey`。
  2. `findLatest` 存在時回 409 `INVALID_STATE`。
  3. `insert` 時捕捉 `AI_CONFIG_CONFLICT`，改丟 `AI_CONFIG_INVALID_STATE`。
- 從三個 repository 移除 `existsByConfigType`。
- `config-validator.json`：把頂層 `$or` 換成 spec 4.2 的 `$and` 區塊，三條規則照抄。

- [ ] **Step 4: 執行，確認通過**

Run: `mvn -q test -Dtest='ConfigCommandServiceTest,MongoSchemaTest,AdminConfigControllerTest,AssistantConfigRepositoryImplTest,SeedLoaderTest'`
Expected: PASS

### Task 6: 設定列表改為三步驟查詢

**Files:**
- Modify: `{main}infrastructure/mongo/ConfigQueryRepositoryImpl.java`（`listSummaries`；以 `activeVersionsQuery()`、`latestVersionQuery(String)` 取代 `summariesQuery()`）
- Test: `{test}infrastructure/mongo/ConfigQueryRepositoryImplTest.java`

**Interfaces:**
- Consumes：既有 `ConfigSummary.of(List<AssistantConfig>)`，不修改
- Produces（package-private static，供測試）：
  - `Query activeVersionsQuery()`：`status in [PUBLISHED, DRAFT, SCHEDULED]`，只 include 八個欄位
  - `Query latestVersionQuery(String configKey)`：`configKey` 相符，依 `version` 由大到小，`limit(1)`，同一組 include
  - 八個欄位：`configKey`、`configType`、`name`、`status`、`version`、`scheduledAt`、`updatedBy`、`updatedAt`

- [ ] **Step 1: 寫失敗的測試**

- `listSummariesShowsKeyWhoseOnlyVersionIsDiscarded`：插入 `instruction.dropped` 的 v1，狀態為 `DISCARDED`、名稱 `"Dropped"`；列表中有這個 key，名稱為 `"Dropped"`，`published` 與 `workingCopy` 都是 null。
- `listQueriesReadOnlySummaryFields`：`activeVersionsQuery().getFieldsObject().keySet()` 與 `latestVersionQuery(KEY).getFieldsObject().keySet()` 都只含上述八個欄位，不含 `content` 與 `changeNote`。這個測試取代既有第 100 行對 `summariesQuery()` 的斷言；第 101 行 `historyQuery` 的斷言保留。
- 既有三個 `listSummaries…` 測試不改。

- [ ] **Step 2: 執行，確認失敗**

Run: `mvn -q test -Dtest=ConfigQueryRepositoryImplTest`
Expected: 編譯失敗，`activeVersionsQuery` 不存在

- [ ] **Step 3: 實作 `listSummaries`**

1. 以 `mongo.findDistinct(new Query(), "configKey", AssistantConfigDocument.class, String.class)` 取得 key。
2. 以 `activeVersionsQuery()` 查出版本，依 `configKey` 分組。
3. 步驟 1 有、步驟 2 沒有的 key，各以 `latestVersionQuery` 查一筆。
4. key 集合取兩者的聯集，每組交給 `ConfigSummary.of`，排序沿用既有的 `configType`、`configKey`。

- [ ] **Step 4: 執行，確認通過**

Run: `mvn -q test`
Expected: PASS

- [ ] **Step 5: Phase B 結束：提供 commit 指令並停下**

```bash
git add backend/src
git commit -m "fix(config): revalidate due schedules, system.main only, indexed config list" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

等使用者確認已 commit，再開始 Phase C。

---

## Phase C：前端文字與規格文件

### Task 7: 版本歷程的來源文字

**Files:**
- Modify: `{fe}admin/promptLabels.ts:67-70`
- Test: `{fe}admin/__tests__/VersionViewer.spec.ts:60-61`、`{fe}admin/__tests__/VersionList.spec.ts:69`

- [ ] **Step 1: 改測試**

- `VersionViewer.spec.ts` 第 60 行預期 `以 10/19 08:00 的版本為底`，第 61 行預期 `以先前的版本為底`。
- `VersionList.spec.ts` 第 69 行改為 `not.toContain('為底')`。

- [ ] **Step 2: 執行，確認失敗**

Run: `cd frontend && npx vitest run src/features/ai-assistant/admin/__tests__/VersionViewer.spec.ts`
Expected: FAIL，實際文字仍是「套用自」

- [ ] **Step 3: 改 `versionMeta` 的兩個字串**

找得到來源版本時為 `` `以 ${formatTaipeiShort(source.updatedAt)} 的版本為底` ``，找不到時為 `'以先前的版本為底'`。

- [ ] **Step 4: 執行，確認通過**

Run: `cd frontend && npm test`
Expected: PASS

### Task 8: 規格文件

**Files:**
- Modify: `docs/spec/01-architecture.md`、`02-rest-api.md`、`04-mongodb-schema.md`、`05-error-codes.md`、`06-conversation-scenarios.md`
- Modify: `docs/superpowers/specs/2026-10-28-prompt-admin-simplify-design.md`
- Modify: `docs/superpowers/specs/2026-11-02-prompt-admin-review-fixes-design.md`（狀態改為「已實作」）

- [ ] **Step 1: 依 spec 第 6 節的表格逐列修改**

每一列都要改到，文字以 spec 對應章節為準：

- 01：`domain/exception/` 的類別清單補 `ErrorDetail`、`FieldDetail`、`WorkingCopyDetail`、`RunningEvalDetail`。
- 02 §3 的權限前提，照抄 spec 第 7 節的引言。
- 05 §1 的 `details` 表，照抄 spec 4.1 的表。
- 04 §2.3 的 validator，換成 `config-validator.json` 的實際內容。

- [ ] **Step 2: 檢查沒有遺漏**

```bash
grep -rn "套用自" docs/spec docs/superpowers/specs/2026-10-28-prompt-admin-simplify-design.md frontend/src
```

Expected: 沒有輸出。

```bash
grep -n "草稿不存在" docs/spec/05-error-codes.md
```

Expected: 沒有輸出。

- [ ] **Step 3: Phase C 結束：提供 commit 指令並停下**

```bash
git add frontend/src docs/spec docs/superpowers/specs
git commit -m "docs(spec): prompt admin review fixes; neutral source-version label" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
