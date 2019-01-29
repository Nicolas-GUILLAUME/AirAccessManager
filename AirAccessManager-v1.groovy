/**
 *  AirAccessManager stand alone
 *
 *  Copyright 2019 NICOLAS GUILLAUME
 *
 */
definition(
    name: "Air Access Manager - Service",
    namespace: "com.airaccessmanager",
    author: "Nicolas Guillaume",
    description: "Application that allows airaccessmanager.com to add and remove codes for your Airbnb guests.",
    category: "",
    iconUrl: "https://s3.amazonaws.com/airaccessmanager.com/logo_background-low-res.png",
    iconX2Url: "https://s3.amazonaws.com/airaccessmanager.com/logo_background.png",
    iconX3Url: "https://s3.amazonaws.com/airaccessmanager.com/logo_background.png",
    oauth: true)

preferences {
  section("Smart lock used for Airbnb listings:") {
    input "doorlock", "capability.lockCodes", required: true
  }
}

def installed() {
  log.debug "Installed with settings: ${settings}"
  initialize()
}

def updated() {
  log.debug "Updated with settings: ${settings}"
  unsubscribe()
  initialize()
}

def initialize() {
  	subscribe(doorlock, "codeChanged", codeChangedEvent);
}

mappings {
  path("/locks") {
    action: [
      GET: "listLocks",
      POST: "updateCode",
      DELETE: "deleteCode"
    ]
  }
}

def listLocks() {
    def lockMap = [:]
  	log.debug doorlock
    doorlock.each { lock->
        def lockState = [:]
        lockState["displayName"] = lock.displayName;
        lockState["name"] = lock.name
        lockState["codes"] = parseJson(lock.currentValue("lockCodes") ?: "{}") ?: [:]
        lockState["codeLength"] = lock.currentValue("codeLength")
    	lockState["maxCodes"] = lock.currentValue("maxCodes")
    	lockState["battery"] = lock.device.currentValue("battery")
        lockMap[lock.id] = lockState
    }
    log.debug "List locks response: ${lockMap}"
    return lockMap;
}

def updateCode() {
    doorlock.each { lock->
    	if (lock.id == params.lock_uuid) {
        	log.debug "Set code: | params: ${params} Code length: ${lock.currentValue("codeLength")}"
            atomicState[createIndexKeyFor(params.code_index)] = [code: params.code, name: params.name, validation_token: params.validation_token]
        	lock.setCode(params.code_index.toInteger(), params.code, params.name)
            return true // break each loop
        }
    }
}

def deleteCode() {
    doorlock.each { lock->
    	if (lock.id == params.lock_uuid) {
    		log.debug "Delete code: | params: ${params}"
            atomicState[createIndexKeyFor(params.code_index)] = [name: params.name, validation_token: params.validation_token]
    		lock.deleteCode(params.code_index.toInteger())
            return true // break loop
        }
    }
}

def codeChangedEvent(evt) {
    def splitState = evt.value.split(" ", 2)
    def details = atomicState[createIndexKeyFor(splitState[0])]
    def status = splitState[1]
    log.debug "codeChangedEvent. Target: $details, Context: $evt.value: $evt.data, $evt.jsonData, $settings"
    switch (status) {
    	case 'failed':
    		def message = "Code update failure. Target: $details, Context: $evt.value: $evt.data, $evt.jsonData, $settings"
        	log.debug message
        	sendPush(message)
        	break
        case 'deleted':
        	def message = "Code deleted for ${extractShortName(details['name'])}."
        	log.debug message
        	sendPush(message)
        	break
        case 'set':
            def message = "Code ${details['code']} added for ${extractShortName(details['name'])}."
    		log.debug message
        	sendPush(message)
            break
        case 'changed':
        case 'unset':
        case 'renamed':
        default:
        	log.debug "Unexpected state: $status. Target: $details, Context: $evt.value: $evt.data, $evt.jsonData, $settings" 
        	break   
    }
    // Send validation that the code was changed.
    def params = [
    	uri: "https://airaccessmanager.com/lock_sync_validation/validate",
    	body: [ validation_token: details['validation_token']]
        ]
        
    try {
    	httpPostJson(params) { resp ->
        	log.debug "Validation code: ${resp.status}"
        	resp.headers.each {
    	        log.debug "${it.name} : ${it.value}"
	        }
        	log.debug "response contentType: ${resp.contentType}"
    	}
	} catch (e) {
    	log.debug "something went wrong: $e"
	}
}

def codeReportEvent(evt) {
	log.debug "codeReportEvent. Context: $evt.value: $evt.data, $evt.jsonData, $settings"
}

def createIndexKeyFor(id) {
	return "index-${id}"
}

def extractShortName(name) {
	def lastCharIndex = name.length() - 1;
	return name.getAt(6..lastCharIndex)
}
