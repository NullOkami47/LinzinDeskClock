# Android 建置環境

版本與官方索引查核：2026-11-07；本機驗證持續至 2026-11-08（Asia/Hong_Kong）。建置工具、下載、快取、暫存檔均放在 D 槽。功能來源碼由主工作同步修改，以下結果僅代表各次執行所讀取的工作目錄狀態。

## 固定工具鏈

| 元件 | 版本／設定 | 證據與用途 |
| --- | --- | --- |
| JDK | Eclipse Temurin 25.0.5+8，Windows x64 | 官方 Adoptium API 與 archive SHA-256 校驗；本機與 CI 同版本 |
| Gradle | 9.6.0 | 官方發行索引已列出；distribution 與 wrapper JAR 分別校驗 |
| Android Gradle Plugin | 9.4.1 | Google Maven POM／metadata 可取得；官方相容性表要求 Gradle 9.6.0，支援 API 37 |
| Android Command-line Tools | 23.0，build 16111833 | 官方 SDK repository；Windows archive SHA-1 校驗 |
| Android 平台 | `platforms;android-37.0`，revision 2 | 官方穩定 SDK 索引中的確切識別名稱 |
| Android Build Tools | 36.0.0 | AGP 9.4 的官方最低／預設版本；於 app Gradle 明確固定 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 31 | 保留產品要求 |
| Kotlin Compose plugin | 2.2.10 | 與 AGP 9.4.1 POM 的內建 Kotlin plugin 版本相符 |
| Lifecycle | 2.10.0 | runtime-ktx 與 runtime-compose 明確對齊既有解析結果；不是宣稱最新版 |

目前依賴圖也解析到 lifecycle-viewmodel 2.10.0、savedstate 1.4.0；Dream 的 owner API 已通過 Kotlin 編譯，無須為缺少符號另加依賴。Google Maven 已有較新穩定版 Lifecycle 2.11.0，但本次採用既有依賴圖的 2.10.0，避免無關升級。

## Windows 使用方式

於專案根目錄執行：

```powershell
pwsh -File .\scripts\install-android-toolchain.ps1
pwsh -File .\scripts\build-android.ps1
```

安裝腳本可重複執行，會重用並校驗下載的 archive，保留已有檔案。預設根目錄為 `D:\codex-tools\linzin-android`；可指定另一個 D 槽的 `-ToolRoot`。若 archive 校驗失敗會停止，由操作者檢查，不會刪除既有內容。

建置入口會設定 JDK、SDK、ANDROID_USER_HOME、GRADLE_USER_HOME、TMP/TEMP，以及 Java 的 user.home／java.io.tmpdir；不修改全系統環境變數。預設執行：

```text
:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
--no-daemon --console=plain --continue
```

`--continue` 讓可獨立完成的檢查繼續執行；任何失敗仍會返回非零 exit code。完整記錄位於忽略的 `.local/build-logs/`。使用現有 PowerShell 工作階段時可指定任務：

```powershell
& .\scripts\build-android.ps1 -Tasks @(':app:compileDebugKotlin', ':app:testDebugUnitTest') -LogPath 'D:\codex-tools\linzin-android\evidence\compile-and-test.log'

# 直接操作 wrapper 前先載入環境：
. .\scripts\Set-AndroidEnvironment.ps1
.\gradlew.bat --version
```

IDE 如需本機 SDK，可在忽略的 `local.properties` 指定：

```properties
sdk.dir=D\:/codex-tools/linzin-android/sdk
```

Windows 的磁碟代號冒號必須跳脫，否則 lint 會回報 PropertyEscape。

## 已確認的工具問題

- 初始 wrapper 無法啟動，原因是未設定 JAVA_HOME 且 PATH 沒有 Java。指定已驗證的 JDK 後解決，未降低 Android API。
- SDK 23 的 Windows `sdkmanager.bat` 在本環境會將含分號的套件名稱拆開，並可能以 exit 0 結束但沒有安裝完整套件。安裝腳本直接使用同套工具的 `android.exe --no-metrics --sdk=… sdk install …`，且檢查實際 android.jar／aapt2.exe。
- Gradle daemon criteria 保留 Java 25 並指定 Adoptium；移除原本跨平台自動下載 URL，由安裝腳本／CI 明確提供 JDK。
- 原 wrapper JAR 與官方 9.6.0 checksum 不同，已換成經官方 checksum 驗證的 9.6.0 JAR；未改動 wrapper 啟動腳本。
- 主工作修改 AutoClockTriggerController.kt 的同一輪 lint 曾出現 Kotlin FIR 內部例外。該輪記錄與修改時間均有保存；此結果不能單獨證明工具鏈有可重現缺陷。

