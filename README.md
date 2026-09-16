# penink VPN

> Android Kotlin VPN App — 透過 WebSocket(TLS) 隧道，安全連回你家裡的網路。

---

## 專案結構

```
penink-vpn/
├── android/          ← Android App 專案（Kotlin + Jetpack Compose + Room）
│   ├── app/
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       ├── res/                  ← 圖示、字串、主題
│   │       └── java/com/penink/vpn/
│   │           ├── MainActivity.kt
│   │           ├── PeninkApp.kt
│   │           ├── data/             ← Room DB：VpnNode、NodeDao、Repository
│   │           ├── ui/
│   │           │   ├── main/         ← 主畫面（連線 / 斷線）
│   │           │   ├── nodes/        ← 節點管理 + 新增表單
│   │           │   ├── navigation/   ← Compose Navigation
│   │           │   └── theme/        ← Material3 主題
│   │           └── vpn/
│   │               ├── VpnState.kt
│   │               ├── VpnTunnelService.kt      ← VpnService + TUN
│   │               └── tunnel/TunnelWebSocket.kt ← 自製 WS 客戶端（RFC 6455）
│   ├── build.gradle.kts
│   └── gradle/
│
├── server/           ← 家庭伺服器端（Node.js + tuntap2 + ws）
│   ├── server.js
│   ├── setup-nat.sh  ← iptables NAT 設定腳本
│   └── package.json
│
└── web/              ← Cloudflare Pages 下載網站
    ├── index.html
    ├── style.css
    ├── app.js
    └── _headers      ← 強制下載 APK 的 HTTP Header
```

---

## 一、打包 Android APK

### 前置需求
- [Android Studio](https://developer.android.com/studio)（Koala 或更新版，內含 JDK 17）
- Android 手機（開啟「開發人員選項」→「USB 偵錯」）

### 方法 A：Android Studio 圖形介面

1. 打開 Android Studio，選 `File → Open`，選擇 `android/` 資料夾。
2. 等待 Gradle Sync 完成（首次約 2-5 分鐘）。
3. 連接手機（USB），點綠色 ▶️ Run。
4. App 會自動安裝並執行。

**輸出 Release APK：**
1. `Build → Generate Signed App Bundle / APK`
2. 選 APK → 建立或載入 Keystore → Release
3. 輸出路徑：`app/build/outputs/apk/release/app-release.apk`

### 方法 B：命令列（Gradle）

```bash
cd android/

# 確認有 gradlew（若無，先執行一次）
# 方法 1：透過 Homebrew 安裝 gradle 後產生 wrapper
brew install gradle
gradle wrapper --gradle-version 8.9

# 方法 2：直接用 Android Studio 開啟時會自動產生

# Debug APK（不需簽名，可直接安裝）
./gradlew assembleDebug
# 輸出：app/build/outputs/apk/debug/app-debug.apk

# Release APK（需 keystore）
./gradlew assembleRelease
# 輸出：app/build/outputs/apk/release/app-release.apk
```

### 安裝到手機

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 二、部署家庭伺服器

### 需求
- Linux（Ubuntu / Debian），需要 **root** 權限（建立 TUN）
- Node.js ≥ 18 且 < 24（建議 LTS 20）
- 網路可被手機存取（同一內網，或有公網 IP / Cloudflare Tunnel）

> ⚠️ `tuntap2` 是原生 addon，在 **Linux** 上執行 `npm install` 時會自動編譯。
> macOS 本機若出現編譯錯誤屬正常（其 TUN 建立也需要 root），請直接在 Linux 伺服器上部署。
> 需先安裝編譯工具：`sudo apt install build-essential python3`

### 步驟

```bash
cd server/
npm install
```

**設定 NAT 轉發（需 root）：**

```bash
sudo bash setup-nat.sh
```

**啟動伺服器：**

```bash
sudo PASSWORD="你的密鑰" node server.js
```

> 密鑰須與 App 中新增節點時填的「密鑰 / 密碼 / UUID」一致。

### 搭配 Cloudflare Tunnel（推薦）

免費取得正式 TLS 憑證，無需自簽憑證：

```bash
# 安裝 cloudflared（若尚未安裝）
# macOS: brew install cloudflare/cloudflare/cloudflared
# Linux: curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -o /usr/local/bin/cloudflared && chmod +x /usr/local/bin/cloudflared

# 啟動 Tunnel，轉發到本地伺服器
cloudflared tunnel --url http://localhost:9543
```

會得到一個 `https://xxxx.trycloudflare.com` 網址。在 App 中新增節點時：
- **伺服器 IP/網址：** `xxxx.trycloudflare.com`
- **連接埠：** `443`
- **密鑰：** 你設定的 PASSWORD

---

## 三、部署 Cloudflare Pages 下載網站

1. 建立 [Cloudflare](https://dash.cloudflare.com/) 帳號。
2. 進入 `Workers & Pages → Create → Pages → Upload assets`。
3. 上傳 `web/` 資料夾內所有檔案。
4. 部署後得到類似 `https://penink-vpn.pages.dev` 的網址。

> 記得將 build 好的 APK 檔案（`app-debug.apk` 重新命名為 `penink-vpn.apk`）也放進 `web/` 目錄後重新上傳。

---

## 架構圖

```
┌────────────────────────────┐        WSS (TLS/443)        ┌──────────────────────┐
│       Android App          │  ◄──────────────────────────► │   家庭伺服器 (Linux)  │
│                            │                               │                      │
│  ┌──────────────────────┐  │                               │  ┌────────────────┐  │
│  │   TUN (10.27.0.2)   │──┼── IP 封包 ──► WebSocket ──►──┼──► TUN (10.27.0.1)│  │
│  └──────────────────────┘  │                               │  └───────┬────────┘  │
│                            │                               │          │ NAT       │
│  所有 App 流量 → TUN → WS  │                               │          ▼           │
│                            │                               │    Internet (eth0)   │
└────────────────────────────┘                               └──────────────────────┘
```

---

## 技術細節

| 項目 | 技術 |
|------|------|
| UI 框架 | Jetpack Compose + Material3 |
| 本地資料庫 | Room (SQLite) |
| VPN 核心 | Android VpnService + TUN |
| 隧道協定 | WebSocket (RFC 6455) + TLS 1.2/1.3 |
| WebSocket 實作 | 自製 Kotlin 客戶端（使用 `VpnService.protect()` 避免迴圈） |
| 伺服器端 TUN | Node.js + tuntap2 (N-API) |
| WebSocket 伺服器 | ws (npm) |
| 下載網站 | Cloudflare Pages (純靜態 HTML/CSS/JS) |

---

## 授權

本專案僅供教育與個人合法用途。請遵守所在地區的法律與學校網路使用規範。