/**
 *  Ring Virtual Camera Device Driver
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
 *  2019-11-10: Initial
 *  2019-11-13: Added battery level support
 *  2019-11-15: Import URL
 *  2019-11-18: Differentiated between ring and motion events
 *  2020-02-29: Changed namespace
 *  2020-05-19: Snapshot preference
 *  2022-01-29: Connection state
 */

metadata {
  definition(name: "Ring Virtual Camera", namespace: "ring-hubitat-codahq", author: "Ben Rimmasch",
    importUrl: "https://raw.githubusercontent.com/codahq/ring_hubitat_codahq/master/src/drivers/ring-virtual-camera.groovy") {
    capability "Actuator"
    capability "Sensor"
    capability "Refresh"
    capability "Polling"
    capability "MotionSensor"
    capability "Battery"
    capability "PushableButton"

    command "getDings"
      
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

def installed() {
  pollDeviceStatus()
}

def updated() {
  checkChanged("numberOfButtons", 1)
  parent.snapshotOption(device.deviceNetworkId, snapshotPolling)
  pollDeviceStatus()
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

def getDings() {
  logDebug "getDings()"
  parent.simpleRequest("dings")
}

def childParse(type, params) {
  logDebug "childParse(type, msg)"
  logTrace "type ${type}"
  logTrace "params ${params}"

  if (type == "refresh") {
    logTrace "refresh"
    handleRefresh(params.msg)
  }
  else if (type == "dings") {
    logTrace "dings"
    handleDings(params.type, params.msg)
  }
  else {
    log.error "Unhandled type ${type}"
  }
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

private handleRefresh(json) {
  logDebug "handleRefresh(${json.description})"
    
  if (json.alerts != null && json.alerts.connection != null) {
    checkChanged("connection", json.alerts.connection) // devices seem to be considered offline after 20 minutes
  }

  if (json.battery_life != null && !["jbox_v1", "lpd_v1", "lpd_v2"].contains(device.getDataValue("kind"))) {
    checkChanged("battery", json.battery_life)
  }
  if (json.firmware_version && device.getDataValue("firmware") != json.firmware_version) {
    device.updateDataValue("firmware", json.firmware_version)
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
    if (type == "IFTTT") {
      def motionTimeout = 60
      runIn(motionTimeout, motionOff)
    }
  }
  else if (json.kind == "ding") {
    logInfo "${device.label} button 1 was pushed"
    sendEvent(name: "pushed", value: 1, isStateChange: true)
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
