# BoostDroid

BoostDroid is an open-source system optimization and performance utility designed for Android devices. The project aims to provide memory management, application tracking, and cache optimization while adapting to modern Android privacy and security architectures.

> **Status Notice:** This project is currently in an active development/alpha state. Several system-level components are being refactored to comply with modern Android API constraints (Android 10+ and API 26+).

---

## Features & Core Modules

* **Memory Optimization:** Designed to evaluate system load and optimize available RAM allocations.
* **Process Tracking:** Utilizes system telemetry to identify resource-heavy applications.
* **System Info:** Displays hardware configuration and system metadata dynamically.
* **Material 3 Interface:** Provides a clean UI supporting both native Dark and Light modes.

---

## Technical Architecture & Constraints

Modern Android security protocols strictly isolate application sandboxes. To bypass these limitations without requiring Root access, BoostDroid is migrating towards the following engineering solutions:

### 1. Automation via AccessibilityService
Traditional methods like `ActivityManager.killBackgroundProcesses()` are heavily restricted by the OS. BoostDroid implements an `AccessibilityService` loop that programmatically navigates to target applications' settings nodes to safely invoke "Force Stop" and "Clear Cache" actions via UI automation.

### 2. Telemetry via UsageStatsManager
Direct process enumeration via `getRunningAppProcesses()` is deprecated for security. Active user processes and resource usage are fetched using the `UsageStatsManager` API, requiring the explicit `PACKAGE_USAGE_STATS` runtime permission.

### 3. Architecture Stack
* **Language:** 100% Kotlin
* **Architecture Pattern:** MVVM (Model-View-ViewModel) *[In Refactoring]*
* **Asynchronous Execution:** Kotlin Coroutines (`Dispatchers.IO`) for non-blocking I/O operations.
* **UI Framework:** Modern XML / Jetpack Compose components following Material 3 guidelines.

---

## Known Operational Issues (Alpha State)

* **Device Info Crash:** Direct access to restricted hardware identifiers (e.g., serial numbers) throws a `SecurityException` on Android 10+. A patch using safe build metadata properties is under development.
* **Background Process Termination:** The "Kill All Apps" feature is restricted until the background accessibility layer configurations are finalized.
* **Light Theme Contrast:** High-contrast color assets are currently being adjusted to meet WCAG readability standards.

---

## Development & Installation

### Prerequisites
* Android Studio Ladybug (or newer)
* JDK 17 or higher
* Android SDK Platform 34 (Target SDK)

### Building from Source
1. Clone the repository:
   ```bash
   git clone [https://github.com/Kitsune-Rei/BoostDroid.git](https://github.com/Kitsune-Rei/BoostDroid.git)
