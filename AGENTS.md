# Agent System Specification (AGENTS.md) - Midgley Auto

This document specifies the architecture, developer directives, and multi-agent coordination protocols for the **Midgley Auto (`midgley-auto`)** native Android Automotive OS and Android Auto companion application ecosystem.

---

## 🚗 Architecture Overview

`midgley-auto` delivers real-time unleaded gas price forecasts, optimal fill-up timing recommendations, and OBD-II in-transit fuel telemetry directly to driver vehicle head units and companion mobile devices.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           MIDGLEY CORE BACKEND & CDN                           │
│  • Tier 1: Production GitHub Pages Static CDN (https://koshiirra.github.io/...) │
│  • Tier 2: Dwarvenbard Cloud Gateway (https://local-dev.dwarvenbard.com/)       │
│  • Tier 3: Local Dev VM REST Gateway (http://10.42.42.54:8000/)                │
└────────────────────────────────────────┬────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    MIDGLEY RESILIENT REPOSITORY LAYER                           │
│                      (data/repository/MidgleyRepository.kt)                     │
│  • Dual-Mode Network Client (Static .json by @Url vs Dynamic @GET("combined")) │
│  • 6-Hour In-Memory & Deterministic Fallback Cache (ConcurrentHashMap)          │
│  • OBD-II Low-Fuel Safety Reserve Override (< 15% -> Emergency FILL_NOW)        │
└────────────────────┬───────────────────────────────────────┬────────────────────┘
                     │                                       │
                     ▼                                       ▼
┌─────────────────────────────────────────┐  ┌────────────────────────────────────┐
│      IN-DASH CAR APP UI                 │  │      COMPANION HANDSET APP         │
│      (androidx.car.app:car)             │  │      (Jetpack Compose Material 3)  │
│  • AdvisorPaneScreen (Signal & Timing)  │  │ • CompanionSettingsScreen          │
│  • PriceTrendsScreen (5-Day Trajectory) │  │ • 3-Tier API Gateway Selector      │
│  • ActiveAlertsScreen (NOAA/Outages)    │  │ • Tank Capacity & Metric Presets   │
│  • GasAdvisorSession (Lifecycle Engine) │  │ • BLE OBD-II Scanner & Connect     │
└─────────────────────────────────────────┘  └────────────────────────────────────┘
```

---

## 🐧 Dev VM Directives & Execution Standards

All agent sessions MUST adhere to the dedicated Linux development environment:

### Specifications
* **Host**: `dev-vm` (`10.42.42.54`), Ubuntu 26.04 LTS on hypervisor `LAB-HOST` (`10.42.42.26`).
* **SSH User**: `marty@10.42.42.54`
* **Project Directory**: `/home/marty/projects/midgley-auto`
* **Java SDK**: OpenJDK 17 (`openjdk-17-jdk`)

### Execution Rules
1. **Offload Builds:** Never execute heavy Gradle builds (`assembleDebug`, `test`) locally on the Windows host. Always run via SSH on `dev-vm`:
   ```bash
   ssh marty@10.42.42.54 "cd /home/marty/projects/midgley-auto && ./gradlew test assembleDebug"
   ```
2. **POSIX Pathing:** Use `/home/marty/projects/midgley-auto` on `dev-vm`.
3. **Line Endings:** Enforce LF (`\n`) on all Kotlin source files and Gradle build scripts.

---

## 🌐 3-Tier API Gateway Delivery Architecture

The application implements a 3-tier gateway selector configured via `MetroPreferenceManager` and `CompanionSettingsScreen`:

| Gateway Tier | Preset URL | Mode | Primary Use Case |
| :--- | :--- | :--- | :--- |
| **1. Production CDN (Default)** | `https://koshiirra.github.io/midgley/` | **Static JSON** | Zero-cost, 100% SLA global CDN delivery from GitHub Pages (`api/v1/combined_{locale}.json`). |
| **2. Dwarvenbard Cloud** | `https://local-dev.dwarvenbard.com/` | **Dynamic REST** | Remote dev gateway routing to live FastAPI staging instance. |
| **3. Dev VM Local LAN** | `http://10.42.42.54:8000/` | **Dynamic REST** | Direct local subnet debugging for real-time model revisions and shock testing. |

### Dual-Mode Routing Logic (`MidgleyRepository.kt`)
- If the configured `baseUrl` contains `github.io`, `github.com`, or `raw.githubusercontent.com`:
  - Automatically fetches `api/v1/combined_<locale>.json` using `@Url getCombinedByUrl()`.
- Otherwise:
  - Calls dynamic REST endpoint `@GET("combined")` with query parameters `locale` and `zip_code`.

---

## 🔌 OBD-II Fuel Telemetry & Safety Overrides

* **Module:** `data/obd/Obd2PidDecoder.kt` & `data/obd/Obd2BleManager.kt`
* **Standard PID Ingested:** Mode `01` PID `2F` (Fuel Tank Level Input %).
* **Formula:** Fuel Level % = (A * 100) / 255
* **Safety Override Rule:**
  - When fuel level < 15% (reserve threshold `LOW_FUEL_THRESHOLD_PERCENT = 15.0`), the system **immediately overrides** any price hold or trough timing signal with `🔴 LOW FUEL • FILL UP NOW`.
  - Net savings calculation dynamically factors in actual gallons required to fill the tank based on live shortfall:
    `Shortfall Gallons = (1.0 - Fuel Level % / 100.0) * Tank Capacity`

---

## 🧪 Testing & Verification Protocols

* **Unit Testing Suite:**
  ```bash
  ssh marty@10.42.42.54 "cd /home/marty/projects/midgley-auto && ./gradlew test"
  ```
  - `MidgleyRepositoryTest`: Tests MockWebServer deserialization for combined payloads and offline fallbacks.
  - `Obd2PidDecoderTest`: Validates ELM327 hexadecimal response parsing across standard and edge-case PID responses.
  - `MetroPreferenceManagerTest`: Validates gateway URL switching and metro persistence.

* **Build & Release Protocol:**
  1. Build APK on `dev-vm`: `./gradlew assembleDebug`
  2. Copy APK to sync worktree: `scp marty@10.42.42.54:/home/marty/projects/midgley-auto/app/build/outputs/apk/debug/app-debug.apk midgley-auto-vX.Y.Z-debug.apk`
  3. Publish GitHub Release: `gh release create vX.Y.Z midgley-auto-vX.Y.Z-debug.apk --title "vX.Y.Z Release" --notes "..."`
