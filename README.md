# Hubitat USGS Stream Gauge

Hubitat device driver that polls the [USGS Water Data OGC API](https://api.waterdata.usgs.gov/docs/ogcapi) (`latest-continuous` collection) for any USGS monitoring location and surfaces the latest readings as attributes for dashboards and rules. No API key required.

Default site: `USGS-05331833` — Namekagon River at Leonards, WI.

## Attributes

| Attribute | Description |
|---|---|
| `gageHeight` | Gage height, ft (pcode 00065; arbitrary datum — trend indicator, not depth) |
| `gageHeightDisplay` | Plain-text `"1.43 ft"` for tiles |
| `gageHeightColored` | HTML-colored `"1.43 ft"` per the configured red/yellow/green ranges |
| `rangeStatus` | `red` / `yellow` / `green` — use it in Rule Machine / webCoRE |
| `elevation` | Water surface elevation, ft NAVD88 (pcode 63160, where published) |
| `discharge` | Discharge, ft³/s (pcode 00060) |
| `temperature` | Water temperature, °F or °C (pcode 00010) |
| `lastObservation` | Hub-local time of the latest reading |

## Preferences

- **USGS monitoring location ID** — any site, `USGS-` prefix included (find yours at [waterdata.usgs.gov](https://waterdata.usgs.gov/))
- **Poll interval** — 15 min / 30 min / 1 h / 3 h (most gauges report hourly; 30 min default)
- **Red at or below (ft)** — low-water threshold (default 1.5)
- **Yellow above red, up to (ft)** — marginal band; green above this (default 1.7)
- **Report water temperature in °F** — off = °C
- **Debug logging**

## Install

1. **Drivers Code → New Driver**, paste `usgsgaugedriver.groovy`, Save.
2. **Devices → Add Device → Virtual**, set Type to **USGS Stream Gauge**, Save.
3. Set the site ID and thresholds in preferences, hit **Refresh**.

## Dashboard

Add a tile → your gauge device → template **Attribute** → attribute `gageHeightColored`. The value renders red (≤ red max), yellow (between), or green (above yellow max). Use `gageHeightDisplay` for an uncolored tile, and additional Attribute tiles for `discharge`, `elevation`, or `lastObservation`.

## Notes

- Gage height is stage against an arbitrary local datum — a trend indicator, not water depth.
- Multiple rivers: create one virtual device per site, all using this driver.
- The USGS API needs no key; a free key only raises rate limits and is unnecessary at these poll intervals.
