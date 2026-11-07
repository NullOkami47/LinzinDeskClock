# Android emulator instrumentation QA 交接

本工作只準備 emulator。2026-11-08（UTC+08:00）已實測 API 35 開機、adb 連線、Package Manager 與橫向畫面。主工作仍負責最終 build 與之後的 instrumentation；本工作沒有安裝 APK、啟動 LinzinDeskClock 或執行任何測試。

## 裝置與目前狀態

| 項目 | 值 |
| --- | --- |
| AVD | `Linzin_API35_GoogleApis_QA` |
| **唯一允許的 adb serial** | **`emulator-5580`** |
| **專用 adb server** | **`127.0.0.1:5038`** |
| Emulator console / device adb port | `5580` / `5581` |
| gRPC | `8555`，啟用 JWT 驗證 |
| 系統映像 | `system-images;android-35;google_apis;x86_64`，revision 9 |
| Android / ABI | Android 15、API 35、x86_64 |
| Emulator | 37.2.13.0，build 16740729 |
| CPU / RAM | 2 個虛擬核心、2048 MB guest RAM |
| 加速 | WHPX；GPU 使用 `software` |
| 實際橫向畫面 | **1280 × 720**、320 dpi、`ROTATION_90` |
| adb / launcher / QEMU PID（本次啟動） | `25908` / `14892` / `13876` |
| 開機耗時 | 日誌回報 95644 ms |

PID 會隨重啟改變；以 `D:\codex-tools\linzin-android\emulator-qa\current-run.json` 及即時檢查為準。只有一個本工作建立的 emulator 在執行，未另外下載或啟動 API 31。

主工作已告知有個人手機連線。**所有 adb 裝置變更都必須明確帶上 `-H 127.0.0.1 -P 5038 -s emulator-5580`。不得使用未限定裝置的 `connectedDebugAndroidTest`。不得安裝、執行、測試或變更實體手機；手機的最終 APK 安裝由主工作另行處理，且不可啟動 App。**

## 即時驗證與環境

以下使用 PowerShell 7；只檢查 emulator，不安裝或啟動 App：

```powershell
& 'D:\codex-tools\linzin-android\emulator-qa\Get-LinzinEmulatorStatus.ps1'
```

交付時結果為 `Ready: true`、`State: device`、`BootCompleted: 1`、API `35`、`1280x720`，`LinzinPackages` 為空。`Ready` 只代表 emulator 可供測試，不代表 App instrumentation 通過。

需要自行下 adb 指令時，先載入此程序限定的環境：

```powershell
. 'D:\codex-tools\linzin-android\emulator-qa\Set-LinzinEmulatorEnvironment.ps1'
$adb = 'D:\codex-tools\linzin-android\sdk\platform-tools\adb.exe'
$qaTarget = @('-H', '127.0.0.1', '-P', '5038', '-s', 'emulator-5580')
& $adb @qaTarget emu avd name
& $adb @qaTarget get-state
& $adb @qaTarget shell getprop sys.boot_completed
& $adb @qaTarget shell getprop ro.build.version.sdk
& $adb @qaTarget shell wm user-rotation
```

專用 server 的 USB selector 為 `--one-device emulator-5580`，mDNS 自動連線關閉。沒有停止或重設原本的 5037 server。AVD、Android 使用者資料、emulator 偏好、暫存、AppData 及 cache 全在 `D:\codex-tools\linzin-android\emulator-qa`；JDK、SDK 與 Gradle cache 沿用既有 D 槽工具鏈。`USERPROFILE` 等調整只在載入腳本的程序及子程序生效，未更改 Windows 使用者／系統環境，也未覆寫 PowerShell `$HOME` 或 `CODEX_HOME`。

## 橫向設定

本 AVD 的 `config.ini` 已設定 `hw.initialOrientation=landscape`。執行中的 AVD 亦已套用：

```powershell
& $adb @qaTarget shell settings put system accelerometer_rotation 0
& $adb @qaTarget shell settings put system user_rotation 1
& $adb @qaTarget shell wm user-rotation lock 1
& $adb @qaTarget shell wm fixed-to-user-rotation enabled
```

Google Launcher 在只有 `user_rotation=1` 時仍要求直向，因此另外固定顯示使用者旋轉。實際 Window Manager bounds 與 PNG 截圖均驗證為 1280×720；首次顯示的系統提示無法以 Back 關閉，之後已點擊提示外側將其關閉，並以新的截圖確認。未啟動任何待測 App。

`wm size` 仍可能列出面板自然尺寸 720×1280，應以 `dumpsys display` 的 `mOverrideDisplayInfo`、Window Manager bounds 或截圖確認實際橫向渲染。此設定會優先採用固定橫向；若主工作之後要測試 App 自行切換方向，先自行決定是否在**這個 AVD**執行 `wm fixed-to-user-rotation default`。

## 重啟與停止

目前實例已保持執行。不要再次啟動第二個實例。僅在已停止專用實例和 server 時使用：

```powershell
& 'D:\codex-tools\linzin-android\emulator-qa\Start-LinzinEmulator.ps1'
```

腳本會先確認 5038、5580、5581、8555 未被佔用，以及 `-accel-check` 成功；任一不符就停止，不取代現有程序。Windows 背景程序均使用 `Start-Process -WindowStyle Hidden`。Emulator 的完整參數為：

