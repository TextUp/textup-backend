package org.textup.util 

import grails.plugin.remotecontrol.RemoteControl
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import org.springframework.http.HttpStatus
import spock.lang.Shared
import spock.lang.Specification

//from AbstractRestSpec.groovy at https://github.com/alvarosanchez/grails-spring-security-rest
//remote control from https://github.com/craigatk/grails-api-testing/
abstract class RestSpec extends Specification {

    @Shared 
    ConfigObject config = new ConfigSlurper()
        .parse(new File("grails-app/conf/Config.groovy").toURL())

    @Shared 
    RestBuilder rest = new RestBuilder(connectTimeout:10000, readTimeout:20000)

    @Shared
    int iterationCount = 0

    RestRemote remote 
    String loggedInUsername
    String loggedInPassword
    
    String baseUrl = "http://localhost:8080"

    String getAuthToken() {
        getAuthToken(loggedInUsername, loggedInPassword)
    }

    String getAuthToken(String k, String p) {
        RestResponse authResponse = rest.post("$baseUrl/login") {
            contentType "application/json"
            json {
                keyword = "$k".toString()
                password = "$p".toString()
            }
        }
        authResponse.json?.access_token
    }

    protected void setupData() {
        remote = new RestRemote(iterationCount)
        loggedInUsername = remote.loggedInUsername
        loggedInPassword = remote.loggedInPassword
    }

    protected void cleanupData() {
        iterationCount++
    }
}