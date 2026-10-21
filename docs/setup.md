# 開發環境安裝

> 適用：Windows 11，PowerShell

## 需要的環境

| 元件 | 版本 | 用途 |
|---|---|---|
| JDK | 25 | 後端 |
| Maven | 3.9.x | 後端建置（專案也附 Maven Wrapper，不裝也能建置） |
| Node.js | 24 | 前端 |
| Docker Desktop | 最新版 | MongoDB |
| OpenAI API key | 本專案專用 key | GPT 呼叫 |

---

## 1. 安裝 JDK 25

```powershell
winget install EclipseAdoptium.Temurin.25.JDK
Get-ChildItem "C:\Program Files\Eclipse Adoptium"
```

第二行應該會看到類似 `jdk-25.0.4.101-hotspot` 的資料夾。

### 讓系統改用 JDK 25

如果電腦上已經有其他版本的 JDK（例如 21），`JAVA_HOME` 可能還指向舊版，需要手動調整：

1. 按 Win 鍵搜尋「**編輯系統環境變數**」→「環境變數」。
2. 在**系統變數**裡找到 `JAVA_HOME`，改成 JDK 25 的路徑，例如 `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`。
3. 編輯**系統變數**的 `Path`：
   - 刪掉 `...jdk-21...\bin` 這類舊版路徑，或改成 `%JAVA_HOME%\bin`。
   - 確認 `%JAVA_HOME%\bin` 排在其他 Java 路徑前面。

