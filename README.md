# SpartanPath: Campus Navigation & Asset Management System (ArasPoint Map)

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-blue.svg)](https://kotlinlang.org)
[![Android SDK](https://img.shields.io/badge/Android%20SDK-34%20(minSdk%2033)-green.svg)](https://developer.android.com)
[![Firebase](https://img.shields.io/badge/Firebase-Firestore%20%26%20Auth-orange.svg)](https://firebase.google.com)
[![Leaflet JS](https://img.shields.io/badge/Leaflet-1.9.4-brightgreen.svg)](https://leafletjs.com)
[![License](https://img.shields.io/badge/License-Academic-lightgrey.svg)](#)

---

## Overview
**SpartanPath** (ArasPoint Map) is an Android application developed for **Batangas State University ARASOF-Nasugbu**. The system bridges the gap between campus spatial navigation, facility asset inventory tracking, and maintenance issue reporting. 

Students, faculty, and visitors frequently struggle with locating specific campus buildings and rooms. Concurrently, administrative staff face challenges maintaining accurate inventories of facility equipment and tracking maintenance work orders. SpartanPath solves these problems by providing an offline-capable interactive campus map with graph-based A* routing, real-time Firebase-backed asset management, and strict Role-Based Access Control (RBAC) workflows.

---

## Key Features & Modules

### 1. Interactive Campus Map & A* Graph Navigation
* **Leaflet WebView Integration:** Renders offline KML map data via Leaflet JS with custom building boundary polygons, permanent labels, and live GPS user location markers.
* **Graph-Based A* Pathfinding (`CampusNavigationEngine`):** Parses raw campus KML spatial XML files (`KmlParser`) into `GeoNode` vertices and `GeoEdge` paths, computing the shortest walking route between any two campus locations.
* **Turn-by-Turn Guidance & Arrival Alerts:** Generates step-by-step walking directions and triggers an arrival banner ("Arrived at Building!") when within 2 meters of the destination.

### 2. Real-Time Asset Inventory Tracking
* **Building Floor & Room Grouping:** Tapping any campus building on the map opens a bottom sheet (`BuildingAssetsBottomSheetDialogFragment`) displaying equipment categorized by floors (*Ground*, *Second*, *Third*) and rooms.
* **Quick-Sort & Filtering:** Inventory search bar equipped with a quick-sort modal button supporting sorting by **Tag (A-Z)**, **Name (A-Z)**, **Status**, and **Condition**, alongside building filter spinners and status chips.
* **Placeholder Media Fallback:** Asset details modal displays equipment photos with an automatic `ic_inventory_box` placeholder fallback.

### 3. Role-Based Maintenance Ticketing
* **Incident Issue Reporting:** Allows users to report damaged or faulty equipment. The incident location automatically populates with the asset's assigned location (`asset.location`) and locks as read-only.
* **Evidence Media Pipeline:** Mandatory evidence photo captured via device camera or gallery, cropped with `uCrop`, and securely uploaded to Cloudinary CDN.
* **Strict RBAC Ticket Management:** 
  - **Administrators (`UserRole.ADMIN`):** Full privileges to assign, reassign staff, and update ticket progress.
  - **Maintenance Staff (`UserRole.MAINTENANCE`):** Review assigned work orders and advance ticket statuses (*Pending*, *In Progress*, *Resolved*, *Rejected*) via an explicit selection dialog without unauthorized ticket reassignment permissions.

### 4. Role-Based Access Control (RBAC) & Visitor Guest Mode
* **Tailored User Workflows:** Secure authentication supporting 5 distinct user actor roles: *Administrator*, *Maintenance Staff*, *Teacher (Faculty)*, *Student*, and *Visitor Guest*.
* **Visitor Guest Exploration:** Unauthenticated external visitors can explore the public campus map and view wayfinding routes, with restricted write operations safely prompting a "Sign In Required" modal.

---

## Tech Stack & Architecture

* **Language:** Kotlin `1.9.24` / Java 17
* **Architecture:** MVVM (Model-View-ViewModel) with Repository Pattern
* **Concurrency:** Kotlin Coroutines & `StateFlow` reactive streams (`BaseRepository.safeCall()`)
* **Backend & Cloud:** Firebase Authentication & Cloud Firestore NoSQL Database
* **Media Storage:** Cloudinary Android SDK `2.5.0` & Yalantis `uCrop` `2.2.11`
* **UI Framework:** AndroidX, ViewBinding, Material Design 3, ConstraintLayout
* **Runtime Target:** Android 13.0 Tiramisu (`minSdk 33` / `targetSdk 37` / SDK 34)

---

## Project Structure & Package Hierarchy

```
com.example.araspoint_map
 ┣ backend.model          # Domain data models (Asset, Building, MaintenanceRecord, User, Mappable, Constants)
 ┣ backend.model.enums    # Type-safe enums (AssetStatus, AssetCondition, ReportStatus, PriorityStatus, UserRole)
 ┣ backend.navigation     # Spatial graph parsing (KmlParser, CampusNavigationEngine, GeoNode, BuildingPolygon)
 ┣ backend.repository     # Data access layer (BaseRepository, AssetRepository, AuthRepository, MaintenanceRepository, UserRepository)
 ┣ frontend.viewModel     # Presentation ViewModels (AssetViewModel, AuthViewModel, MaintenanceViewModel, UserViewModel)
 ┗ frontend.activity      # UI View Layer (Activities, Fragments, Bottom Sheets, Dialogs)
```

---

## Getting Started & Installation

### Prerequisites
* **Android Studio Ladybug** (2024.1+) or newer.
* **JDK 17** installed and configured.
* Android SDK 34 / minSdk 33 emulator or physical Android device with GPS enabled.

### Cloning & Building
1. Clone the repository:
   ```bash
   git clone https://github.com/SalesReesk/2106_SpartanPath_Sales_Valdez.git
   ```
2. Open the project folder in Android Studio.
3. Allow Gradle to sync dependencies.
4. Run unit tests via terminal or Android Studio:
   ```bash
   ./gradlew testDebugUnitTest
   ```
5. Build and deploy the debug APK to your connected device/emulator:
   ```bash
   ./gradlew assembleDebug
   ```

---

## Testing & Verification
The project includes a comprehensive unit test suite (`SystemIntegrationAndOopTest.kt`, `CampusNavigationEngineTest.kt`, `AuthViewModelTest.kt`, etc.) verifying:
* Polymorphism & interface contracts (`Mappable.getFormattedMapTag()`)
* Encapsulation & strongly-typed enum bounds
* A* graph pathfinding shortest route calculations
* Coroutine error handling in `BaseRepository.safeCall()`
* Role-Based Access Control (RBAC) permission enforcement

---

## License & Coursework Disclaimer
Developed as an academic demonstration for Object-Oriented Programming (OOP) coursework at **BatStateU ARASOF-Nasugbu**. All campus mapping data and university assets represent institutional resources of Batangas State University.
