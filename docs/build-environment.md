# Android 建置環境

## 工具鏈

| 元件 | 固定版本 |
| --- | --- |
| JDK | Eclipse Temurin 25.0.5+8 |
| Gradle | 9.6.0 |
| Android Gradle Plugin | 9.4.1 |
| Android Command-line Tools | 23.0（build 16111833） |
| Android 平台 | platforms;android-37.0 |
| Android Build Tools | 36.0.0 |
| minSdk / compileSdk / targetSdk | 31 / 37 / 37 |
| Kotlin Compose plugin | 2.2.10 |
| Lifecycle runtime | 2.10.0 |

版本依專案設定固定；工具下載需通過腳本中的來源與 checksum 驗證。

## Windows 建置

在專案根目錄執行：

```powershell
pwsh -File ./scripts/install-android-toolchain.ps1
pwsh -File ./scripts/build-android.ps1
```

工具與快取預設置於 D 槽的獨立工具目錄，可透過兩支腳本的 `-ToolRoot` 指定另一個 D 槽目錄。環境設定只作用於目前程序及子程序，不修改系統環境變數。

預設建置任務：

```text
:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
--no-daemon --console=plain --continue
```

任何任務失敗仍返回非零退出碼。完整記錄位於忽略的 `.local/build-logs/`。直接執行 Gradle 前可載入 `scripts/Set-AndroidEnvironment.ps1`。

## 持續整合

GitHub Actions 是主要驗證途徑。建置工作會編譯應用程式及測試 APK、執行 JVM 測試與 lint；另一項工作在 API 35 Google APIs 模擬器執行 Android 測試及背景啟動檢查。API 35 是測試裝置版本，不會降低應用程式的 targetSdk。

Actions 以 commit SHA 固定，token 僅有 contents:read，checkout 不保留 credentials。CircleCI 為手動備援，必須明確提供分支及完整來源 SHA，且全部檢查通過後才產生驗證摘要。自架 runner 需另行配置及明確啟用，不能由外部 PR 啟動。

相關說明：[模擬器驗證](emulator-testing.md)、[CircleCI 備援](circleci-validation.md)、[自架驗證](self-hosted-validation.md)。

## 公開內容

不得提交本機環境檔、實體裝置識別碼、簽署金鑰、下載憑證、帳號資料、原始手機紀錄或私人截圖。驗證摘要應只包含來源版本、命令、測試總數與失敗狀態。
