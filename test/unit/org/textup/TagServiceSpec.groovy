package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.type.FutureMessageType
import org.textup.util.CustomSpec
import spock.lang.Shared

@TestFor(TagService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole,
    FutureMessage, SimpleFutureMessage, NotificationPolicy])
@TestMixin(HibernateTestMixin)
class TagServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    int _cancelCalled = 0

    def setup() {
        super.setupData()
        service.resultFactory = getResultFactory()
        service.notificationService = [
            handleNotificationActions: { Phone p1, Long recordId, Object rawActions ->
                new Result(status:ResultStatus.NO_CONTENT, payload:null)
            }
        ] as NotificationService

        FutureMessage.metaClass.refreshTrigger = { -> null }
        FutureMessage.metaClass.doUnschedule = { ->
            _cancelCalled++
            new Result(success:true)
        }
    }

    def cleanup() {
        super.cleanupData()
    }

    // Create
    // ------

    void "test create"() {
        given: "baselines"
        int tBaseline = ContactTag.count()
        addToMessageSource("tagService.create.noPhone")

    	when: "we create for a nonexistent Team"
        Map createInfo = [name:"Tag 1"]
        Result res = service.create(null, createInfo)

    	then:
        res.success == false
        res.errorMessages[0] == "tagService.create.noPhone"
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        ContactTag.count() == tBaseline

    	when: "we create tag with a unique name"
        t1.phone.resultFactory = service.resultFactory
        res = service.create(t1.phone, createInfo)

    	then:
        res.success == true
        res.status == ResultStatus.CREATED
        res.payload instanceof ContactTag
        res.payload.name == createInfo.name
        ContactTag.count() == tBaseline + 1
    }

    // Update
    // ------

    void "test find tag from id"() {
        given:
        addToMessageSource("tagService.update.notFound")

        when: "nonexistent tag"
        Result res = service.findTagFromId(-88L)

        then:
        res.success == false
        res.errorMessages[0] == "tagService.update.notFound"
        res.status == ResultStatus.NOT_FOUND

        when: "existing tag"
        res = service.findTagFromId(tag1.id)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof ContactTag
        res.payload.id == tag1.id
    }

    void "test update tag info"() {
        given: "baselines"
        int tBaseline = ContactTag.count()

        when: "update with invalid fields"
        Map updateInfo = [name:"Tag888", hexColor:"invalid"]
        Result res = service.updateTagInfo(tag2, updateInfo)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        ContactTag.count() == tBaseline

        when: "we update with valid fields and tagActions"
        updateInfo.hexColor = "#123"
        res = service.updateTagInfo(tag2, updateInfo)
        assert res.success
        tag2.save(flush:true, failOnError:true)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof ContactTag
        res.payload.name == updateInfo.name
        res.payload.hexColor == updateInfo.hexColor
        ContactTag.count() == tBaseline
    }

    void "test updating with notification actions"() {
        given: "baselines"
        int tBaseline = ContactTag.count()

        when: "we try to delete nonexistent number"
        Map notifActions = [doNotificationActions:[[hello:"yes"]]]
        Result res = service.handleNotificationActions(tag1, notifActions)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof ContactTag
        res.payload.id == tag1.id
        ContactTag.count() == tBaseline
    }

    void "test tag actions invalid"() {
        given:
        service.resultFactory.messageSource = mockMessageSourceWithResolvable()
        addToMessageSource("tagService.update.contactForbidden")

        when: "we try to update with tag actions that is not list"
        Map updateInfo = [doTagActions:"I am not a list"]
        Result res = service.doTagActions(tag1, updateInfo)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        res.errorMessages.contains("emptyOrNotACollection")

        when: "we try to update tag action with nonexistent contact"
        updateInfo = [doTagActions:[
            [id:-88L, action:Constants.TAG_ACTION_ADD]
        ]]
        res = service.doTagActions(tag1, updateInfo)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        res.errorMessages.contains("actionContainer.invalidActions")

        when: "we try to update tag action with forbidden contact"
        service.resultFactory.messageSource = messageSource
        updateInfo = [doTagActions:[
            [id:tC1.id, action:Constants.TAG_ACTION_ADD]
        ]]
        res = service.doTagActions(tag1, updateInfo)

        then:
        res.success == false
        res.errorMessages[0] == "tagService.update.contactForbidden"
        res.status == ResultStatus.FORBIDDEN

        when: "we update with tag action with unspecified action"
        service.resultFactory.messageSource = mockMessageSourceWithResolvable()
        updateInfo = [doTagActions:[
            [id:c2.id, action:"invalid"]
        ]]
        res = service.doTagActions(tag2, updateInfo)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        res.errorMessages.contains("actionContainer.invalidActions")
    }

    void "test tag actions valid"() {
        given: "no members in tag"
        tag2.members?.clear()
        tag2.save(flush:true, failOnError:true)

        when: "add contact to tag"
        Map updateInfo = [doTagActions:[
            [id:c2.id, action:Constants.TAG_ACTION_ADD]
        ]]
        Result res = service.doTagActions(tag2, updateInfo)
        assert res.success
        tag2.save(flush:true, failOnError:true)

        then:
        tag2.members.size() == 1
        tag2.members.contains(c2)
        res.status == ResultStatus.OK
        res.payload instanceof ContactTag
        res.payload.id == tag2.id

        when: "remove contact from tag"
        updateInfo = [doTagActions:[
            [id:c2.id, action:Constants.TAG_ACTION_REMOVE]
        ]]
        res = service.doTagActions(tag2, updateInfo)
        assert res.success
        tag2.save(flush:true, failOnError:true)

        then:
        tag2.members.isEmpty() == true
        tag2.members.contains(c2) == false
        res.status == ResultStatus.OK
        res.payload instanceof ContactTag
        res.payload.id == tag2.id
    }

    void "test update overall"() {
        when: "invalid update"
        Map updateInfo = [name:"Tag888", hexColor:"invalid"]
        Result res = service.update(tag2.id, updateInfo)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1

        when: "valid update"
        updateInfo.hexColor = "#123"
        res = service.update(tag2.id, updateInfo)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof ContactTag
        res.payload.id == tag2.id
        res.payload.name == updateInfo.name
        res.payload.hexColor == updateInfo.hexColor
    }

    // Delete
    // ------

    void "test delete"() {
        given:
        addToMessageSource("tagService.delete.notFound")

    	when: "we delete a nonexistent tag"
        Result res = service.delete(-88L)

    	then:
        res.success == false
        res.errorMessages[0] == "tagService.delete.notFound"
        res.status == ResultStatus.NOT_FOUND

    	when: "we delete an existing team tag"
        res = service.delete(teTag1.id)
        assert res.success
        t1.merge(flush:true, failOnError:true)

    	then:
        res.status == ResultStatus.NO_CONTENT
        teTag1.phone.tags.contains(teTag1) == false
        teTag1.members.every { !it.tags.contains(teTag1) }
    }

    void "test delete for tag with future messages"() {
        given:
        FutureMessage fMsg1 = new FutureMessage(record:tag1.record,
            type:FutureMessageType.CALL, message:"hi")
        fMsg1.save(flush:true, failOnError:true)

        int numFutureMsgs = tag1.record.countFutureMessages()

        when: "deleting"
        _cancelCalled = 0
        Result res = service.delete(tag1.id)
        assert res.success
        tag1.save(flush:true, failOnError:true)

        then: "tag marked as deleted and all future messages cancelled"
        res.status == ResultStatus.NO_CONTENT
        tag1.isDeleted == true
        _cancelCalled == numFutureMsgs
    }
}