```text
-avd Linzin_API35_GoogleApis_QA -port 5580 -no-window -no-audio -no-boot-anim -no-snapshot -no-metrics -accel on -gpu software -memory 2048 -cores 2 -camera-back none -camera-front none -netdelay none -netspeed full -grpc 8555 -grpc-use-jwt
```

啟動腳本只回報程序建立，不聲稱 Android 已開機。之後執行狀態檢查，需得到 `Ready: true` 才能交給主工作。若 300 秒仍未就緒，檢查本次 `RunRoot` 的日誌並停止此實例，不要持續重試或啟動第二個實例。

當主工作確定不再使用後，僅停止本專用實例與 5038 server：

```powershell
& $adb @qaTarget emu kill
& $adb @qaTarget kill-server
```

`kill-server` 是 server 層級操作，`-s` 本身不能限制其範圍；上述 **`-P 5038` 不可省略**。不要使用預設 5037，也不要執行全域 `adb kill-server`、`adb reconnect`、`-wipe-data` 或其他 AVD 的清理命令。

## 主工作確認最終 build 後才可執行

本節只提供主工作之後使用的命令，本工作尚未執行。必須先取得主工作對 emulator 安裝／測試的確認；最終 APK 路徑及測試 runner 應由最終 build 確認。

```powershell
# 僅供主工作在確認 ready 後執行；不會自動執行。
. 'D:\codex-tools\linzin-android\emulator-qa\Set-LinzinEmulatorEnvironment.ps1'
$adb = 'D:\codex-tools\linzin-android\sdk\platform-tools\adb.exe'
$qaTarget = @('-H', '127.0.0.1', '-P', '5038', '-s', 'emulator-5580')
$appApk = 'D:\LinzinDeskClock v2.0\app\build\outputs\apk\debug\app-debug.apk'
$testApk = 'D:\LinzinDeskClock v2.0\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
& $adb @qaTarget emu avd name
# 上一行必須回傳 Linzin_API35_GoogleApis_QA，且狀態檢查必須 Ready=true。
& $adb @qaTarget install -r $appApk
if ($LASTEXITCODE -ne 0) { throw 'Emulator app APK installation failed.' }
& $adb @qaTarget install -r $testApk
if ($LASTEXITCODE -ne 0) { throw 'Emulator test APK installation failed.' }
& $adb @qaTarget shell pm list instrumentation
# 依最終 APK 確認 runner 後，只有主工作獲授權才可執行下列測試。
& $adb @qaTarget shell am instrument -w -r 'com.linzin.linzindeskclock.test/androidx.test.runner.AndroidJUnitRunner'
```

不要將這組命令改成無指定 serial 的安裝／測試命令。`am instrument` 的結果需檢查測試數、失敗內容與 `INSTRUMENTATION_CODE`，不能只依賴 adb 程序退出碼認定全數通過。直接 adb 執行不會自動產生 Gradle connected-test 的 HTML 報告。

## 安裝與能力檢查紀錄

本次先安裝 emulator 本體並執行 `-accel-check`，確認 `WHPX(10.0.26300) is installed and usable` 後才下載完整 API 35 映像。雖然 CIM 的 `HypervisorPlatform` 選用功能快照顯示未啟用，實際 emulator 檢測及開機日誌均確認 WHPX 可用；未變更任何 Windows 功能、安裝 Hypervisor driver 或重新啟動系統。因此本次無需 CPU 軟體模擬退路。主機只有約 8 GB RAM，檢查時約 0.8–1.1 GB 可用，請維持單一 emulator。

CLI 23 安裝套件使用新版 `android.exe`，PowerShell 中含分號的 package identifier 必須單引號包住：

```powershell
. 'D:\LinzinDeskClock v2.0\scripts\Set-AndroidEnvironment.ps1'
$cli = 'D:\codex-tools\linzin-android\sdk\cmdline-tools\23.0\bin\android.exe'
& $cli --no-metrics "--sdk=$env:ANDROID_HOME" sdk --ignore-outdated-xmls install 'emulator'
# 只有 -accel-check 通過或另有明確 bounded software 計畫後才安裝完整映像。
& 'D:\codex-tools\linzin-android\sdk\emulator\emulator.exe' -accel-check
& $cli --no-metrics "--sdk=$env:ANDROID_HOME" sdk --ignore-outdated-xmls install 'system-images;android-35;google_apis;x86_64'
```

建立 AVD 時採用同一套 CLI 的 Java entry point，保留 wrapper 所需的 `-Dcom.android.sdkmanager.toolsdir`，避免 Windows shim 對分號參數的解析問題。新版與舊版 home 變數同時指向不同路徑會使 avdmanager 回報無法定位 SDK，因此專用環境只使用 `ANDROID_USER_HOME`、`ANDROID_EMULATOR_HOME`、`ANDROID_AVD_HOME`，並在程序範圍清除舊的 `ANDROID_SDK_HOME`。

所有證據都在 `D:\codex-tools\linzin-android\emulator-qa`：

- `capabilities.json`、`accel-check.log`：下載完整映像前的主機與加速檢查。
- `install-emulator.log`、`install-api35.log`：已完成的套件安裝。
- `current-run.json`：本次程序 handle、精確參數與日誌位置。
- `readiness.json`：最近一次唯讀裝置狀態檢查。
- `runs\20261108-004508\emulator.stdout.log`：實際 WHPX 啟動與開機時間。
- `runs\20261108-004508\landscape-ready.png`：橫向像素驗證截圖。

本工作僅新增此文件與工具鏈下的 emulator QA 檔案，未修改 App 原始碼、Gradle 或 `.github`。

