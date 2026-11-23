# SpartanPath 🛡️🗺️
### Campus Asset Mapping & Navigation System
**BatStateU ARASOF-Nasugbu • Semestral Project for Object-Oriented Programming (OOP)**

---

## 🚀 Overview
**SpartanPath** is a comprehensive, production-ready Android application designed for BatStateU ARASOF-Nasugbu. It seamlessly combines interactive Leaflet campus mapping, graph-based shortest-path indoor/outdoor navigation, role-based asset inventory tracking, and maintenance reporting workflows.

---

## ✨ Key Features
*   **🗺️ Interactive Leaflet Map & Navigation**:
    *   Satellite & street-level campus maps powered by OSMDroid and custom Leaflet tiles (`campus_map.html`).
    *   Graph-based campus navigation engine supporting building nodes, walkpaths, turn-by-turn instructions, and real-time GPS arrival alerts.
*   **👥 Role-Based Access Control (RBAC)**:
    *   Secure multi-role access supporting **Students**, **Maintenance Personnel**, and **Administrators**.
    *   Role-specific navigation tabs, inventory management, and user administration panels.
*   **📦 Asset & Maintenance Management**:
    *   QR/search-enabled campus asset inventory with category filtering, floor/room sorting, and condition tagging.
    *   Issue reporting and maintenance ticketing with photo attachment support powered by Cloudinary and uCrop.
*   **🔐 Enterprise-Grade Security**:
    *   Strict Android component export restrictions (`exported="false"`).
    *   WebView XSS defense using `JSONObject.quote(...)` and disabled cross-origin file URL access.
    *   Encrypted and backup-isolated local session preferences (`SpartanPathLoginPrefs.xml`).
    *   Debug-only gated quick sign-in demo accounts (`BuildConfig.DEBUG`).

---

## 📱 Tech Stack & Libraries
*   **Language**: Kotlin (100%)
*   **UI Framework**: Material Design 3 (M3), XML Layouts, & Jetpack Compose
*   **Architecture**: MVVM (Model-View-ViewModel) with Android Architecture Components & Coroutines
*   **Backend & Auth**: Firebase Authentication & Cloud Firestore
*   **Mapping & GIS**: OSMDroid (`osmdroid-android`) & Leaflet.js
*   **Media & Processing**: Glide, Cloudinary Android SDK, and uCrop

---

## 🛡️ Security & Hardening Highlights
1.  **Component Isolation**: All internal activities (`LoginActivity`, `AddEditAssetActivity`, `MapActivity`, `AccountActivity`, etc.) are explicitly unexported (`exported="false"`).
2.  **Backup Rules**: Sensitive local preference files are excluded from device full backups and cloud backups (`backup_rules.xml` & `data_extraction_rules.xml`).
3.  **FileProvider Scoping**: Temporary camera capture files are stored securely within a scoped `camera/` cache subdirectory.
4.  **Device Compatibility**: Minimum SDK set to `26` (Android 8.0+) with verified stability on Android 13+ devices (Realme C53 / API 33).

---

## 📥 Installation & Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/SalesReesk/2106_SpartanPath_Sales_Valdez.git
   ```
2. Open the project in **Android Studio**.
3. Ensure `google-services.json` is placed inside the `app/` directory.
4. Build and run the app on an emulator or physical device (`minSdk 26+`).

---

## 👤 Authors & Contributors
*   **Developers**: Lawrence Valdez & Sales Reesk
*   **Institution**: BatStateU ARASOF-Nasugbu
