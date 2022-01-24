/**
 *  Ring Virtual Keypad Driver
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

import groovy.transform.Field

metadata {
  definition(name: "Ring Virtual Keypad", namespace: "ring-hubitat-codahq", author: "Ben Rimmasch",
    importUrl: "https://raw.githubusercontent.com/codahq/ring_hubitat_codahq/master/src/drivers/ring-virtual-keypad.groovy") {
    capability "Sensor"
    capability "Motion Sensor"
    capability "Audio Volume"
    capability "Battery"

    attribute "brightness", "number"
    attribute "lastCheckin", "string"

    command "setBrightness", [[name: "Set LED Brightness*", type: "NUMBER", range: "0..100", description: "Choose a value between 0 and 100"]]
  }

  preferences {
    input name: "motionTimeout", type: "number", range: 5..600, title: "Time in seconds before motion resets to inactive", defaultValue: 15
    input name: "descriptionTextEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    input name: "traceLogEnable", type: "bool", title: "Enable trace logging", defaultValue: false
  }
}

@Field static Integer VOLUME_INC = 5 //somebody can make this a preference if they feel strongly about it

private logInfo(msg) {
  if (descriptionTextEnable) log.info msg
}

def logDebug(msg) {
  if (logEnable) log.debug msg
}

def logTrace(msg) {
  if (traceLogEnable) log.trace msg
}

def setVolume(vol) {
  logDebug "Attempting to set volume."
  vol = vol > 100 ? 100 : vol
  vol = vol < 0 ? 0 : vol

  if (vol == 0) {
    if (checkChanged("mute", "muted")) {
      state.prevVolume = device.currentValue("volume")
    }
  }
  else {
    checkChanged("mute", "unmuted")
  }

  if (device.currentValue("volume") != vol) {
    Map data = ["volume": (vol == null ? 50 : vol).toDouble() / 100]
    parent.simpleRequest("setdevice", [zid: device.getDataValue("zid"), dst: null, data: data])
  }
  else {
    logInfo "Already at volume."
    sendEvent(name: "volume", value: device.currentValue("volume"))
  }
}

def volumeUp() {
  logDebug "Attempting to raise volume."
  Integer nextVol = device.currentValue("volume") + VOLUME_INC
  if (nextVol <= 100) {
    setVolume(nextVol)
  }
  else {
    logInfo "Already max volume."
    sendEvent(name: "volume", value: device.currentValue("volume"))
  }
}

def volumeDown() {
  logDebug "Attempting to lower volume."
  Integer nextVol = device.currentValue("volume") - VOLUME_INC
  if (nextVol >= 0) {
    setVolume(nextVol)
  }
  else {
    logInfo "Already min volume."
    sendEvent(name: "volume", value: device.currentValue("volume"))
  }
}

def mute() {
  logDebug "Attempting to mute."
  setVolume(0)
}

def unmute() {
  logDebug "Attempting to unmute."
  setVolume(state.prevVolume)
}

def setBrightness(brightness) {
  logDebug "Attempting to set brightness ${brightness}."
  brightness = brightness > 100 ? 100 : brightness
  brightness = brightness < 0 ? 0 : brightness
  Map data = ["brightness": (brightness == null ? 100 : brightness).toDouble() / 100]
  parent.simpleRequest("setdevice", [zid: device.getDataValue("zid"), dst: null, data: data])
}

def refresh() {
  logDebug "Attempting to refresh."
  parent.simpleRequest("refresh", [dst: device.deviceNetworkId])
}

void stopMotion() {
  checkChanged("motion", "inactive")
}

void setValues(final Map deviceInfo) {
  logDebug "updateDevice(deviceInfo)"
  logTrace "deviceInfo: ${deviceInfo}"

  if (deviceInfo.state != null) {
    final Map deviceInfoState = deviceInfo.state

    for (final String key in ['brightness', 'volume']) {
      final keyVal = deviceInfoState.get(key)
      if (keyVal != null) {
        checkChanged(key, (keyVal * 100).toInteger())
      }
    }
  }

  if (deviceInfo.batteryLevel != null) {
    checkChanged("battery", deviceInfo.batteryLevel, "%")
  }

  if (deviceInfo.impulseType != null) {
    final String impulseType = deviceInfo.impulseType
    state.impulseType = impulseType
    if (impulseType == "comm.heartbeat") {
      sendEvent(name: "lastCheckin", value: convertToLocalTimeString(new Date()))
    } else if (value == "keypad.motion") {
      checkChanged("motion", "active")
      //The inactive message almost never comes reliably. for now we'll schedule it off
      runIn(motionTimeout.toInteger(), stopMotion)
    }
  }

  for(final String key in ['lastCommTime', 'lastUpdate', 'nextExpectedWakeup', 'signalStrength']) {
    final keyVal = deviceInfo[key]
    if (keyVal != null) {
      state[key] = keyVal
    }
  }

  for(final String key in ['firmware', 'hardwareVersion']) {
    final keyVal = deviceInfo[key]
    if (keyVal != null && device.getDataValue(key) != keyVal) {
      device.updateDataValue(key, keyVal)
    }
  }
}

boolean checkChanged(final String attribute, final newStatus, final String unit=null) {
  final boolean changed = device.currentValue(attribute) != newStatus
  if (changed) {
    logInfo "${attribute.capitalize()} for device ${device.label} is ${newStatus}"
  }
  sendEvent(name: attribute, value: newStatus, unit: unit)
  return changed
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
