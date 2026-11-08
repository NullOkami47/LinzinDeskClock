# LinzinDeskClock v2 實作與驗證報告

## 交付內容

本次從 upstream de82efc 建立 feat/desk-clock-v2，於 D 槽實作。原指定工作目錄為空，沒有原有使用者未提交變更。最低 Android 12（API 31）及 target/compile SDK 37 保留，版本更新為 2.0（versionCode 2）。

MainActivity 原 5,074 行，設定與 DeskClockScreen 已移至 settings/ui 目錄，新增感測、鎖定協調、退出、DreamService、MediaSession 與可拖曳版面均獨立成檔。舊錶盤／月曆／天氣資源與繪圖實作保留。

## 架構與權限

- GravityOrientation／SensorTiming／TiltDetector：從自然顯示方向的重力向量計算實體橫放與 30°–75° 傾角；重力 sensor 優先，加速度計備援，濾波與 axis hysteresis／時間穩定判定拒絕不可靠資料。
- ClockActivationStateMachine：只有觀察到鎖定才開始；單一絕對期限 5 秒、連續 1.5 秒、資料缺口重設、解除鎖定取消。ClockCycleStore 同步保存 boot／cycle／attempt／suppression；單次短效 offer 不能跨 boot 重用。
- AutoClockService：使用者從可見設定啟動的 specialUse 前景事件監看；無常駐感測。單次掃描持有最多 5 秒 PARTIAL_WAKE_LOCK，取消／錯誤／銷毀清理。
- Main host 只嘗試調整既有前景工作視窗；背景使用明確不可變 PendingIntent 通知。獨立 ClockSessionActivity 維持 keyguard，Dream 可共用仍在執行的掃描。沒有 cached eligibility、逾時重試、鎖定解除 API 或新增特權。
- ResizableDualLayout：20%–80% 連續拖曳，放開才中央吸附，固定 composition 槽位保留 state。SplitLayoutPreferences 遷移舊左右大區塊設定。
- MediaSessionRepository：NotificationListenerService 授權後監聽真實 active sessions、metadata、playback 與 destroyed 回呼，操作時重新驗證權限／session／actions；封面採有界記憶體快取。AlbumArtBackground 保留完整原圖，僅鏡像延伸區模糊。鎖定隱私預設開啟。
- 新 uses-permission：POST_NOTIFICATIONS、FOREGROUND_SERVICE、FOREGROUND_SERVICE_SPECIAL_USE；沿用既有 WAKE_LOCK。Dream 與 notification listener 透過各自 bind permission 保護。沒有新增 Accessibility、SYSTEM_ALERT_WINDOW、full-screen intent、root 或 Shizuku。
- 新 dependency：lifecycle-runtime-compose；lifecycle-runtime-ktx 對齊 2.10.0。其餘原版本保留。Build Tools 固定 36.0.0，Gradle 9.6.0、JDK 25.0.5+8；wrapper JAR 依官方 checksum 修復。

## 自動化結果

2026-11-08 本機完整命令：

    . ./scripts/Set-AndroidEnvironment.ps1
    ./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --console=plain --continue

結果：BUILD SUCCESSFUL（exit 0）。120 個 JVM tests，0 failures/errors。Lint 0 errors、94 warnings、4 hints；仍含既有 deprecated API、圖示與資源建議，沒有把 warnings 稱為零。

隔離 Android 15／API 35 Google APIs 模擬器、1280×720、硬體加速，執行 AndroidJUnitRunner：28/28 PASS，無 skip。覆蓋出口按鈕與底部長滑／誤觸、UI Lock、三種既有錶盤家族、divider／pager競爭與 state 保存、真實 MediaSession metadata／transport／銷毀／撤權、PixelCopy 封面幾何／鏡像／延伸模糊／轉場。

首次裝置測試有一項 upstream ExampleInstrumentedTest 套件斷言錯誤（com.example 而非 com.linzin）；已修正並完整重跑。較早 lint 曾在來源修改期間發生 FIR 內部例外；固定來源重跑成功，未停用 lint。

本機原始紀錄在 .local/final-build.log、.local/instrumentation-api35-final.log 與 D:/codex-tools/linzin-android/evidence。封面驗證 PNG 在 .local/media-artwork，機器可讀摘要在 verification-local.json。GitHub-hosted CI 執行結果另補在交付紀錄中。

## 驗收對照與限制

| 需求 | 目前證據／界線 |
| --- | --- |
| 折疊 30°–120°、>150°／<=5° reset | 原有 onSensorChanged 分支範圍與 shell commands 保留；新增 mode 標記及非折疊隔離。OEM 真機尚未執行。 |
| 一般手機 eligibility／充電來源／物理橫放 | JVM 向量、左右方向、自然橫向、無效訊號、plug source、邊界案例通過；實際熄屏感測需真機。 |
| 鎖定事件、5 秒、1.5 秒、不可重試 | 狀態機、真實儲存 adapter、跨 boot、失敗寫入、pending broker 共覆蓋時序；Android 廠商事件交付仍需真機。 |
| 安全鎖定畫面與退出 | Activity／Dream 與明確通知路徑已實作；按鈕、手勢、UI Lock 模擬器通過。個人手機依要求只安裝，不啟動、不做解鎖測試。 |
| 雙面板與 state／persistence | JVM 幾何／偏好 36 項及 Compose 10 項測試通過。 |
| 媒體／封面／權限撤回 | 真實 Android framework MediaSession 與 PixelCopy 共 10 項模擬器測試通過；Spotify、YouTube Music 與 OEM 仍需使用者實機驗收。 |
| 舊外觀與功耗功能 | 舊錶盤 drawable 與 face renderer 保留，三個錶盤家族已 render；長時間 OLED／OEM timeout 實機清單保留。 |
| 設定與三語 | 英文、繁中、簡中所有新增資源對齊；本機 resource compile 通過。 |
| SDK 31/37、編譯／測試 | 本機 debug、unit、lint、AndroidTest APK 通過；min31/target37 不降級。 |

實體裝置限制及操作步驟見 v2-user-guide.md；逐項真機驗收見 v2-device-checklist.md。自動 AOD 替代、任意背景全螢幕喚起、廠商保證與原有 shell 特權不在可普遍保證範圍；已提供安全可行的通知／手動路徑。螢幕保護程式若未於原鎖定期限內被系統建立，該週期會結束，不另開新嘗試。

## 手機安裝與 CI 交付

ADB 已將本機驗證過的 debug APK 安裝至已連接手機的 user 0，回應 Success。唯讀確認 versionName=2.0、versionCode=2、minSdk=31、targetSdk=37；stopped=true、notLaunched=true，沒有 App 程序。沒有發出 App 啟動、instrumentation、權限變更或畫面操作命令。安裝 SHA-256：7437ed7941576badd4e2bb25ef9ca266ea5433544d84a4e599f1dbb58855beb7。此為 debug 簽署版本，不是 Play 商店發行包。

GitHub-hosted run 42399769799 的 debug/test/lint 成功，模擬器環境前提失敗（顯示邊界與預裝媒體工作階段）；run 42403706282 的 debug/test/lint 成功，KVM 準備失敗。已修正專用顯示／媒體 fixture 與 KVM udev 套用時序，修正版結果將以實際 run 收據補充。

依使用者偏好已準備 self-hosted 備援，但 runner 註冊／背景啟動遭自動核准審查以 blocked by policy 拒絕，未提供更具體理由；未繞過、未取得註冊 token、沒有實際 self-hosted run。保留手動 dispatch 工作流程，不自動排隊沒有 runner 的工作。
