# 2.0 程式結構

路徑均相對專案根目錄。

| 範圍 | 用途 |
| --- | --- |
| app/src/main/java/com/linzin/linzindeskclock/autostart | 鎖定週期、資格判定、退出抑制及背景啟動 |
| app/src/main/java/com/linzin/linzindeskclock/service | 明確啟用的監看與 DreamService |
| app/src/main/java/com/linzin/linzindeskclock/sensors | 重力方向、傾角、資料時效與充電狀態 |
| app/src/main/java/com/linzin/linzindeskclock/layout | 分隔比例、吸附與時鐘側別偏好 |
| app/src/main/java/com/linzin/linzindeskclock/media | 系統媒體工作階段、封面與進度幾何 |
| app/src/main/java/com/linzin/linzindeskclock/calendar | 行程資料、格式與外部行事曆入口 |
| app/src/main/java/com/linzin/linzindeskclock/power | 低電量保護、選用鎖定權限及關屏清理 |
| app/src/main/java/com/linzin/linzindeskclock/settings | 設定、權限說明與狀態呈現 |
| app/src/main/java/com/linzin/linzindeskclock/ui | 時鐘、雙面板、媒體與行程畫面 |
| app/src/test | JVM 行為與邊界測試 |
| app/src/androidTest | Compose、framework、像素及系統整合測試 |
| scripts/verify-background-clock.py | 專用模擬器的背景啟動回歸檢查 |

詳細差異以 PR 的 Files changed 為準；執行紀錄與私人裝置資料不納入版本控制。
