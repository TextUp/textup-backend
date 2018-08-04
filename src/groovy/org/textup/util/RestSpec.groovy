package org.textup.util

import grails.plugin.remotecontrol.RemoteControl
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import org.springframework.http.HttpStatus
import org.textup.*
import org.textup.type.*
import org.textup.validator.*
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
    int iterationCount

    RemoteControl remote = new RemoteControl()

    String loggedInUsername
    String loggedInPassword
    String baseUrl = "http://localhost:8080"

    // Helpers
    // -------

    String getAuthToken() {
        getAuthToken(loggedInUsername, loggedInPassword)
    }

    String getAuthToken(String un, String pwd) {
        RestResponse authResponse = rest.post("$baseUrl/login") {
            contentType "application/json"
            json {
                username = un
                password = pwd
            }
        }
        authResponse.json?.access_token
    }

    // Setup data
    // ----------

    Closure doSetup = { int iterationCount ->
        CustomSpec spec = new CustomSpec()
        spec.setupIntegrationData(iterationCount)
        [loggedInUsername:spec.loggedInUsername, loggedInPassword:spec.loggedInPassword]
    }

    void setupData() {
        // re-seed each time to ensure generation of random numbers
        long seed = Math.random() * Math.pow(10, 10)
        Random randomGenerator = new Random(seed)
        iterationCount = randomGenerator.nextInt(100000000)
        // Pass these into the integration data setup because when we are using
        // the RemoteControl plugin to populate the remote server with test data,
        // we cannot access any of the Shared fields and must supply our own values
        Map data = remote.exec(doSetup.curry(iterationCount))
        loggedInUsername = data.loggedInUsername
        loggedInPassword = data.loggedInPassword
    }

    void cleanupData() {}
}