> 也可以改從 [adoptium.net](https://adoptium.net/) 下載 JDK 25 的 MSI 安裝檔，安裝時勾選「Set JAVA_HOME variable」和「Add to PATH」，就會自動完成上面的設定。

## 2. 安裝 Maven

winget 上沒有 Maven，需要手動安裝：

```powershell
# 下載並解壓縮
Invoke-WebRequest -Uri https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip -OutFile "$env:TEMP\maven.zip"
New-Item -ItemType Directory -Force C:\tools | Out-Null
Expand-Archive "$env:TEMP\maven.zip" -DestinationPath C:\tools

# 設定使用者環境變數
[Environment]::SetEnvironmentVariable("MAVEN_HOME", "C:\tools\apache-maven-3.9.16", "User")
$p = [Environment]::GetEnvironmentVariable("Path", "User")
[Environment]::SetEnvironmentVariable("Path", "$p;C:\tools\apache-maven-3.9.16\bin", "User")
```

> `dlcdn.apache.org` 只保留最新版。如果下載出現 404，把網址前段換成 `https://archive.apache.org/dist/maven/maven-3/3.9.16/binaries/`。

## 3. 建立本專案專用的 OpenAI key

**不要沿用個人常用的 key。** 另外建一把專用 key，萬一外洩時損失有上限，而且可以單獨撤銷。

1. 登入 [OpenAI Platform](https://platform.openai.com/)。
2. 建立一個新的 Project，例如 `marketing-advisor-poc`。
3. 在這個 Project 裡建立 API key：
   - 權限選 **Restricted**。
   - 只開放 Chat Completions（`/v1/chat/completions`）需要的權限，其他設為 None。LangChain4j 的 `langchain4j-open-ai` 使用這個端點。
4. 在 Project 的 Limits 設定**每月預算上限**和**用量通知**。
5. POC 結束後，到後台**撤銷這把 key**。

> OpenAI 後台的選項名稱可能隨版本調整，以實際畫面為準。

## 4. 設定 `backend/.env`

key 只放在專案的 `backend/.env`，由後端在啟動時讀取。**不要設成 Windows 環境變數**，否則電腦上所有程式都讀得到。

```powershell
New-Item -ItemType Directory -Force C:\Users\user\Desktop\chat-boilerplate\backend | Out-Null
notepad C:\Users\user\Desktop\chat-boilerplate\backend\.env
```

在記事本裡輸入以下內容後存檔：

```properties
OPENAI_API_KEY=sk-...
OPENAI_MODEL=<模型名稱>
```

- `.env` 已列入 `.gitignore`，不會被 commit。可以用 `git status` 確認它沒有出現在清單裡。
- 不要把這個檔案的內容貼到聊天、截圖或文件裡。

### 如果之前已經把 key 設成 Windows 環境變數

用以下指令移除：

```powershell
[Environment]::SetEnvironmentVariable("OPENAI_API_KEY", $null, "User")
[Environment]::SetEnvironmentVariable("OPENAI_MODEL", $null, "User")
```

如果那把 key 不是專用 key，建議也到 OpenAI 後台撤銷它。

## 5. 重開 VS Code 並驗證

環境變數改完後，要**完全關閉再重開 VS Code**，終端機才讀得到新的設定。

```powershell
java -version                    # 應顯示 openjdk version "25..."
mvn -v                           # 應顯示 Apache Maven 3.9.16，且 Java version: 25
node -v                          # 應顯示 v24.x
docker info                      # 確認 Docker Desktop 有在執行
echo $env:OPENAI_API_KEY         # 應該是空的（key 只放在 backend/.env）
```

## 6. 專案內的 jar（`backend/lib-repo/`）

公司環境連不到外部 Maven repo，所以 LangChain4j 的 jar 放在專案內的 `backend/lib-repo/`（設計見 [01-architecture.md](spec/01-architecture.md) 7.1）。**平常建置不需要做任何事**，以下步驟只有第一次建立或升級 LangChain4j 版本時才需要，而且要在能連網的電腦上做。

### 6.1 下載並複製進專案

```powershell
cd C:\Users\user\Desktop\chat-boilerplate\backend

# 1. 列出依賴樹，記下 LangChain4j 底下有哪些不是 Spring Boot 帶進來的 artifact
.\mvnw dependency:tree

# 2. 下載到暫存資料夾（不影響 ~/.m2）
$tmp = "$env:TEMP\l4j-repo"
.\mvnw dependency:get "-Dartifact=dev.langchain4j:langchain4j:1.22.0" "-Dmaven.repo.local=$tmp"
.\mvnw dependency:get "-Dartifact=dev.langchain4j:langchain4j-open-ai:1.22.0" "-Dmaven.repo.local=$tmp"

# 3. 保留目錄結構複製進 lib-repo
New-Item -ItemType Directory -Force .\lib-repo\dev | Out-Null
Copy-Item -Recurse -Force "$tmp\dev\langchain4j" .\lib-repo\dev\
# 第 1 步記下的其他 artifact 也照同樣方式複製，groupId 的「.」對應到資料夾層級
# 例如 groupId 是 org.example，就把 "$tmp\org\example" 複製到 .\lib-repo\org\

# 4. 移除 Maven 的快取紀錄檔
Get-ChildItem .\lib-repo -Recurse -Include _remote.repositories,*.lastUpdated,resolver-status.properties | Remove-Item
```

> 升級版本時，先刪掉 `lib-repo` 裡舊版本的資料夾，再照上面步驟重做，並同步修改 pom.xml 的版本號。

### 6.2 確認 jar 都從 lib-repo 取得

開發機連得到網路，`lib-repo` 少放檔案時 Maven 會默默改從 Maven Central 下載，建置照樣成功，要到公司環境才會失敗。用以下方式確認：

```powershell
# 清掉本機快取的 LangChain4j，再重新解析依賴
Remove-Item -Recurse -Force "$env:USERPROFILE\.m2\repository\dev\langchain4j"
.\mvnw -q dependency:resolve

# 每一行都應該是 project-local；出現 central 代表 lib-repo 少了檔案
Get-ChildItem "$env:USERPROFILE\.m2\repository\dev\langchain4j" -Recurse -Filter _remote.repositories | Select-String "="
```

放進 `lib-repo` 的其他 artifact 也照同樣方式檢查。

## 7. 執行 POC

**用 VS Code（建議）**：按 **F5**，選「POC：全部啟動並開啟 Edge」，會依序啟動 MongoDB、後端、前端，然後開啟瀏覽器。其他操作（停止 MongoDB、重設資料、跑測試、查看稽核紀錄）在 `Ctrl+Shift+P` →「Tasks: Run Task」中選擇。設定在 `.vscode/tasks.json`、`.vscode/launch.json`。

**手動執行**：需要開三個 PowerShell 視窗（或 VS Code 的三個終端機分頁）。

```powershell
# 1. MongoDB（replica set，第一次啟動約 20 秒完成初始化）
cd C:\Users\user\Desktop\chat-boilerplate
docker compose up -d
docker compose ps                  # mongo 顯示 (healthy) 才繼續

# 2. 後端（另開一個視窗），http://localhost:8090
cd C:\Users\user\Desktop\chat-boilerplate\backend
.\mvnw spring-boot:run

# 3. 前端（另開一個視窗），http://localhost:5173
cd C:\Users\user\Desktop\chat-boilerplate\frontend
npm install                        # 只有第一次或 package.json 改變時需要
npm run dev
```

啟動後：

- 後端 log 出現 `seed completed: 6 configs published`（第一次啟動）或 `seed skipped`（之後的啟動）。
- 瀏覽器開 `http://localhost:8090/actuator/health`，應顯示 `{"status":"UP"}`。
- 瀏覽器開 `http://localhost:5173`，以下列任一帳號登入，密碼都是 `poc1234`：

| 帳號 | 角色 | 能做什麼 |
|---|---|---|
| `fc.officer` / `fc.supervisor` | 理專經辦 / 理專主管 | 360 頁面使用小幫手 |
| `ao.officer` / `ao.supervisor` | AO 經辦 / AO 主管 | 同上，範圍不同 |
| `branch.mgr`、`region.north`、`hq.viewer` | 分行主管、區域、總行 | 同上，範圍不同 |
| `backoffice.admin`、`it.admin` | 後台管理、IT 管理 | 小幫手（只看基本輪廓）＋ Prompt 管理 |

> **Port**：後端預設 8090（本機的 8080 常被其他程式占用）。要改用其他 port 時，後端設 `$env:SERVER_PORT = "9000"`，前端啟動前設 `$env:VITE_API_TARGET = "http://localhost:9000"`。

### 7.1 重設資料

想回到初始狀態（例如 demo 前）：

```powershell
docker compose down -v             # 連同資料一起刪除
docker compose up -d               # 重新建立；後端下次啟動會重新寫入 seed
```

### 7.2 查看稽核紀錄

```powershell
docker compose exec mongo mongosh copilot --quiet --eval "db.COLL_ICRM_AI_ASSISTANT_AUDIT.find({}, {userId:1, customerId:1, question:1, createdAt:1}).sort({createdAt:-1}).limit(5)"
```
