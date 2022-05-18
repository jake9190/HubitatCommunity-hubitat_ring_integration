/**
 *  Ring Virtual Camera with Siren Device Driver
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
 *
 *
 *  Change Log:
 *  2019-11-09: Initial
 *  2019-11-13: Added battery level support
 *  2019-11-15: Import URL
 *  2019-12-20: Started tinkering with getting thumbnails
 *  2020-02-29: Changed namespace
 *  2020-05-19: Snapshot preference
 *  2022-01-29: Connection state
 */

metadata {
  definition(name: "Ring Virtual Camera with Siren", namespace: "ring-hubitat-codahq", author: "Ben Rimmasch",
    importUrl: "https://raw.githubusercontent.com/codahq/ring_hubitat_codahq/master/src/drivers/ring-virtual-camera-with-siren.groovy") {
    capability "Actuator"
    capability "Sensor"
    capability "Refresh"
    capability "Polling"
    capability "Alarm"
    capability "MotionSensor"
    capability "Battery"

    command "getDings"
    //command "test"
      
    attribute "connection", "string"
  }

  preferences {
    input name: "deviceStatusPollingEnable", type: "bool", title: "Enable polling for device status", defaultValue: true
    input name: "snapshotPolling", type: "bool", title: "Enable polling for thumbnail snapshots on this device", defaultValue: false
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

def configure() {

}

def parse(String description) {
  logDebug "description: ${description}"
}

def poll() {
  refresh()
}

def refresh() {
  logDebug "refresh()"
  parent.simpleRequest("refresh", [dni: device.deviceNetworkId])
  scheduleDevicePolling()
}

def pollDeviceStatus() {
  logTrace "pollDeviceStatus()"
  refresh()
}

def scheduleDevicePolling() {
  unschedule(pollDeviceStatus)
  if (deviceStatusPollingEnable) {
        def Second = (new Date().format("s") as int)
        Second = ((Second + 5) % 60)
        schedule( "${ Second } 0/30 * ? * *", "refresh" )
  }
}

def getDings() {
  logDebug "getDings()"
  parent.simpleRequest("dings")
}

def test() {
  //parent.simpleRequest("history", [dni: device.deviceNetworkId])
  parent.simpleRequest("snapshot-image-tmp", [dni: device.deviceNetworkId])
}

def installed() {
  pollDeviceStatus()
}

def updated() {
  parent.snapshotOption(device.deviceNetworkId, snapshotPolling)
  pollDeviceStatus()
}

def off(boolean modifyAlarm = true) {
  parent.simpleRequest("device-set", [dni: device.deviceNetworkId, kind: "doorbots", action: "siren_off"])
}

def siren() {
  logDebug "Attempting to turn on siren."
  parent.simpleRequest("device-set", [dni: device.deviceNetworkId, kind: "doorbots", action: "siren_on"])
}

def strobe(value = "strobe") {
  log.error "Strobe not implemented for device type ${device.getDataValue("kind")}"
}

def both() {
  log.error "Both (strobe and siren) not implemented for device type ${device.getDataValue("kind")}"
}


def childParse(type, params) {
  logDebug "childParse(type, msg)"
  logTrace "type ${type}"
  logTrace "params ${params}"

  if (type == "refresh") {
    logTrace "refresh"
    handleRefresh(params.msg)
  }
  else if (type == "device-set") {
    logTrace "set"
    handleSet(type, params)
  }
  else if (type == "dings") {
    logTrace "dings"
    handleDings(params.type, params.msg)
  }
  else if (type == "snapshot-image") {
    state.snapshot = params.jpg
  }
  else {
    log.error "Unhandled type ${type}"
  }
}

private handleRefresh(json) {
  logDebug "handleRefresh(${json.description})"
  
  if (json.alerts != null && json.alerts.connection != null) {
    checkChanged("connection", json.alerts.connection) // devices seem to be considered offline after 20 minutes
  }
    
  if (json.battery_life != null) {
    checkChanged("battery", json.battery_life)
  }
  if (json.siren_status?.seconds_remaining != null) {
    def value = json.siren_status.seconds_remaining > 0 ? "siren" : "off"
    checkChanged("alarm", value)
    if (value == "siren") {
      runIn(json.siren_status.seconds_remaining + 1, refresh)
    }
  }
  if (json.firmware_version && device.getDataValue("firmware") != json.firmware_version) {
    device.updateDataValue("firmware", json.firmware_version)
  }
}

private handleSet(id, params) {
  logTrace "handleSet(${id}, ${params})"
  if (params.response != 200) {
    log.warn "Not successful?"
    return
  }
  if (params.action == "siren_on") {
    def value = device.currentValue("alarm") == "both" ? "both" : "siren"
    if (value != "both") {
      logInfo "Device ${device.label} alarm is ${value}"
      sendEvent(name: "alarm", value: value)
    }
    runIn(params.msg.seconds_remaining + 1, refresh)
  }
  else if (params.action == "siren_off") {
    logInfo "Device ${device.label} alarm is off"
    sendEvent(name: "alarm", value: "off")
  }
  else {
    log.error "Unsupported set ${params.action}"
  }

}

private handleDings(type, json) {
  logTrace "json: ${json}"
  if (json == null) {
    checkChanged("motion", "inactive")
  }
  else if (json.kind == "motion" && json.motion == true) {
    checkChanged("motion", "active")
    unschedule(motionOff)
  }
  if (type == "IFTTT") {
    def motionTimeout = 60
    runIn(motionTimeout, motionOff)
  }
}

def motionOff(data) {
  logDebug "motionOff($data)"
  childParse("dings", [msg: null])
}

def checkChanged(attribute, newStatus, unit=null) {
  if (device.currentValue(attribute) != newStatus) {
    logInfo "${attribute.capitalize()} for device ${device.label} is ${newStatus}"
    sendEvent(name: attribute, value: newStatus, unit: unit)
  }
}
