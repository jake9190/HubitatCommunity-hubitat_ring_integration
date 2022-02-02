/**
 *  Ring Virtual Beams Bridge Driver
 *
 *  Copyright 2019 Ben Rimmasch
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */

metadata {
  definition(name: "Ring Virtual Beams Bridge", namespace: "ring-hubitat-codahq", author: "Ben Rimmasch",
    importUrl: "https://raw.githubusercontent.com/codahq/ring_hubitat_codahq/master/src/drivers/ring-virtual-beams-bridge.groovy") {
    capability "Refresh"
    capability "Sensor"

    attribute "lastCheckin", "string"
    attribute "networkConnection", "enum", ["cellular", "ethernet", "unknown", "wifi"]
    attribute "wifi", "string"

    command "createDevices"
  }

  preferences {
    input name: "descriptionTextEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    input name: "traceLogEnable", type: "bool", title: "Enable trace logging", defaultValue: false
  }
}

private logInfo(msg) {
  if (descriptionTextEnable) log.info msg
}

def logDebug(msg) {
  if (logEnable) log.debug msg
}

def logTrace(msg) {
  if (traceLogEnable) log.trace msg
}

def createDevices() {
  logDebug "Attempting to create devices."
  parent.createDevices(device.getDataValue("zid"))
}

def refresh() {
  logDebug "Attempting to refresh."
  parent.refresh(device.getDataValue("zid"))
}

void setValues(final Map deviceInfo) {
  logDebug "setValues(deviceInfo)"
  logTrace "deviceInfo: ${deviceInfo}"

  if (deviceInfo.networks != null) {
    final Map networks = deviceInfo.networks

    if (deviceInfo.containsKey('networkConnection')) {
      final networkConnection = deviceInfo.networkConnection

      checkChanged("networkConnection", networks.getOrDefault(networkConnection, [type: "unknown"]).type)
    }

    // Beams bridge appears to only support wifi
    for (final String networkKey in ['wlan0']) {
      final Map network = networks[networkKey]
      if (network != null) {
        String networkType = network.type

        // Sometimes the type isn't included. Just skip updating things for now
        if (!networkType) {
          logDebug "Could not get network.type for ${networkKey}: ${networks}"
          continue
        }

        String networkName = network.name ?: device.getDataValue(networkKey + "Name")
        String networkRssi = network.rssi ?: device.getDataValue(networkKey + "Rssi")

        checkChangedDataValue(networkKey + "Type", networkType)
        checkChangedDataValue(networkKey + "Name", networkName)
        checkChangedDataValue(networkKey + "Rssi", networkRssi)

        final String fullNetworkStr = networkName + " RSSI " + networkRssi
        logInfo "${networkKey} ${networkType} ${fullNetworkStr}"
        checkChanged(networkType, fullNetworkStr)
        state[networkKey] = fullNetworkStr
      }
    }
  }

  if (deviceInfo.version != null) {
    final version = deviceInfo.version

    if (deviceInfo.deviceType == "adapter.ringnet") {
      for(final String key in ['buildNumber', 'nordicFirmwareVersion', 'softwareVersion']) {
        checkChangedDataValue(key, version[key])
      }
    } else {
      checkChangedDataValue("version", version)
    }
  }

  if (deviceInfo.status == 'success') {
    if (deviceInfo.deviceType == "halo-stats.latency") {
      sendEvent(name: "lastCheckin", value: convertToLocalTimeString(new Date()))
    }
  }

  // Update state values
  state += deviceInfo.subMap(['impulseType', 'lastCommTime', 'lastUpdate'])
}

boolean checkChanged(final String attribute, final newStatus, final String unit=null) {
  final boolean changed = device.currentValue(attribute) != newStatus
  if (changed) {
    logInfo "${attribute.capitalize()} for device ${device.label} is ${newStatus}"
  }
  sendEvent(name: attribute, value: newStatus, unit: unit)
  return changed
}

void checkChangedDataValue(final String name, final value) {
  if (value != null && device.getDataValue(name) != value) {
    device.updateDataValue(name, value)
  }
}

private String convertToLocalTimeString(final Date dt) {
  final TimeZone timeZone = location?.timeZone
  if (timeZone) {
    return dt.format("yyyy-MM-dd h:mm:ss a", timeZone)
  }
  else {
    return dt.toString()
  }
}