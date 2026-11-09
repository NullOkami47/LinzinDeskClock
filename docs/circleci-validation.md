# CircleCI 手動備援驗證

主要驗證使用 GitHub-hosted CI；失敗後才手動執行此備援。設定不包含個人專案 ID 或帳號，維護者需將倉庫連接到自己的 CircleCI 專案。

## 啟動條件

在 Trigger Pipeline 為設定來源與 checkout 選擇同一個已檢查分支，並指定：

| 參數 | 值 |
| --- | --- |
| run-fallback | true |
| expected-branch | 完整分支名稱 |
| expected-sha | 已推送的完整 40 字元 commit SHA |

預設不啟動。workflow 僅接受 API 手動事件、非空分支／SHA 與相符的來源分支；執行腳本再次核對 CircleCI 環境、分支、HEAD 與 expected-sha。PR、push、tag 或排程不能自行啟用備援，驗證失敗會在下載、建置或裝置操作前停止。

## 檢查內容

- 固定 Temurin 25.0.5+8、SDK 37.0、Build Tools 36.0.0。
- 建置應用程式及測試 APK，執行完整 JVM 測試及 lint。
- 在全新 API 35 Google APIs x86_64 AVD 執行完整 instrumentation，以及四項背景啟動／退出／重建／再啟動檢查。
- 嚴格核對測試總數、零失敗／錯誤／跳過、正常結束、lint 零錯誤與來源 commit。
- 全部成功後產生含來源 SHA、APK SHA-256 與結果的摘要。

確切預期測試數由 CI 與驗證腳本維護；若新增測試，必須同步更新，不能藉降低總數或容許 skip 通過。

雲端產物只使用測試簽章，沒有正式簽署、發布或實體手機操作。私有金鑰及工具快取不得上傳為 artifact。
