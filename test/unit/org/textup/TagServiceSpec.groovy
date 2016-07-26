package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.types.FutureMessageType
import org.textup.types.ResultType
import org.textup.util.CustomSpec
import spock.lang.Shared
import static org.springframework.http.HttpStatus.*

@TestFor(TagService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole,
    FutureMessage, SimpleFutureMessage])
@TestMixin(HibernateTestMixin)
class TagServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    int _cancelCalled = 0

    def setup() {
        super.setupData()
        service.resultFactory = getResultFactory()

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

    	when: "we create for a nonexistent Team"
        Map createInfo = [name:"Tag 1"]
        Result res = service.create(null, createInfo)

    	then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "tagService.create.noPhone"
        res.payload.status == UNPROCESSABLE_ENTITY
        ContactTag.count() == tBaseline

    	when: "we create tag with a unique name"
        res = service.create(t1.phone, createInfo)

    	then:
        res.success == true
        res.payload instanceof ContactTag
        res.payload.name == createInfo.name
        ContactTag.count() == tBaseline + 1
    }

    // Update
    // ------

    void "test find tag from id"() {
        when: "nonexistent tag"
        Result res = service.findTagFromId(-88L)

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "tagService.update.notFound"
        res.payload.status == NOT_FOUND

        when: "existing tag"
        res = service.findTagFromId(tag1.id)

        then:
        res.success == true
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
        res.payload instanceof ValidationErrors
        res.payload.errorCount == 1
        ContactTag.count() == tBaseline

        when: "we update with valid fields and tagActions"
        updateInfo.hexColor = "#123"
        res = service.updateTagInfo(tag2, updateInfo)
        assert res.success
        tag2.save(flush:true, failOnError:true)

        then:
        res.success == true
        res.payload instanceof ContactTag
        res.payload.name == updateInfo.name
        res.payload.hexColor == updateInfo.hexColor
        ContactTag.count() == tBaseline
    }

    void "test tag actions invalid"() {
        when: "we try to update with tag actions that is not list"
        Map updateInfo = [doTagActions:"I am not a list"]
        Result res = service.doTagActions(tag1, updateInfo)

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "tagService.update.tagActionNotList"
        res.payload.status == BAD_REQUEST

        when: "we try to update tag action with nonexistent contact"
        updateInfo = [doTagActions:[
            [id:-88L, action:Constants.TAG_ACTION_ADD]
        ]]
        res = service.doTagActions(tag1, updateInfo)

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "tagService.update.contactNotFound"
        res.payload.status == NOT_FOUND

        when: "we try to update tag action with forbidden contact"
        updateInfo = [doTagActions:[
            [id:tC1.id, action:Constants.TAG_ACTION_ADD]
        ]]
        res = service.doTagActions(tag1, updateInfo)

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "tagService.update.contactForbidden"
        res.payload.status == FORBIDDEN

        when: "we update with tag action with unspecified action"
        updateInfo = [doTagActions:[
            [id:c2.id, action:"invalid"]
        ]]
        res = service.doTagActions(tag2, updateInfo)

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "tagService.update.tagActionInvalid"
        res.payload.status == BAD_REQUEST
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
        res.payload instanceof ContactTag
        res.payload.id == tag2.id
    }

    void "test update overall"() {
        when: "invalid update"
        Map updateInfo = [name:"Tag888", hexColor:"invalid"]
        Result res = service.update(tag2.id, updateInfo)

        then:
        res.success == false
        res.payload instanceof ValidationErrors
        res.payload.errorCount == 1

        when: "valid update"
        updateInfo.hexColor = "#123"
        res = service.update(tag2.id, updateInfo)

        then:
        res.success == true
        res.payload instanceof ContactTag
        res.payload.id == tag2.id
        res.payload.name == updateInfo.name
        res.payload.hexColor == updateInfo.hexColor
    }

    // Delete
    // ------

    void "test delete"() {
    	when: "we delete a nonexistent tag"
        Result res = service.delete(-88L)

    	then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "tagService.delete.notFound"
        res.payload.status == NOT_FOUND

    	when: "we delete an existing team tag"
        res = service.delete(teTag1.id)
        assert res.success
        t1.save(flush:true, failOnError:true)

    	then:
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
        tag1.isDeleted == true
        _cancelCalled == numFutureMsgs
    }
}
