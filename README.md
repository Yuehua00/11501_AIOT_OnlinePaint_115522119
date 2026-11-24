# Android Online Paint 

### 專案介紹｜Project Overview

本專案使用 **Android Studio 與 Java** 開發多人即時繪畫系統，結合 Android Canvas 繪圖技術與 TCP Socket 網路通訊，讓多位使用者能夠在不同裝置上共享畫布、同步繪畫。

系統採用 Client–Server 架構，由 Server 負責管理使用者、分配畫筆顏色與同步筆跡資料。

### 主要功能｜Features

**Server**
- 自訂 Server 名稱並啟動畫室
- 支援兩位以上 Client 同時連線
- 為不同使用者分配專屬畫筆顏色
- 即時顯示所有使用者的繪畫內容
- 顯示使用者名稱、IP 與畫筆顏色
- 清除所有筆跡或指定使用者的筆跡
- 管理使用者並執行踢出操作
- 使用者離開時移除其筆跡
- 關閉 Server 時通知 Client 安全斷線

**Client**
- 輸入使用者名稱、Server IP 與 Port
- 在畫布上自由繪畫
- 即時同步其他使用者的筆跡
- UNDO：撤銷自己的上一筆
- ERASE：刪除指定的整筆線條
- CLEAR：清除自己的所有筆跡
- LEAVE：離開共享畫室
- 顯示連線及使用者進出通知

### 使用技術｜Technologies

- **Language：** Java
- **IDE：** Android Studio
- **UI：** XML Layout / Custom View
- **Drawing：** Canvas / Paint / Path
- **Touch Interaction：** MotionEvent
- **Network：** TCP Socket
- **Multithreading：** UI Thread / Worker Thread

### 專案架構｜Project Structure

**Client（app）**
- `MainActivity.java`：登入與連線設定
- `MainActivity2.java`：繪畫操作與 Socket 通訊
- `DrawingView.java`：自訂畫布、觸控繪畫與筆跡管理

**Server（server）**
- `MainActivity.java`：Server 啟動頁面
- `MainActivity2.java`：Socket 連線、使用者管理、筆跡廣播與共享畫布

### 系統運作｜How It Works

1. Server 啟動並等待 Client 加入。
2. Client 輸入名稱、IP 與 Port 建立連線。
3. Server 為新使用者分配 ID 與畫筆顏色。
4. 使用者在畫布上繪畫時，系統記錄觸控座標。
5. Client 透過 Socket 將筆跡資料傳送到 Server。
6. Server 將筆跡廣播給其他使用者。
7. 各裝置重新繪製畫布，呈現多人同步繪畫效果。

為了適應不同手機尺寸，筆跡座標以 **0～1 的比例值**傳輸，再依各裝置畫布尺寸還原繪製位置。

### 操作方式｜How to Run

1. 使用 Android Studio 開啟專案。
2. 選擇 `server` Module，啟動 Server App。
3. 輸入名稱並按 START。
4. 選擇 `app` Module，啟動 Client App。
5. 輸入使用者名稱、IP 與 Port。
6. 按 CONNECT 進入共享畫布。
7. 使用畫筆、撤銷、橡皮擦和清除工具操作畫布。

**連線設定**

- Server Port：`6200`
- Client IP：`127.0.0.1`（搭配 ADB Reverse）
- Client Port：`6200`

多台 Android 模擬器測試時，需設定對應的 ADB Forward 與 Reverse。
