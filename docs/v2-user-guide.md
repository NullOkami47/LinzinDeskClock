# LinzinDeskClock v2 使用與限制

> 2.0.3 改為使用者選擇時鐘左／右側，拖曳不再自動換邊。詳見 [2.0.3 說明](v2.0.3-changes.md)。

> 2.0.2 已更新鎖屏媒體、控制面板、行程詳細資訊與低電量保護，請先參閱 [2.0.2 更新與驗證紀錄](v2.0.2-changes.md)。下方媒體、行程與低電量說明已同步更新。

> 2.0.1 已更新背景自動開啟、預設充電條件及吸附點。請優先依 [2.0.1 修正與啟用說明](v2.0.1-fixes.md) 操作；以下保留 2.0 初版紀錄，不代表新版限制。

## 啟用自動桌面時鐘

1. 一般手機保留原有「不是大摺疊機？」手動入口。進入時鐘後，點一下畫面並開啟齒輪設定。
2. 在「自動桌面時鐘」開啟鎖定觸發監看；預設關閉，充電條件預設僅無線充電。可改為有線或無線、或不要求充電。
3. 通知權限只會在首次使用者啟用時要求；拒絕後，可從同區塊的通知設定重設。通知被封鎖時，背景點擊開啟備援無法顯示。
4. 將手機實體橫放，面朝上、相對桌面傾斜 30°–75°，然後鎖定。感測器最多執行 5 秒，需連續穩定 1.5 秒；移動或插電本身不會觸發。使用低通平滑、橫放軸向遲滯與穩定時間避免誤判，傾角範圍維持 30°–75°。
5. 若從本應用程式前景鎖定，會對既有視窗嘗試顯示時鐘；系統未允許時保留通知。若當時正在使用其他 App，符合條件後只顯示通知，30 秒內點擊可開啟鎖定時鐘。
6. 右上角退出或從底部 12 dp 區域向上滑至少 80 dp，即可結束；UI Lock 不會封鎖這兩個出口。此鎖定週期不再嘗試，實際解鎖後才重新待命。
7. 系統停止、強制停止、從最近使用項目移除或重新開機後，重新開啟設定並點「恢復監看」。不會在開機時偷偷重啟前景服務。

## 媒體與版面

設定中的「設定媒體通知存取」會開啟 Android 系統授權頁；只需要通知存取，不需要無障礙。授權後，雙區塊側面板的第三頁顯示系統媒體工作階段。只有播放器宣告支援的操作才可使用；無工作階段、缺少曲目或封面時有佔位狀態。只讀取內嵌媒體封面 Bitmap，不下載來源提供的任意 URI。

鎖定時鐘／螢幕保護程式會保留曲名、演出者、封面及進度，已授權的媒體控制仍可操作；舊的隱藏選項不再使用。UI Lock 會停用媒體操作與分隔線拖曳。鎖定工作階段不顯示個人行事曆行程、不彈出權限或設定對話框。

直接拖曳雙區塊中間分隔線，範圍 20%–80%；放開在 30%、50% 或 70% 附近的 ±5 百分點範圍，會吸附至對應比例。時鐘固定在設定選擇的左／右側，即使該區塊較小也不會自動換邊；切換左右保留分隔比例。比例與側別分開儲存；原本左右大區塊的設定可遷移。垂直滑動側面板依序切換月曆、天氣與媒體。

## 行程詳細資訊

在解鎖後的時鐘月曆頁點擊行程，可查看完整標題、起訖時間、地點、行事曆及說明，並透過「在行事曆中開啟」查看對應項目。內容較長時可捲動。需授予行事曆讀取權限；UI Lock 開啟時無法點擊，鎖定期間不顯示個人行程。

## 低電量保護

齒輪設定中的「低電量保護」可選關閉、自動降低亮度或退出時鐘，門檻可設為 5–50%。預設為未接上電源且電量低於 20% 時，將時鐘視窗亮度限制在 8%；若原本設定更暗，維持原亮度。接上電源或電量恢復後會還原原本亮度，不改系統亮度設定。

「退出時鐘」會結束時鐘並停止監測。仍符合低電量退出條件時，返回引導頁或系統重啟服務均不會恢復監測；充電、電量恢復或更改保護動作後，可重新開啟 App 或從設定恢復。

## 螢幕保護程式

在設定點「設定系統螢幕保護程式」，選擇 Linzin Desk Clock，再由系統選擇充電或底座啟動條件。此路徑由 Android DreamService 管理，只會在已觀察到的鎖定事件後 5 秒剩餘期限內檢查一般手機的姿勢與充電選項，並共用單次鎖定週期限制。若廠商只在更晚才啟動 Dream，或根本沒有先發送熄屏事件，此週期不顯示時鐘，請改用監看通知或手動入口。監看與 Dream 不會在同一週期各重試一次：若前景監看已開始掃描，Dream 等待該次掃描的結果並共用原期限。

退出／失敗後，不因系統重新啟動 Dream 就重新掃描。如果監看未執行且程序被系統殺掉，應用程式可能收不到解鎖事件；此時採保守策略，解鎖後重新開啟 App 才會重新待命。

## 平台界線

- 沒有 requestDismissKeyguard、自動輸入 PIN、生物辨識繞過、overlay、full-screen intent、AccessibilityService、root 或新 Shizuku 整合。無需 ADB 授權安裝使用。
- 前景服務只維持使用者開啟的事件監看，不能取得背景 Activity 啟動特權。Android 12 起限制背景啟動前景服務；本實作只從可見設定操作啟動。
- Android 14 起使用 specialUse 前景服務類型與宣告用途。Google Play 上架需要該用途的審查，本機可建置不代表已通過商店政策審查。
- Android 15/16 的背景 Activity／PendingIntent 規則不構成繞過路徑；通知使用不可變的明確 Activity PendingIntent，只有使用者點擊才啟動。
- Android 16 大螢幕可能忽略方向限制；Android 17 target 37 不再提供同樣的 opt-out。實際物理橫放仍由感測向量判斷，不能用 requestedOrientation 當作物理姿勢證據。
- 第三方 DreamService 不是廠商 AOD 替代品。廠商可能不提供螢幕保護程式入口，或在熄屏時不傳送感測事件。此時 5 秒後釋放資源，維持系統畫面；不持續喚醒、不重試。
- 沒有鉸鏈感測器並不保證裝置一定是直板機；具有鉸鏈 sensor／feature 的裝置排除在新監看之外，廠商未揭露能力的裝置仍需實機確認。
- 原本的廠商 cmd device_state 行為與權限限制保留；未宣稱新增 shell 特權或讓不支援的折疊機指令必然成功。

## 官方參考資料（2026-11-07 查閱）

- Android Activity security: https://developer.android.com/guide/components/activities/secure-bal
- FGS types / specialUse: https://developer.android.com/develop/background-work/services/fgs/service-types
- FGS background restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- DreamService: https://developer.android.com/reference/android/service/dreams/DreamService
- Android 15: https://developer.android.com/about/versions/15/behavior-changes-15
- Android 16: https://developer.android.com/about/versions/16/behavior-changes-16
- Android 17: https://developer.android.com/about/versions/17/behavior-changes-17
- Sensors: https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview
