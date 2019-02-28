package org.textup.rest

import grails.plugins.rest.client.RestResponse
import grails.test.mixin.*
import grails.test.mixin.support.*
import javax.servlet.http.HttpServletRequest
import org.joda.time.*
import org.quartz.*
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.textup.*
import org.textup.cache.*
import org.textup.job.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class CallRetryFunctionalSpec extends FunctionalSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    String _firstApiId
    String _retryApiId
    List _numbers

    def setup() {
        _firstApiId = TestUtils.randString()
        _retryApiId = TestUtils.randString()
        _numbers = [TestUtils.randPhoneNumberString(), TestUtils.randPhoneNumberString()]

        doSetup()
        remote.exec({ nums, apiId1, apiId2 ->
            MockedMethod.force(ctx.callService, "start") { fromNum, toNums ->
                TempRecordReceipt tempRpt1 = TempRecordReceipt.tryCreate(apiId1, toNums[0]).payload
                ctx.resultFactory.success(tempRpt1)
            }
            MockedMethod.force(ctx.callService, "retry") { from, toNums, existingApiId ->
                TempRecordReceipt tempRpt1 = TempRecordReceipt.tryCreate(apiId2, toNums[0]).payload
                RecordItems.findEveryForApiId(existingApiId).each { RecordItem rItem1 ->
                    rItem1.addReceipt(tempRpt1)
                    rItem1.save(flush: true, failOnError: true)
                }
                ctx.resultFactory.success(tempRpt1)
            }
            return
        }.curry(_numbers, _firstApiId, _retryApiId))
    }

    def cleanup() {
    	doCleanup()
    }

    void "test retry call on failure of initial call"() {
        given:
        String authToken = getAuthToken()
        def (Long cId, Long sId) = remote.exec({ un, nums ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1, false)
            nums.eachWithIndex { num, i -> ipr1.mergeNumber(PhoneNumber.create(num), i + 10) }
            ipr1.save(flush: true, failOnError: true)
            return [ipr1.id, s1.id]
        }.curry(loggedInUsername, _numbers))

        when: "creating a future message for a contact with two numbers"
        String msg = "hi"
        FutureMessageType fType = FutureMessageType.CALL
        RestResponse response = rest.post("${baseUrl}/v1/future-messages?contactId=${cId}") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
            json {
                "future-message" {
                    message = msg
                    type = fType.toString().toLowerCase()
                }
            }
        }

        then:
        response.status == ResultStatus.CREATED.intStatus
        response.json["future-message"].message == msg
        response.json["future-message"].type == fType.toString()

        when: "first call made fails in the status callback"
        // retrieve the future message key from the future message id
        String jKey = remote.exec({ Long fMsgId ->
            return FutureMessage.get(fMsgId).keyName
        }.curry(response.json["future-message"].id))
        // quartz not started in test environment so we must manually trigger the job
        remote.exec({ String jobKey, Long staffId ->
            FutureMessageJob job = new FutureMessageJob()
            job.futureMessageJobService = ctx.getBean("futureMessageJobService")
            job.threadService = ctx.getBean("threadService")
            job.execute([
                getMergedJobDataMap: { ->
                    [
                        (QuartzUtils.DATA_FUTURE_MESSAGE_KEY): jobKey,
                        (QuartzUtils.DATA_STAFF_ID): staffId
                    ] as JobDataMap
                },
                getTrigger: {
                    [
                        mayFireAgain: { -> true  }
                    ] as Trigger
                }
            ] as JobExecutionContext)
            return
        }.curry(jKey, sId))
        // then mock the status callback
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add("CallSid", _firstApiId)
        form.add("From", "1112223333")
        form.add("To", _numbers[0])
        form.add("CallStatus", ReceiptStatus.FAILED.statuses[0])
        form.add("CallDuration", "88")
        String requestUrl = "${baseUrl}/v1/public/records?"
        requestUrl += "${CallbackUtils.PARAM_HANDLE}=${CallbackUtils.STATUS}&"
        requestUrl += "${CallService.RETRY_REMAINING}=+1${_numbers[1..-1]}"
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "status is stored but second number is tried"
        response.status == ResultStatus.OK.intStatus
        remote.exec({ apiId1 ->
            return RecordItemReceipt.countByApiId(apiId1)
        }.curry(_firstApiId))
        remote.exec({ apiId2 ->
            return RecordItemReceipt.countByApiId(apiId2)
        }.curry(_retryApiId))
    }
}
