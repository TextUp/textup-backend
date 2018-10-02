package org.textup.rest

import grails.plugins.rest.client.RestResponse
import java.util.concurrent.TimeUnit
import java.util.UUID
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.quartz.Trigger
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.textup.*
import org.textup.job.FutureMessageJob
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.BasePhoneNumber
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt
import static org.springframework.http.HttpStatus.*

class CallRetryFunctionalSpec extends RestSpec {

    String _firstApiId
    String _retryApiId
    List<String> _numbers = ["1112223333", "2223338888"]

    def setup() {
        _firstApiId = UUID.randomUUID().toString()
        _retryApiId = UUID.randomUUID().toString()

        setupData()
        remote.exec({ nums, apiId1, apiId2 ->
            // ensure that callbackService validates all requests
            ctx.callbackService.metaClass.validate = { HttpServletRequest request,
                TypeConvertingMap params ->
                ctx.resultFactory.success()
            }
            ctx.callService.metaClass.start = { PhoneNumber fromNum,
                List<? extends BasePhoneNumber> toNums, Map afterPickup ->
                String toNumAsString = toNums[0].number

                String apiId = apiId1
                ctx.resultFactory.success(new TempRecordReceipt(apiId:apiId,
                    contactNumberAsString:toNumAsString))
            }
            ctx.callService.metaClass.retry = { PhoneNumber fromNum,
                List<? extends BasePhoneNumber> toNums,
                String existingApiId, Map afterPickup ->

                String toNumAsString = toNums[0].number
                String retryApiId = apiId2
                TempRecordReceipt tempReceipt = new TempRecordReceipt(apiId:retryApiId,
                    contactNumberAsString:toNumAsString)
                RecordItem.findEveryByApiId(existingApiId).each { RecordItem item1 ->
                    item1.addReceipt(tempReceipt)
                    item1.save(flush:true, failOnError:true)
                }
                ctx.resultFactory.success(tempReceipt)
            }
            // make threadService execute within this same thread synchronously for testing
            ctx.threadService.metaClass.submit = { long delay, TimeUnit unit, Closure action ->
                action(); return null;
            }
            return
        }.curry(_numbers, _firstApiId, _retryApiId))
    }

    def cleanup() {
    	cleanupData()
    }

    void "test retry call on failure of initial call"() {
        given:
        String authToken = getAuthToken()
        Long cId
        Long sId
        ( cId, sId ) = remote.exec({ un, nums ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = s1.phone
            Contact contact = p1.createContact([:], nums).payload
            contact.save(flush:true, failOnError:true)
            return [contact.id, s1.id]
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
        response.status == CREATED.value()
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
            job.futureMessageService = ctx.getBean("futureMessageService")
            job.resultFactory = ctx.getBean("resultFactory")
            job.execute([
                getMergedJobDataMap: { ->
                    [
                        (Constants.JOB_DATA_FUTURE_MESSAGE_KEY):jobKey,
                        (Constants.JOB_DATA_STAFF_ID):staffId
                    ] as JobDataMap
                },
                getTrigger: {
                    [
                        mayFireAgain: { -> true  }
                    ] as Trigger
                }
            ] as JobExecutionContext)
        }.curry(jKey, sId))
        // then mock the status callback
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add("CallSid", _firstApiId)
        form.add("From", "1112223333")
        form.add("To", _numbers[0])
        form.add("CallStatus", ReceiptStatus.FAILED.statuses[0])
        form.add("CallDuration", "88")
        String requestUrl = "${baseUrl}/v1/public/records?"
        requestUrl += "handle=${Constants.CALLBACK_STATUS}"
        requestUrl += "&remaining=+1${_numbers[1..-1]}"
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "status is stored but second number is tried"
        response.status == OK.value()
        remote.exec({ apiId1 ->
            return RecordItemReceipt.countByApiId(apiId1)
        }.curry(_firstApiId))
        remote.exec({ apiId2 ->
            return RecordItemReceipt.countByApiId(apiId2)
        }.curry(_retryApiId))
    }
}
