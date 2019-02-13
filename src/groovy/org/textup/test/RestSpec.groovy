package org.textup.test

import grails.plugin.remotecontrol.RemoteControl
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import org.apache.http.HttpResponse
import org.apache.http.impl.client.*
import org.springframework.http.HttpStatus
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

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

    final String MOCKED_METHODS_CONFIG_KEY = "_restSpecMockedMethods"

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
        // Shared overrides -- primarily to avoid triggering external events when running tests
        remote.exec({ mockedMethodsKey ->
            Collection mockedMethods = []

            mockedMethods << MockedMethod.create(TwilioUtils, "validate") { ctx.resultFactory.success() }
            mockedMethods << MockedMethod.create(ctx.threadService, "submit") { action ->
                AsyncUtils.noOpFuture(action())
            }
            mockedMethods << MockedMethod.create(ctx.threadService, "delay") { delay, unit, action ->
                AsyncUtils.noOpFuture(action())
            }
            mockedMethods << MockedMethod.create(HttpUtils, "executeBasicAuthRequest") { un, pwd, req, action ->
                assert un != null
                assert pwd != null
                CloseableHttpClient client = HttpClients.createDefault()
                client.withCloseable {
                    HttpResponse resp = client.execute(req)
                    resp.withCloseable { action(resp) }
                }
            }
            mockedMethods << MockedMethod.create(ctx.mailService, "sendMail") {
                ctx.resultFactory.success()
            }
            mockedMethods << MockedMethod.create(ctx.staffService, "verifyCreateRequest") {
                ctx.resultFactory.success()
            }

            mockedMethods << MockedMethod.create(ctx.storageService, "uploadAsync") {
                new ResultGroup()
            }
            mockedMethods << MockedMethod.create(ctx.incomingMediaService, "finishProcessingUploads") {
                ctx.resultFactory.success()
            }

            mockedMethods << MockedMethod.create(ctx.textService, "send") { fromNum, toNums ->
                assert toNums.isEmpty() == false
                TempRecordReceipt temp = TestUtils.buildTempReceipt()
                temp.contactNumber = toNums[0]
                assert temp.validate()
                // return temp
                ctx.resultFactory.success(temp)
            }
            mockedMethods << MockedMethod.create(ctx.callService, "doCall") { fromNum, toNum ->
                TempRecordReceipt temp = TestUtils.buildTempReceipt()
                temp.contactNumber = toNum
                assert temp.validate()
                // return temp
                ctx.resultFactory.success(temp)
            }

            app.config[mockedMethodsKey] = mockedMethods
            return
        }.curry(MOCKED_METHODS_CONFIG_KEY))
        // Pass these into the integration data setup because when we are using
        // the RemoteControl plugin to populate the remote server with test data,
        // we cannot access any of the Shared fields and must supply our own values
        Map data = remote.exec(doSetup.curry(iterationCount))
        loggedInUsername = data.loggedInUsername
        loggedInPassword = data.loggedInPassword
    }

    void cleanupData() {
        remote.exec({ mockedMethodsKey ->
            Collection mockedMethods = app.config[mockedMethodsKey]
            mockedMethods.each { it.restore() }
            return
        }.curry(MOCKED_METHODS_CONFIG_KEY))
    }
}
