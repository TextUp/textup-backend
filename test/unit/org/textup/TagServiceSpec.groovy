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

@TestFor(TagService)
@Domain([TagMembership, Contact, Phone, ContactTag, 
	ContactNumber, Record, RecordItem, RecordNote, RecordText, 
	RecordCall, RecordItemReceipt, PhoneNumber, SharedContact, 
	TeamMembership, StaffPhone, Staff, Team, Organization, 
	Schedule, Location, TeamPhone, WeeklySchedule, TeamContactTag])
@TestMixin(HibernateTestMixin)
class TagServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }
    def setup() {
        super.setupData()
        service.resultFactory = getResultFactory()
    }
    def cleanup() { 
        super.cleanupData()
    }

    void "test create"() {
    	when: "we create for a nonexistent Team"
        Result res = service.create(Team, -88L, [:])

    	then:
        res.success == false 
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "tagService.create.noPhone"
        res.payload.status == UNPROCESSABLE_ENTITY

    	when: "we create tag with a unique name"
        String tagName = "Tag 1"
        Map createInfo = [name:tagName]
        res = service.create(Team, t1.id, createInfo)

    	then:
        res.success == true 
        res.payload instanceof TeamContactTag 
        res.payload.name == tagName
    }

    void "test update"() {
    	when: "we try to update a nonexistent tag"
        Result res = service.update(-88L, [:])

    	then: 
        res.success == false 
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "tagService.update.notFound"
        res.payload.status == NOT_FOUND

    	when: "we try to update with tag actions that is not list"
        Map updateInfo = [doTagActions:"I am not a list"]
        res = service.update(tag1.id, updateInfo)

    	then: 
        res.success == false 
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "tagService.update.tagActionNotList"
        res.payload.status == BAD_REQUEST

    	when: "we try to update tag action with nonexistent contact"
        updateInfo = [doTagActions:[
            [id:-88L, action:Constants.TAG_ACTION_ADD]
        ]]
        res = service.update(tag1.id, updateInfo)

    	then: 
        res.success == false 
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "tagService.update.contactNotFound"
        res.payload.status == NOT_FOUND

    	when: "we try to update tag action with forbidden contact"
        service.authService = [tagAndContactBelongToSame:{ Long tId, Long cId -> false }]
        updateInfo = [doTagActions:[
            [id:tC1.id, action:Constants.TAG_ACTION_ADD]
        ]]
        res = service.update(tag1.id, updateInfo)

    	then: 
        res.success == false 
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "tagService.update.contactForbidden"
        res.payload.status == FORBIDDEN

        when: "we update with tag action with unspecified action"
        service.authService = [tagAndContactBelongToSame:{ Long tId, Long cId -> true }]
        updateInfo = [doTagActions:[
            [id:c2.id, action:"invalid"]
        ]]
        res = service.update(tag2.id, updateInfo)

        then:
        res.success == false 
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "tagService.update.tagActionInvalid"
        res.payload.status == BAD_REQUEST

    	when: "we update with valid fields and tagActions"
        //c2 is not already in tag2
        assert TagMembership.countByContactAndTag(c2, tag2) == 0
        int baseline = TagMembership.count()
        String newName = "Tag888", newColor ="#123"
        updateInfo = [
            name:newName, 
            hexColor:newColor, 
            doTagActions:[
                [id:c2.id, action:Constants.TAG_ACTION_ADD]
            ]
        ]
        res = service.update(tag2.id, updateInfo)
        assert res.success
        tag2.save(flush:true, failOnError:true)

    	then:
        TagMembership.count() == baseline + 1
        res.payload instanceof ContactTag
        res.payload.id == tag2.id 
        res.payload.name == newName
        res.payload.hexColor == newColor
        TagMembership.countByContactAndTag(c2, tag2) == 1
    }

    void "test delete"() {
    	when: "we delete a nonexistent tag"
        Result res = service.delete(-88L)

    	then: 
        res.success == false 
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "tagService.delete.notFound"
        res.payload.status == NOT_FOUND

    	when: "we delete an existing team tag"
        int numMembers = teTag1.countAllMembers()
        int tBaseline = ContactTag.count(), 
            rBaseline = Record.count(), 
            mBaseline = TagMembership.count(), 
            iBaseline = RecordItem.count(),
            rptBaseline = RecordItemReceipt.count()
        res = service.delete(teTag1.id)
        assert res.success
        t1.save(flush:true, failOnError:true)

    	then:
        ContactTag.count() == tBaseline - 1
        Record.count() == rBaseline - 1
        TagMembership.count() == mBaseline - numMembers
        RecordItem.count() == iBaseline - 1
        RecordItemReceipt.count() == rptBaseline - 0
    	
        when: "we delete an existing tag belonging to a staff"
        numMembers = tag1.countAllMembers()
        tBaseline = ContactTag.count()
        mBaseline = TagMembership.count()
        res = service.delete(tag1.id)
        assert res.success
        t1.save(flush:true, failOnError:true)

        then: 
        ContactTag.count() == tBaseline - 1
        TagMembership.count() == mBaseline - numMembers
    }
}
