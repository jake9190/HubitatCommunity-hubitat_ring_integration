/**
 *  Ring Virtual Alarm Smoke & CO Listener Driver
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

import groovy.transform.Field

metadata {
  definition(name: "Ring Virtual Alarm Smoke & CO Listener", namespace: "ring-hubitat-codahq", author: "Ben Rimmasch") {
    capability "Battery"
    capability "CarbonMonoxideDetector"
    capability "Refresh"
    capability "Sensor"
    capability "SmokeDetector"
    capability "TamperAlert"

    attribute "commStatus", "enum", ["error", "ok", "update-queued", "updating", "waiting-for-join", "wrong-network"]
    attribute "firmware", "string"
    attribute "listeningCarbonMonoxide", "enum", ["listening", "inactive"]
    attribute "listeningSmoke", "enum", ["listening", "inactive"]
    attribute "testMode", "string"
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

void setValues(final Map deviceInfo) {
  logDebug "setValues(${deviceInfo})"

  if (deviceInfo.co != null) {
    final Map co = deviceInfo.co

    checkChanged("carbonMonoxide", ALARM_STATUS.getOrDefault(co.alarmStatus, "tested"))
    state.coEnabled = co.enabledTimeMs

    checkChanged("listeningCarbonMonoxide", co.enabled ? "listening" : "inactive")
  }

  if (deviceInfo.smoke != null) {
    final Map smoke = deviceInfo.smoke

    checkChanged("smoke", ALARM_STATUS.getOrDefault(smoke.alarmStatus, "tested"))
    state.smokeEnabled = smoke.enabledTimeMs

    checkChanged("listeningSmoke", smoke.enabled ? "listening" : "inactive")
  }

  if (deviceInfo.pending != null) {
    final Map deviceInfoPending = deviceInfo.pending

    if (deviceInfoPending.commands) {
      logDebug "Device ${device.label} will set the commands ${deviceInfoPending.commands} on ${getNextExpectedWakeupString(deviceInfo)}"
    }
  }

  if (deviceInfo.batteryLevel != null) {
    checkChanged("battery", deviceInfo.batteryLevel, "%")
  }

  // Update attributes where deviceInfo key is the same as attribute name and no conversion is necessary
  for (final entry in deviceInfo.subMap(["commStatus", "firmware", "tamper", "testMode"])) {
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

// @return Next expected wakeup time as a string.
// Gets value from deviceInfo first, falls back to state because nextExpectedWakeup isn't sent with every deviceInfo
String getNextExpectedWakeupString(final Map deviceInfo) {
  final Long nextExpectedWakeup = deviceInfo.nextExpectedWakeup ?: state.nextExpectedWakeup

  return nextExpectedWakeup ? new Date(nextExpectedWakeup).toString() : 'unknown'
}

boolean checkChanged(final String attribute, final newStatus, final String unit=null, final String type=null) {
  final boolean changed = device.currentValue(attribute) != newStatus
  if (changed) {
    logInfo "${attribute.capitalize()} for device ${device.label} is ${newStatus}"
  }
  sendEvent(name: attribute, value: newStatus, unit: unit, type: type)
  return changed
}

@Field final static Map<String, String> ALARM_STATUS = [
  active: 'detected',
  inactive: 'clear',
]