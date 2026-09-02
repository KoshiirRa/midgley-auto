# Midgley Auto (`midgley-auto`)

Android Auto and Android Automotive OS in-dash fuel price assistant for [Midgley](https://github.com/KoshiirRa/midgley).

## Overview
`midgley-auto` brings real-time regional unleaded gas prices, optimal fill-up timing guidance (`🔴 FILL UP NOW` vs `🟢 WAIT TO FILL`), and 5-day ML trend forecasts directly to vehicle dashboard displays using the `androidx.car.app` Car App Library and Jetpack Compose.

## Key Features
- **In-Dash Car UI (`androidx.car.app`):** `PlaceListMapTemplate` and `PaneTemplate` distraction-free head unit rendering.
- **Geo-Aware MSA Auto-Switching:** Vehicle GPS location tracking dynamically maps to refining hubs (Tulsa, Newark, Cincinnati, Greenville, Charlotte, Oakland).
- **Google Assistant & App Actions:** Hands-free voice query triggers (*"What's the gas price forecast in Tulsa?"*).
- **Companion Handset App:** Jetpack Compose (Material 3) phone settings screen for custom alert thresholds and tank capacity presets.

## Cross-References
- Core Forecast & Multi-Agent Backend: [KoshiirRa/midgley](https://github.com/KoshiirRa/midgley)
- Main Architectural Issue: [midgley#21](https://github.com/KoshiirRa/midgley/issues/21)
- REST API Documentation: [docs/API_CONTRACT.md](docs/API_CONTRACT.md)

## Tech Stack
- **Language:** Kotlin 2.0+
- **Car Head Unit:** AndroidX Car App Library (`androidx.car.app:car:1.7.0-beta01`)
- **Companion Handset UI:** Jetpack Compose & Material 3
- **Async & State:** Kotlin Coroutines & `StateFlow`
- **Min SDK:** 26 (Android 8.0) | **Target SDK:** 34 | **Compile SDK:** 35
