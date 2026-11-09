# 選用自架 Android 驗證

預設使用 GitHub-hosted runner。自架驗證只供維護者明確配置的專用 Windows runner；此 PR 不會註冊或啟動 runner。

## 明確啟用

在倉庫設定以下 Actions variables，缺少任一項時工作不會排入自架 runner：

| 變數 | 用途 |
| --- | --- |
| LINZIN_SELF_HOSTED_ENABLED | 必須為 true |
| LINZIN_SELF_HOSTED_REPOSITORY | 明確允許的 owner/repository |
| LINZIN_SELF_HOSTED_ACTOR | 明確允許手動啟動與重新執行的帳號 |
| LINZIN_SELF_HOSTED_BRANCH | 已審查並允許的分支 |

手動 workflow_dispatch 同時提供 branch 及完整 expected_sha。工作與 PowerShell 腳本均核對 repository、actor、triggering_actor、ref、事件、checkout SHA 與輸入 SHA。PR、push、tag 或 schedule 不能排入自架 runner；不得改為信任所有 fork 或任意 contributor。

runner 標籤為 self-hosted、Windows、X64、linzin-v2-qa。工具與工作目錄採專用 D 槽配置，維護者需先依腳本設定準備環境。此配置是範例，不代表某一台電腦已完成驗證。

## 裝置隔離

所有 ADB 操作明確指定 loopback、專用 server port 與模擬器 serial。腳本核對記錄的 ADB／QEMU process、啟動時間、AVD 名稱、API 版本、emulator hardware／fingerprint 與 user 0；不符即停止。

腳本不會自動替換 ADB server 或選擇實體手機。PreflightOnly 僅檢查既有環境，不建置或安裝；執行完整驗證仍需通過所有手動事件與來源授權條件。

## 結果驗證

測試 parser 要求完整預期總數、無 failure／error／skip、唯一 JUnit 結果與正常 instrumentation 結束。媒體截圖事件不能計為測試成功，缺少報告或異常結束必須失敗。parser 與執行範圍防護有獨立回歸測試，不會因此啟動裝置。

公開 artifact 僅包含允許的驗證輸出；本機授權、runner 註冊資料、私鑰、工作站識別與實體手機收據不得提交。
