/**
 *  Ring Virtual Switch Driver
 *
 *  Copyright 2019-2020 Ben Rimmasch
 *  Copyright 2021 Caleb Morse
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
  definition(name: "Ring Virtual Switch", namespace: "ring-hubitat-codahq", author: "Ben Rimmasch") {
    capability "Refresh"
    capability "Sensor"
    capability "Battery"
    capability "Switch"
    capability "TamperAlert"

    attribute "firmware", "string"
  }

  preferences {
    input name: "descriptionTextEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    input name: "traceLogEnable", type: "bool", title: "Enable trace logging", defaultValue: false
  }
}

void logInfo(msg) {
  if (descriptionTextEnable) { log.info msg }
}

void logDebug(msg) {
  if (logEnable) { log.debug msg }
}

void logTrace(msg) {
  if (traceLogEnable) { log.trace msg }
}

void refresh() {
  parent.refresh(device.getDataValue("src"))
}

def on() {
  parent.apiWebsocketRequestSetDevice(device.getDataValue("src"), device.getDataValue("zid"), ["on": true])
}

def off() {
  parent.apiWebsocketRequestSetDevice(device.getDataValue("src"), device.getDataValue("zid"), ["on": false])
}

void setValues(final Map deviceInfo) {
  logDebug "setValues(${deviceInfo})"

  if (deviceInfo.batteryLevel != null) {
    checkChanged("battery", deviceInfo.batteryLevel, "%")
  }

  // Update attributes where deviceInfo key is the same as attribute name and no conversion is necessary
  for (final entry in deviceInfo.subMap(["firmware", "switch", "tamper"])) {
    checkChanged(entry.key, entry.value)
  }

  // Update state values
  Map stateValues = deviceInfo.subMap(['impulseType', 'lastCommTime', 'lastUpdate', 'nextExpectedWakeup', 'signalStrength'])
  if (stateValues) {
      state << stateValues
  }
}

void setPassthruValues(final Map deviceInfo) {
  logDebug "setPassthruValues(${deviceInfo})"

  if (deviceInfo.percent != null) {
    log.warn "${device.label} is updating firmware: ${deviceInfo.percent}% complete"
  }
}

void runCleanup() {
  device.removeDataValue('firmware') // Is an attribute now
}

boolean checkChanged(final String attribute, final newStatus, final String unit=null, final String type=null) {
  final boolean changed = device.currentValue(attribute) != newStatus
  if (changed) {
    logInfo "${attribute.capitalize()} for device ${device.label} is ${newStatus}"
  }
  sendEvent(name: attribute, value: newStatus, unit: unit, type: type)
  return changed
}