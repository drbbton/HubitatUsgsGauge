/**
 *  USGS Stream Gauge
 *
 *  Polls the USGS Water Data OGC API (latest-continuous) for a monitoring
 *  location and exposes the latest readings as attributes for Hubitat
 *  dashboards. Default site: USGS-05331833, Namekagon River at Leonards, WI.
 *
 *  No API key required. Gauge reports hourly; default poll is every 30 min.
 */

import groovy.transform.Field

@Field static final Map RANGE_COLORS = [red: "#d32f2f", yellow: "#f9a825", green: "#2e7d32"]

metadata {
    definition(name: "USGS Stream Gauge", namespace: "drbbton", author: "drbbton",
               importUrl: "https://raw.githubusercontent.com/drbbton/HubitatUsgsGauge/main/usgsgaugedriver.groovy") {
        capability "Sensor"
        capability "Refresh"
        capability "TemperatureMeasurement"

        attribute "gageHeight", "number"        // ft, arbitrary datum (pcode 00065)
        attribute "gageHeightDisplay", "string" // "1.43 ft" — nicer on a tile
        attribute "gageHeightColored", "string" // HTML-colored "1.43 ft" for dashboard tiles
        attribute "rangeStatus", "string"       // red / yellow / green per configured ranges
        attribute "elevation", "number"         // ft NAVD88 (pcode 63160)
        attribute "discharge", "number"         // ft3/s (pcode 00060)
        attribute "lastObservation", "string"   // local time of the reading
    }

    preferences {
        input name: "siteId", type: "text", title: "USGS monitoring location ID",
              defaultValue: "USGS-05331833", required: true
        input name: "pollMinutes", type: "enum", title: "Poll interval",
              options: ["15": "15 minutes", "30": "30 minutes", "60": "1 hour", "180": "3 hours"],
              defaultValue: "30"
        input name: "redMax", type: "decimal",
              title: "Red at or below (ft) — low water",
              defaultValue: 1.5, required: true
        input name: "yellowMax", type: "decimal",
              title: "Yellow above red, up to (ft) — green above this",
              defaultValue: 1.7, required: true
        input name: "tempInF", type: "bool", title: "Report water temperature in °F",
              defaultValue: true
        input name: "logEnable", type: "bool", title: "Enable debug logging",
              defaultValue: false
    }
}

def installed() {
    updated()
}

def updated() {
    unschedule()
    switch (pollMinutes ?: "30") {
        case "15":  runEvery15Minutes("refresh"); break
        case "60":  runEvery1Hour("refresh");     break
        case "180": runEvery3Hours("refresh");    break
        default:    runEvery30Minutes("refresh")
    }
    refresh()
}

def refresh() {
    String site = siteId ?: "USGS-05331833"
    String uri = "https://api.waterdata.usgs.gov/ogcapi/v0/collections/latest-continuous/items" +
                 "?monitoring_location_id=${site}&f=json"
    if (logEnable) log.debug "GET ${uri}"
    try {
        httpGet([uri: uri, contentType: "application/json", timeout: 30]) { resp ->
            if (resp.status != 200 || !resp.data?.features) {
                log.warn "USGS returned status ${resp.status} with no features for ${site}"
                return
            }
            resp.data.features.each { f -> handleObservation(f.properties) }
        }
    } catch (e) {
        log.error "USGS poll failed: ${e.message}"
    }
}

private void handleObservation(Map p) {
    if (p.value == null) return
    BigDecimal val
    try {
        val = new BigDecimal(p.value.toString())
    } catch (ignored) {
        return
    }
    switch (p.parameter_code) {
        case "00065":
            sendEvent(name: "gageHeight", value: val, unit: "ft")
            sendEvent(name: "gageHeightDisplay", value: "${val} ft")
            String status = rangeStatusFor(val)
            sendEvent(name: "rangeStatus", value: status)
            sendEvent(name: "gageHeightColored",
                      value: "<span style='color:${RANGE_COLORS[status]};font-weight:bold'>${val} ft</span>")
            sendEvent(name: "lastObservation", value: formatObsTime(p.time?.toString()))
            break
        case "63160":
            sendEvent(name: "elevation", value: val, unit: "ft")
            break
        case "00060":
            sendEvent(name: "discharge", value: val, unit: "cfs")
            break
        case "00010":
            if (tempInF != false) {
                BigDecimal degF = (val * 9 / 5 + 32).setScale(1, BigDecimal.ROUND_HALF_UP)
                sendEvent(name: "temperature", value: degF, unit: "°F")
            } else {
                sendEvent(name: "temperature", value: val, unit: "°C")
            }
            break
    }
    if (logEnable) log.debug "pcode ${p.parameter_code} = ${val} ${p.unit_of_measure} at ${p.time}"
}

private String rangeStatusFor(BigDecimal val) {
    BigDecimal r = new BigDecimal((redMax ?: 1.5).toString())
    BigDecimal y = new BigDecimal((yellowMax ?: 1.7).toString())
    if (val <= r) return "red"
    if (val <= y) return "yellow"
    return "green"
}

private String formatObsTime(String iso) {
    if (!iso) return ""
    try {
        Date d = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").parse(iso)
        return d.format("EEE h:mm a", location.timeZone)
    } catch (ignored) {
        return iso
    }
}
