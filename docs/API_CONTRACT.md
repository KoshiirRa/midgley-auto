# Midgley Mobile & In-Dash API Contract

This document specifies the REST & Static JSON API contract between the `midgley-auto` Android client and the `midgley` backend data providers.

---

## 🌐 3-Tier Gateway Configuration

The client supports 3 configurable gateway environments:

1. **Production CDN (Default):** `https://koshiirra.github.io/midgley/` (Static JSON Mode)
2. **Dwarvenbard Cloud Gateway:** `https://local-dev.dwarvenbard.com/` (Dynamic REST Mode)
3. **Dev VM Local LAN Gateway:** `http://10.42.42.54:8000/` (Dynamic REST Mode)

---

## 📡 Endpoints

### 1. Static Combined Feed (Production CDN)
- **HTTP Method:** `GET`
- **URL Pattern:** `https://koshiirra.github.io/midgley/api/v1/combined_{locale}.json`
- **Locales:** `national`, `tulsa`, `oakland`, `newark`, `cincinnati`, `greenville`, `charlotte`, `port_st_lucie`, `bayarea`
- **Response Schema (`200 OK`):**
  ```json
  {
    "status": "success",
    "timestamp": "2026-09-14T05:00:00Z",
    "locale": {
      "code": "tulsa",
      "region_id": "Tulsa_OK",
      "name": "Tulsa Metro Area, OK",
      "padd_region": "PADD 2 Midwest"
    },
    "live_lookup": {
      "current_price_per_gal": 3.89,
      "source": "GasBuddy Live / Benchmark"
    },
    "forecast": {
      "current_base_price": 3.89,
      "predicted_price_per_gal": 3.79,
      "expected_change_dollars": -0.10,
      "projected_direction": "DOWN",
      "day_3_price": 3.79,
      "quantile_p10": 3.72,
      "quantile_p90": 3.86
    },
    "key_drivers": [
      {
        "name": "NOAA Weather Risk",
        "impact": -0.02,
        "description": "Convective storm threat subsided"
      }
    ]
  }
  ```

---

### 2. Dynamic Combined Endpoint (Cloud / Dev Gateway)
- **HTTP Method:** `GET`
- **Path:** `/api/v1/combined`
- **Query Parameters:**
  - `locale` (string, optional, default `national`): e.g. `tulsa`
  - `zip_code` (string, optional): 5-digit US ZIP code

---

### 3. Resolve Location to Metropolitan Statistical Area (MSA)
- **HTTP Method:** `GET`
- **Path:** `/api/v1/locations/resolve`
- **Query Parameters:**
  - `lat` (float, required): Latitude coordinate (e.g. `36.1540`)
  - `lon` (float, required): Longitude coordinate (e.g. `-95.9928`)

---

### 4. 5-Day Out-of-Time Retail Price Forecast
- **HTTP Method:** `GET`
- **Path:** `/api/v1/forecasts/{location_id}`

---

### 5. Smart Fill-Up Timing & Savings Advisor
- **HTTP Method:** `GET`
- **Path:** `/api/v1/savings`
- **Query Parameters:**
  - `location_id` (string, required): e.g. `tulsa`
  - `tank_capacity` (float, optional, default `15.0`): Vehicle tank capacity in gallons