## GitHub-hosted CI 路徑

`.github/workflows/android.yml` 使用固定 commit SHA 的公開 Actions，權限為 contents:read，不保留 checkout credentials。

1. `debug-test-lint`：ubuntu-latest，JDK 25.0.5+8、SDK 37.0、Build Tools 36.0.0；建置 app／test APK、JVM 測試與 lint，保存報告與 debug APK。
2. `instrumentation-api-35`：前一個 job 成功後，在另一個 ubuntu-latest runner 啟用 KVM；以穩定 Google APIs x86_64 API 35 emulator 執行 connectedDebugAndroidTest，保存 instrumentation 報告。API 35 是測試裝置版本，app 的 compileSdk／targetSdk 仍為 37。

API 35 系統映像已在官方 Google APIs system-image 索引確認存在。工作流程已通過 actionlint 1.7.12 本機驗證；這不能代替實際 GitHub runner 執行。

2026-11-07 的 authenticated gh 查核顯示登入帳號為 NullOkami47，upstream linzin86/LinzinDeskClock 只有 pull 權限，使用者帳號沒有此專案的 fork。現有公開 fork 屬於其他使用者。此子工作沒有建立 fork、推送、commit 或觸發 CI；主工作仍需決定／授權 fork 與功能分支推送。優先執行 GitHub-hosted CI；只有它實際失敗後才評估適合的 self-hosted runner，沒有預設自動切換。

## 驗證記錄

固定紀錄目錄：`D:\codex-tools\linzin-android\evidence`。

| 記錄 | 結果 |
| --- | --- |
| baseline-no-java.log | 原環境 wrapper 無 Java，未能啟動 |
| baseline-gradle-version.log | Gradle 9.6.0／Temurin 25.0.5+8 成功啟動 |
| baseline-unit-test.log | SDK 尚未安裝／授權時停止；不是測試執行結果 |
| installer-validation.log | 安裝腳本重跑成功，列出 SDK 37.0 revision 2 與 Build Tools 36.0.0 |
| configured-build-01.log | 來源碼尚在修改；MediaWidget 的隱含 maxHeight receiver 編譯錯誤，已回報主工作 |
| configured-build-02.log | debug／instrumentation APK 產出，72 個 JVM tests 通過；lint 因本機 properties 跳脫失敗，已修正 |
| configured-build-03.log | debug／instrumentation APK 與 JVM tests 完成；lint 在來源碼同步修改期間發生 FIR 內部例外 |
| resolved-dependencies.log | 確認 Lifecycle 2.10.0、SavedState 1.4.0 |
| workflow-actionlint.log | actionlint exit 0（成功時無診斷輸出） |

最終重跑結果由下方更新。APK 路徑為 `app/build/outputs/apk/debug/app-debug.apk` 與 `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`。Instrumentation APK 編譯成功不代表裝置測試已執行；實機／emulator 執行與 hardware acceptance 尚未由本機驗證。

## 官方資料

- [AGP 9.4 相容性與發行說明](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
- [Google Maven AGP 發行索引](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml)
- [Gradle 發行索引及 distribution／wrapper checksums](https://services.gradle.org/versions/all)
- [Gradle 9.6.0 Java 相容性](https://docs.gradle.org/9.6.0/userguide/compatibility.html)
- [Adoptium JDK 25 Windows x64 發行資料](https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse)
- [Android SDK 官方套件索引](https://dl.google.com/android/repository/repository2-3.xml)
- [Android CLI](https://developer.android.com/tools/agents/android-cli)
- [Google APIs system-image 索引](https://dl.google.com/android/repository/sys-img/google_apis/sys-img2-3.xml)
- [Android Emulator Runner 官方文件](https://github.com/ReactiveCircus/android-emulator-runner)

## 整合驗證更新

固定來源的 configured-build-04.log 及專案 .local/final-build.log 均為 BUILD SUCCESSFUL。最終本機 JVM 120 tests 全數通過；API 35 隔離模擬器 28 tests 全數通過、無跳過。Lint 0 errors、94 warnings、4 hints。手機安裝結果為 Success，版本 2.0／versionCode 2，套件狀態 stopped=true、notLaunched=true，未啟動。GitHub-hosted 與 self-hosted 執行狀態另見 v2-implementation-report.md 與 self-hosted-validation.md。
