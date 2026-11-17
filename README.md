# KetomSync

**Privacy-focused custom Android keyboard (`InputMethodService`) and real-time cross-device clipboard synchronization app**, engineered with Kotlin, Jetpack Compose, Room, and Cloud Firestore for seamless continuity between phone (**Google Pixel 9 Pro**) and tablet (**Xiaomi Pad 7**).

---

## Features

### 1. Precision Keyboard & Gesture Engine
- **Smart Backspace**:
  - **Tap**: Deletes one Unicode grapheme cluster (including multi-codeunit emoji) and saves it to a **50-entry undo buffer**.
  - **Hold (300–600 ms)**: Deletes the preceding word and trailing whitespace with a live visual highlight and quick **Tap Undo** banner.
  - **Hold past 600 ms**: Accelerates character deletion from **10 chars/sec** up to **~35 chars/sec** over 1.5 seconds.
  - **Swipe Left from Backspace**: Progressively selects preceding words in the active editor; sliding right reduces or clears the selection; releasing deletes the selected words into the undo buffer.
- **Undo Key**: One-tap recovery for recent Backspace deletions directly in the top utility strip.
- **Spacebar Cursor Control**: Drag horizontally across the spacebar to glide the text cursor at **1 character per 12 dp** with tactile tick feedback.
- **Auto-Capitalization**: Automatically capitalizes at sentence starts, newlines, and after `.` / `!` / `?` followed by a space, plus double-tap Shift for Caps Lock.
- **Live Keyboard Sandbox**: Interactive in-app editor sharing the exact same keyboard controller to test gestures, clipboard chips, and macro expansion right away.

### 2. Cross-Device Clipboard Sync & Productivity
- **Hybrid Room SQLite + Cloud Firestore Sync**:
  - Local SQLite cache capped at **100 unpinned entries** with pinning support and configurable retention (**1 hour**, **24 hours**, **7 days**, or **Never**).
  - Real-time cross-device sync scoped to your authenticated Google account via **Jetpack Credential Manager**.
- **Auto-Categorization**: Automatically classifies clips into **Links**, **Code**, **Addresses**, and **Plain Text** with source device badges (Phone / Tablet).
- **Quick-Paste Clipboard Strip**: Recent synced clips appear directly above the keyboard layout for instant one-tap pasting.
- **Snippets & Prompted Macros**: Create reusable `{variable}` templates that prompt for placeholder values inline before pasting.

### 3. Adaptive Ergonomics, Appearance & Privacy
- **Phone & Tablet Adaptive UI**: Bottom `NavigationBar` on compact phones and a `NavigationRail` with a two-pane Master-Detail clipboard inspector on tablets (`>= 680 dp`).
- **One-Handed & Floating Modes**: Switch between **Full Width**, **One-Handed Left / Right (75% width)** with gutter flip controls, and **Floating Resizable** mode with drag repositioning and corner resizing.
- **Material You & Theme Sync**: System / Light / Dark synchronization with Android 12+ wallpaper-based **Dynamic Color**.
- **Tunable Haptics**: Adjustable vibration strength and **1–50 ms** pulse duration controls.
- **Incognito Mode**: Visual incognito indicator that pauses cloud sync, skips persistent history, and clears ephemeral session clips when exited.

---

## Tech Stack

- **Language & UI**: Kotlin, Jetpack Compose, Material Design 3
- **Keyboard Service**: Android `InputMethodService` hosting Compose UI (`LifecycleOwner`, `SavedStateRegistryOwner`, `ViewModelStoreOwner`)
- **Local Storage**: Room (SQLite) & Jetpack Preferences DataStore
- **Cloud Sync & Auth**: Firebase Authentication (Google Sign-In via Credential Manager) & Cloud Firestore with per-user zero-trust security rules
- **Testing**: JUnit, Robolectric, and `@firebase/rules-unit-testing` against local Firebase Emulators

---

## Getting Started

1. Clone the repository and open it in **Android Studio**.
2. Add your own `app/google-services.json` from your Firebase project (with **Firestore** and **Google Sign-In** enabled, plus your debug/release SHA-1 & SHA-256 fingerprints registered).
3. Update `app/src/main/res/values/firebase_applet_config.xml` with your Firestore database ID if using a named database, and deploy `firestore.rules`.
4. Build and install on your Android phone and tablet, then enable **KetomSync Keyboard** under **System → Languages & input → On-screen keyboard**.

### Running Tests

```bash
# Keyboard gesture, undo, auto-cap, and categorizer unit tests
gradle :app:testDebugUnitTest --tests '*KetomKeyboardControllerTest*'

# Firestore security rules (requires local Firebase emulators on ports 8085 & 9099)
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

---

## License

This project is intended for personal and educational use. Feel free to customize and extend it for your devices.
