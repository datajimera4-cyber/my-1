# WatchEarn 🎬💰

WatchEarn is a native Android application built with Kotlin, Jetpack Compose (Material 3), MVVM, Coroutines, and DataStore. It rewards users for watching verified YouTube videos in the official YouTube app by monitoring media notifications and sessions.

---

## 📱 Navigation & Features

### 1. Bottom Navigation Bar (4 Professional Tabs)
- **Home**: The complete original dashboard featuring your active/featured video task, quick balance badge, status monitor, and direct task launcher.
- **Tasks**: Browse all available YouTube video tasks with thumbnail previews, duration badges (e.g. `⏱️ 10:00` or `🔴 LIVE`), and reward tags (`Up to +160 Coins`).
  - **`+` Icon Action**: Tap the top bar `+` or the Floating Action Button (FAB) to add any custom YouTube video or live stream to the task list.
- **Wallet**: View coin balance, INR conversion (`10 Coins = ₹1.00 INR`), and cash payout/withdrawal via UPI, Bank Transfer, or Paytm!
- **Me**: Profile dashboard, VIP rank, completed task statistics, system notification access status, and diagnostics.

---

## 💸 Cash Withdrawal & Payouts (10 Coins = ₹1.00 INR)
Users can withdraw their earnings directly to cash:
- **Conversion Rate**: **10 Coins = ₹1.00 INR** (e.g. 50 Coins = ₹5, 100 Coins = ₹10, 500 Coins = ₹50).
- **Payment Methods Supported**:
  - **UPI** (Google Pay, PhonePe, Paytm, BHIM UPI ID)
  - **Bank Transfer** (Account Number & IFSC)
  - **Paytm Wallet** (10-digit mobile number)
- Instant validation, balance deduction, and detailed payout receipt history!

---

## ⏱️ Watch Goals, Milestones & Continuous Watch Rules
When starting any video task, WatchEarn presents a **Watch Goal Selection Dialog**:
- **3 Minutes** (180s) -> 🪙 **+10 Coins**
- **5 Minutes** (300s) -> 🪙 **+15 Coins**
- **10 Minutes** (600s) -> 🪙 **+40 Coins**
- **20 Minutes** (1200s) -> 🪙 **+100 Coins**
- **30 Minutes** (1800s) -> 🪙 **+160 Coins**

### 🧠 Continuous Watch & Milestone Rules:
- **Continuous watching required**: If a user watches less than 3 minutes (e.g., 1 minute) and leaves, **0 coins** are awarded.
- **Progressive Milestone Unlock**: If a user selects 10 minutes, but watches continuously for 3 minutes or 5 minutes before stopping:
  - At 3 minutes: Unlocks **+10 coins**.
  - At 5 minutes: Unlocks **+15 coins**.
  - At 10 minutes: Unlocks the full **+40 coins**.
- **Live notification status**: Ongoing foreground notification clearly indicates continuous watch progress and unlocked milestones live (e.g. `⏱️ 03:15 / 10:00 • 3m Done (+10c)`).

---

## 🔗 Where to Change or Paste Any Video Link
1. **Directly in the App**: Tap the **Link (🔗)** icon on the Top Bar or tap **"Paste Any Video Link"** on the Home or Task screen. Paste any YouTube video URL (`youtube.com/watch?v=...`, `youtu.be/...`, or `shorts/...`), and the app will instantly fetch and record its Title, Channel Name, and Thumbnail.
2. **In Code**: Open **`app/src/main/java/com/example/data/SampleTask.kt`** and edit line 14:
```kotlin
const val videoUrl = "https://www.youtube.com/watch?v=YOUR_VIDEO_ID"
```
*(Optionally change `requiredSeconds` to 20 for fast testing, default is 180 seconds = 3 minutes).*

---

## 🔍 Search Modes & Toggle Switch ("Live Realtime Mode")
On the Task screen right above **Start Task**, there is a toggle switch:
- **Toggle OFF (In-App Simulation Mode - Default)**:
  - WatchEarn shows the animated typewriter typing & radar search screen.
  - Simulates natural human keystrokes, browsing candidate thumbnails, and channel verification.
  - Once verified, it opens YouTube to play the video.
- **Toggle ON (Live Realtime Mode)**:
  - **No loading screen** appears inside WatchEarn.
  - WatchEarn directly opens the official YouTube app on screen into live search results for that exact title!
  - Users see YouTube opening, searching the title, and selecting the verified target video in real-time.
  - Seamlessly starts foreground tracking and counts verified watch seconds.

---

## 🔔 How to Grant Notification Access
1. When you tap **Start Task** (or open the **Setup** screen via Diagnostics or the permission prompt), tap **"Enable Notification Access"**.
2. Android will open the **Device & App Notifications** / **Notification Listener** settings screen.
3. Locate **WatchEarn** in the list, toggle it **ON**, and confirm the system dialog.
4. Return to WatchEarn — the green tick will appear automatically!

---

## 📲 How to Install on a Real Phone
1. In AI Studio, open the top-right settings/download menu and export as an **APK** (or download the project as a ZIP and build via Android Studio: `Build > Build Bundle(s) / APK(s) > Build APK(s)`).
2. Transfer the `.apk` file to your Android phone (via USB, Drive, or direct download).
3. On your phone, tap the APK to install. Allow "Install unknown apps" if prompted.
4. Launch **WatchEarn**, tap **Open Task**, and follow the setup guide.

---

## ✅ Test Checklist
- **(a) Correct video plays fully -> coins added**: Start task, play the matching video in YouTube until required time elapses. The success dialog appears and +10 coins are credited to the local wallet.
- **(b) Pause -> timer stops; resume -> continues**: Pausing the video in YouTube halts the timer immediately; resuming increments the timer accurately without counting paused duration.
- **(c) Open a different video -> after 10 s red alert + task cancelled**: Playing a video with a mismatched title starts a 10-second grace countdown. If still mismatched, the session is marked INVALID, a high-importance Red Alert notification is triggered, and a red warning banner appears.
- **(d) App in background / screen off -> timer keeps running**: A foreground service with an ongoing notification (`WatchEarn: Watched mm:ss / 03:00`) keeps the timer active in the background.
- **(e) Same task again -> blocked**: Once completed, the task displays "Completed" and "Task Already Completed" until Reset is pressed.
- **(f) Reset -> everything cleared**: Tapping Reset on the Home screen clears the wallet balance to 0, resets task completion, clears watch progress, and purges transaction history.
- **(g) Notification Access off -> Setup screen guides the user**: If Notification Access is revoked or not yet granted, tapping Start Task redirects to the Setup screen with direct deep links to settings.
