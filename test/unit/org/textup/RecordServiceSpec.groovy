package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import spock.lang.Shared
import spock.lang.Specification
import grails.plugin.springsecurity.SpringSecurityService
import org.textup.util.CustomSpec
import org.joda.time.DateTime
import static org.springframework.http.HttpStatus.*
import org.textup.types.ReceiptStatus
import org.textup.types.ResultType
import org.textup.rest.TwimlBuilder
import org.textup.validator.OutgoingText

@TestFor(RecordService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole])
@TestMixin(HibernateTestMixin)
class RecordServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        super.setupData()
        service.resultFactory = getResultFactory()
        service.twimlBuilder = [noResponse: { ->
            new Result(type:ResultType.SUCCESS, success:true, payload:"noResponse")
        }] as TwimlBuilder
        service.authService = [getLoggedInAndActive: { -> s1 }] as AuthService
        service.socketService = [sendItems:{ List<RecordItem> items, String eventName ->
            new ResultList()
        }] as SocketService
        Phone.metaClass.sendText = { OutgoingText text, Staff staff ->
            Result res = new Result(type:ResultType.SUCCESS, success:true,
                payload:"sendText")
            new ResultList(res)
        }
        Phone.metaClass.startBridgeCall = { Contactable c1, Staff staff ->
            Result res = new Result(type:ResultType.SUCCESS, success:true,
                payload:[
                    methodName:"startBridgeCall",
                    contactable:c1
                ])
            new ResultList(res)
        }
    }

    def cleanup() {
        super.cleanupData()
    }

    // Status
    // ------

    void "test update status"() {
        given: "an existing receipt"
        String apiId = "iamsosecretApiId"
        [rText1, rText2, rTeText1, rTeText2].each {
            it.addToReceipts(apiId:apiId, receivedByAsString:"1112223333")
            it.save(flush:true, failOnError:true)
        }

        when: "nonexistent apiId"
        Result res = service.updateStatus(ReceiptStatus.SUCCESS, "nonexistentApiId")

        then:
        res.success == false
        res.type ==  ResultType.MESSAGE_STATUS
        res.payload.status == NOT_FOUND
        res.payload.code == "recordService.updateStatus.receiptsNotFound"

        when: "existing with invalid status"
        res = service.updateStatus(null, apiId)
        // clear changes to avoid optimistic locking exception
        RecordItemReceipt.withSession { it.clear() }

        then:
        res.success == false
        res.type ==  ResultType.VALIDATION
        res.payload.errorCount == 1

        when: "existing with valid status"
        res = service.updateStatus(ReceiptStatus.SUCCESS, apiId)

        then:
        res.success == true
        res.payload == "noResponse"
    }

    // Create
    // ------

    void "test determine class"() {
        when: "unknown entity"
        Result res = service.determineClass([:])

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == UNPROCESSABLE_ENTITY
        res.payload.code == "recordService.create.unknownType"

        when: "text"
        res = service.determineClass([contents:"hello"])

        then:
        res.success == true
        res.payload == RecordText

        when: "call"
        res = service.determineClass([callContact:c1.id])

        then:
        res.success == true
        res.payload == RecordCall
    }

    void "test create text"() {
        when: "no recipients"
        Map itemInfo = [contents:"hi"]
        ResultList resList = service.createText(t1.phone, itemInfo)

        then:
        resList.isAnySuccess == false
        resList.results.size() == 1
        resList.results[0].type == ResultType.MESSAGE_STATUS
        resList.results[0].payload.code == "recordService.create.noTextRecipients"
        resList.results[0].payload.status == BAD_REQUEST

        when:
        itemInfo.sendToPhoneNumbers = ["2223334444"]
        itemInfo.sendToContacts = [tC1.id]
        resList = service.createText(t1.phone, itemInfo)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload == "sendText"
    }

    void "test create call"() {
        when: "try to call multiple"
        Map itemInfo = [callSharedContact:sc2.id, callContact:c1.id]
        ResultList resList = service.createCall(s1.phone, itemInfo)

        then:
        resList.isAnySuccess == false
        resList.results.size() == 1
        resList.results[0].type == ResultType.MESSAGE_STATUS
        resList.results[0].payload.code == "recordService.create.canCallOnlyOne"
        resList.results[0].payload.status == BAD_REQUEST

        when: "call contact"
        itemInfo = [callContact:c1.id]
        resList = service.createCall(s1.phone, itemInfo)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload.methodName == "startBridgeCall"
        resList.results[0].payload.contactable == c1

        when: "call shared contact"
        itemInfo = [callSharedContact:sc2.contact.id]
        resList = service.createCall(s1.phone, itemInfo)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload.methodName == "startBridgeCall"
        resList.results[0].payload.contactable == sc2
    }

    void "test create overall"() {
        when: "no phone"
        ResultList resList = service.create(null, [:])

        then:
        resList.isAnySuccess == false
        resList.results.size() == 1
        resList.results[0].type == ResultType.MESSAGE_STATUS
        resList.results[0].payload.code == "recordService.create.noPhone"
        resList.results[0].payload.status == UNPROCESSABLE_ENTITY

        when: "invalid entity"
        resList = service.create(t1.phone, [:])

        then:
        resList.isAnySuccess == false
        resList.results.size() == 1
        resList.results[0].type == ResultType.MESSAGE_STATUS
        resList.results[0].payload.code == "recordService.create.unknownType"
        resList.results[0].payload.status == UNPROCESSABLE_ENTITY

        when: "text"
        Map itemInfo = [contents:"hi", sendToPhoneNumbers:["2223334444"],
            sendToContacts:[tC1.id]]
        resList = service.create(t1.phone, itemInfo)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload == "sendText"
    }
}
