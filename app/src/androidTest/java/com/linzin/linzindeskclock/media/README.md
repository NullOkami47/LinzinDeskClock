# 媒體整合測試

測試使用 Android framework MediaSession、程式產生的封面及固定文字，驗證正式 MediaSessionRepository 與 Compose 媒體畫面，不依賴私人通知、音樂帳號或真實播放紀錄。

## 執行條件

僅允許可拋棄的專用 API 35 Google APIs 模擬器。測試需要明確的 `linzinDedicatedMediaEmulator=true` instrumentation 參數，且先核對 emulator hardware、fingerprint、API 版本及 user 0。一般測試裝置或缺少參數時會拒絕執行會調整系統狀態的 fixture。

本機所有 ADB 命令必須指定已核對的 server 與 emulator serial；使用者實體手機不能作為備援目標。標準完整執行由 [CI 與建置腳本](../../../../../../../../docs/build-environment.md) 管理。

## 權限與清理

fixture 僅調整本 App 的通知監聽元件，記錄原授權與其他 listener，結束時在 finally 還原；還原失敗會使測試失敗。Repository 本身不取得 shell identity 或 MEDIA_CONTENT_CONTROL，因此撤權測試會經過正式授權路徑。

工作階段、callback、Compose host 與 pending 操作都需清理。若模擬器崩潰或 instrumentation 被強制終止，finally 無法保證執行，重新使用前需在同一個專用模擬器檢查殘留授權。

## 驗證範圍

- 實際播放／暫停／上一首／下一首／seek callback，以及不支援、過期、受鎖定或已撤權命令的拒絕。
- metadata 更新、多工作階段選擇、銷毀、缺少封面、圖片縮放與重啟。
- PixelCopy 驗證完整封面比例、鏡像模糊延伸、轉場、裁切與缺圖清理。
- 正式畫面的控制面板比例、進度點座標、UI Lock、鎖定畫面內容、防烙印遮黑及觸控重設。

等待都有期限，失敗及跳過不能算作通過。PixelCopy 截圖只包含合成 fixture，寫入測試裝置快取，由 CI 收集到忽略的本機輸出或有期限的測試 artifacts。

以上結果不代表所有 OEM／外部播放器相容、真實聲音播放、長期記憶體穩定性或 OLED 耐久性；相關手動項目見專案的真機驗收清單。
