package org.textup.test

import grails.plugin.remotecontrol.RemoteControl
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import org.apache.http.HttpResponse
import org.apache.http.impl.client.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

//from AbstractRestSpec.groovy at https://github.com/alvarosanchez/grails-spring-security-rest
//remote control from https://github.com/craigatk/grails-api-testing/
abstract class FunctionalSpec extends Specification {

    static final String MOCKED_METHODS_CONFIG_KEY = "_restSpecMockedMethods"

    @Shared
    RestBuilder rest = new RestBuilder(connectTimeout: 10000, readTimeout: 20000)

    RemoteControl remote
    String baseUrl = "http://localhost:8080"
    String loggedInPassword
    String loggedInUsername

    void doSetup() {
        remote = new RemoteControl()
        // pre-seed with user to log in with
        (loggedInUsername, loggedInPassword) = remote.exec { ->
            String rawPassword = TestUtils.randString()
            // active org
            Organization org1 = TestUtils.buildOrg(OrgStatus.APPROVED)
            // active staff
            Staff s1 = TestUtils.buildStaff(org1)
            s1.status = StaffStatus.ADMIN
            s1.password = rawPassword
            // active phone
            Phone p1 = TestUtils.buildActiveStaffPhone(s1)
            // appropriate role
            Roles.tryGetUserRole()
                .then { Role r1 -> StaffRole.tryCreate(s1, r1) }
                .logFail("FunctionalSpec")
            [s1.username, rawPassword]
        }
        // override key external-facing methods
        remote.exec({ mockedMethodsKey ->
            Collection mockedMethods = []
            mockedMethods << MockedMethod.create(TwilioUtils, "validate") { Result.void() }
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
            mockedMethods << MockedMethod.create(ctx.mailService, "sendMail") { Result.void() }

            mockedMethods << MockedMethod.create(ctx.storageService, "uploadAsync") {
                new ResultGroup()
            }
            mockedMethods << MockedMethod.create(ctx.incomingMediaService, "finishProcessingUploads") {
                Result.void()
            }

            mockedMethods << MockedMethod.create(ctx.textService, "send") { fromNum, toNums ->
                assert toNums.isEmpty() == false
                TempRecordReceipt temp = TempRecordReceipt
                    .tryCreate(TestUtils.randString(), toNums[0])
                    .payload
                assert temp.validate()
                // return temp
                ctx.resultFactory.success(temp)
            }
            mockedMethods << MockedMethod.create(ctx.callService, "doCall") { fromNum, toNum ->
                TempRecordReceipt temp = TempRecordReceipt
                    .tryCreate(TestUtils.randString(), toNum)
                    .payload
                assert temp.validate()
                // return temp
                ctx.resultFactory.success(temp)
            }

            app.config[mockedMethodsKey] = mockedMethods
            return
        }.curry(MOCKED_METHODS_CONFIG_KEY))
    }

    void doCleanup() {
        remote.exec({ mockedMethodsKey ->
            app.config[mockedMethodsKey].each { it.restore() }
            return
        }.curry(MOCKED_METHODS_CONFIG_KEY))
    }

    String getAuthToken() {
        // For some reason, unless we rename these variables in our local context, they
        // do not execute in the RestBuilder DSL syntax
        String thisUn = loggedInUsername
        String thisPwd = loggedInPassword
        RestResponse authResponse = rest.post("$baseUrl/login") {
            contentType "application/json"
            json {
                username = thisUn
                password = thisPwd
            }
        }
        authResponse.json?.access_token
    }
}
