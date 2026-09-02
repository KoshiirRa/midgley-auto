# Midgley Mobile REST API Contract

This document specifies the REST API contract between the `midgley-auto` Android client and the `midgley` backend API server (`src/api_server.py`).

## Base URL Configuration
- Production Gateway: `https://midgley.n2yti.net/api/v1`
- Local / Dev Tunnel: `http://10.42.42.54:8000/api/v1`

---

## Endpoints

### 1. Resolve Location to Metropolitan Statistical Area (MSA)
- **HTTP Method:** `GET`
- **Path:** `/api/v1/locations/resolve`
- **Query Parameters:**
  - `lat` (float, required): Latitude coordinate (e.g. `36.1540`)
  - `lon` (float, required): Longitude coordinate (e.g. `-95.9928`)
- **Response Schema (`200 OK`):**
  ```json
  {
    "location_id": "tulsa",
    "location_name": "Tulsa Metro Area",
    "state": "OK",
    "padd": "PADD 2 (Midwest)",
    "distance_km": 4.2
  }
  ```

---

### 2. 5-Day Out-of-Time Retail Price Forecast
- **HTTP Method:** `GET`
- **Path:** `/api/v1/forecasts/{location_id}`
- **Response Schema (`200 OK`):**
  ```json
  {
    "location_id": "tulsa",
    "as_of_timestamp": "2026-09-02T12:00:00Z",
    "base_price": 3.89,
    "forecast": [
      {"day": 0, "date": "2026-09-02", "p50": 3.89, "p10": 3.85, "p90": 3.93},
      {"day": 1, "date": "2026-09-03", "p50": 3.87, "p10": 3.81, "p90": 3.92},
      {"day": 2, "date": "2026-09-04", "p50": 3.82, "p10": 3.75, "p90": 3.89},
      {"day": 3, "date": "2026-09-05", "p50": 3.79, "p10": 3.72, "p90": 3.86},
      {"day": 4, "date": "2026-09-06", "p50": 3.81, "p10": 3.74, "p90": 3.88},
      {"day": 5, "date": "2026-09-07", "p50": 3.85, "p10": 3.77, "p90": 3.93}
    ],
    "directional_trend": "TROUGH_DAY_3"
  }
  ```

---

### 3. Smart Fill-Up Timing & Savings Advisor
- **HTTP Method:** `GET`
- **Path:** `/api/v1/savings`
- **Query Parameters:**
  - `location_id` (string, required): e.g. `tulsa`
  - `tank_capacity` (float, optional, default `15.0`): Vehicle tank capacity in gallons
- **Response Schema (`200 OK`):**
  ```json
  {
    "location_id": "tulsa",
    "recommendation_code": "WAIT_TO_FILL",
    "display_signal": "🟢 WAIT TO FILL UP",
    "optimal_day": 3,
    "optimal_date": "2026-09-05",
    "current_price_gal": 3.89,
    "target_price_gal": 3.79,
    "savings_per_gal": 0.10,
    "net_tank_savings_usd": 1.50,
    "confidence_level": "HIGH"
  }
  ```

---

### 4. Active Weather & Supply Outage Alerts
- **HTTP Method:** `GET`
- **Path:** `/api/v1/events/active`
- **Query Parameters:**
  - `location_id` (string, required): e.g. `tulsa`
- **Response Schema (`200 OK`):**
  ```json
  {
    "location_id": "tulsa",
    "active_alerts": [
      {
        "event_id": "NOAA-SPC-2026-0902",
        "category": "NOAA_TORNADO_RISK",
        "title": "SPC Moderate Convective Risk - West Tulsa Refinery Hub",
        "severity": "WARNING",
        "price_impact_score": 0.35,
        "issued_at": "2026-09-02T10:15:00Z"
      }
    ]
  }
  ```
