package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

// TODO

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestFor(TagService)
@TestMixin(HibernateTestMixin)
class TagServiceSpec extends CustomSpec {

    // static doWithSpring = {
    //     resultFactory(ResultFactory)
    // }

    // def setup() {
    //     setupData()
    //     service.resultFactory = TestUtils.getResultFactory(grailsApplication)
    //     service.notificationService = [
    //         handleNotificationActions: { Phone p1, Long recordId, Object rawActions ->
    //             new Result(status:ResultStatus.NO_CONTENT, payload:null)
    //         }
    //     ] as NotificationService
    //     FutureMessage.metaClass.refreshTrigger = { -> null }
    // }

    // def cleanup() {
    //     cleanupData()
    // }

    // // Create
    // // ------

    // void "test create"() {
    //     given: "baselines"
    //     int tBaseline = ContactTag.count()
    //     int rBaseline = Record.count()

    // 	when: "we create for a nonexistent Team"
    //     Map createInfo = [name:"Tag 1"]
    //     Result res = service.create(null, createInfo)

    // 	then:
    //     res.success == false
    //     res.errorMessages[0] == "tagService.create.noPhone"
    //     res.status == ResultStatus.UNPROCESSABLE_ENTITY
    //     ContactTag.count() == tBaseline
    //     Record.count() == rBaseline

    // 	when: "we create tag with a unique name"
    //     createInfo.language = VoiceLanguage.KOREAN.toString()
    //     res = service.create(t1.phone, createInfo)

    // 	then:
    //     res.success == true
    //     res.status == ResultStatus.CREATED
    //     res.payload instanceof ContactTag
    //     res.payload.language == VoiceLanguage.KOREAN
    //     res.payload.name == createInfo.name
    //     ContactTag.count() == tBaseline + 1
    //     Record.count() == rBaseline + 1
    // }

    // // Update
    // // ------

    // void "test find tag from id"() {
    //     when: "nonexistent tag"
    //     Result res = service.findTagFromId(-88L)

    //     then:
    //     res.success == false
    //     res.errorMessages[0] == "tagService.update.notFound"
    //     res.status == ResultStatus.NOT_FOUND

    //     when: "existing tag"
    //     res = service.findTagFromId(tag1.id)

    //     then:
    //     res.success == true
    //     res.status == ResultStatus.OK
    //     res.payload instanceof ContactTag
    //     res.payload.id == tag1.id
    // }

    // void "test update tag info"() {
    //     given: "baselines"
    //     int tBaseline = ContactTag.count()

    //     when: "update with invalid fields"
    //     Map updateInfo = [name:"Tag888", hexColor:"invalid"]
    //     Result res = service.updateTagInfo(tag2, updateInfo)

    //     then:
    //     res.success == false
    //     res.status == ResultStatus.UNPROCESSABLE_ENTITY
    //     res.errorMessages.size() == 1
    //     ContactTag.count() == tBaseline

    //     when: "we update with valid fields and tagActions"
    //     updateInfo.hexColor = "#123"
    //     res = service.updateTagInfo(tag2, updateInfo)
    //     assert res.success
    //     tag2.save(flush:true, failOnError:true)

    //     then:
    //     res.success == true
    //     res.status == ResultStatus.OK
    //     res.payload instanceof ContactTag
    //     res.payload.name == updateInfo.name
    //     res.payload.hexColor == updateInfo.hexColor
    //     ContactTag.count() == tBaseline
    // }

    // void "test updating with notification actions"() {
    //     given: "baselines"
    //     int tBaseline = ContactTag.count()

    //     when: "we try to delete nonexistent number"
    //     Map notifActions = [doNotificationActions:[[hello:"yes"]]]
    //     Result res = service.handleNotificationActions(tag1, notifActions)

    //     then:
    //     res.success == true
    //     res.status == ResultStatus.OK
    //     res.payload instanceof ContactTag
    //     res.payload.id == tag1.id
    //     ContactTag.count() == tBaseline
    // }

    // void "test tag actions invalid"() {
    //     when: "we try to update with tag actions that is not list"
    //     Map updateInfo = [doTagActions:"I am not a list"]
    //     Result res = service.doTagActions(tag1, updateInfo)

    //     then:
    //     res.success == false
    //     res.status == ResultStatus.UNPROCESSABLE_ENTITY
    //     res.errorMessages.size() == 1
    //     res.errorMessages.contains("emptyOrNotACollection")

    //     when: "we try to update tag action with nonexistent contact"
    //     updateInfo = [doTagActions:[
    //         [id:-88L, action:Constants.TAG_ACTION_ADD]
    //     ]]
    //     res = service.doTagActions(tag1, updateInfo)

    //     then:
    //     res.success == false
    //     res.status == ResultStatus.UNPROCESSABLE_ENTITY
    //     res.errorMessages.size() == 1
    //     res.errorMessages.contains("actionContainer.invalidActions")

    //     when: "we try to update tag action with forbidden contact"
    //     updateInfo = [doTagActions:[
    //         [id:tC1.id, action:Constants.TAG_ACTION_ADD]
    //     ]]
    //     res = service.doTagActions(tag1, updateInfo)

    //     then:
    //     res.success == false
    //     res.errorMessages[0] == "tagService.update.contactForbidden"
    //     res.status == ResultStatus.FORBIDDEN

    //     when: "we update with tag action with unspecified action"
    //     updateInfo = [doTagActions:[
    //         [id:c2.id, action:"invalid"]
    //     ]]
    //     res = service.doTagActions(tag2, updateInfo)

    //     then:
    //     res.success == false
    //     res.status == ResultStatus.UNPROCESSABLE_ENTITY
    //     res.errorMessages.size() == 1
    //     res.errorMessages.contains("actionContainer.invalidActions")
    // }

    // void "test tag actions valid"() {
    //     given: "no members in tag"
    //     tag2.members?.clear()
    //     tag2.save(flush:true, failOnError:true)

    //     when: "add contact to tag"
    //     Map updateInfo = [doTagActions:[
    //         [id:c2.id, action:Constants.TAG_ACTION_ADD]
    //     ]]
    //     Result res = service.doTagActions(tag2, updateInfo)
    //     assert res.success
    //     tag2.save(flush:true, failOnError:true)

    //     then:
    //     tag2.members.size() == 1
    //     tag2.members.contains(c2)
    //     res.status == ResultStatus.OK
    //     res.payload instanceof ContactTag
    //     res.payload.id == tag2.id

    //     when: "remove contact from tag"
    //     updateInfo = [doTagActions:[
    //         [id:c2.id, action:Constants.TAG_ACTION_REMOVE]
    //     ]]
    //     res = service.doTagActions(tag2, updateInfo)
    //     assert res.success
    //     tag2.save(flush:true, failOnError:true)

    //     then:
    //     tag2.members.isEmpty() == true
    //     tag2.members.contains(c2) == false
    //     res.status == ResultStatus.OK
    //     res.payload instanceof ContactTag
    //     res.payload.id == tag2.id
    // }

    // void "test update overall"() {
    //     when: "invalid update"
    //     Map updateInfo = [name:"Tag888", hexColor:"invalid"]
    //     Result res = service.update(tag2.id, updateInfo)

    //     then:
    //     res.success == false
    //     res.status == ResultStatus.UNPROCESSABLE_ENTITY
    //     res.errorMessages.size() == 1

    //     when: "valid update"
    //     updateInfo.hexColor = "#123"
    //     updateInfo.language = VoiceLanguage.JAPANESE.toString()
    //     res = service.update(tag2.id, updateInfo)
    //     res.payload.save(flush:true, failOnError:true)

    //     then:
    //     res.success == true
    //     res.status == ResultStatus.OK
    //     res.payload instanceof ContactTag
    //     res.payload.id == tag2.id
    //     res.payload.name == updateInfo.name
    //     res.payload.hexColor == updateInfo.hexColor
    //     res.payload.language == VoiceLanguage.JAPANESE
    // }

    // // Delete
    // // ------

    // void "test delete"() {
    //     given:
    //     service.futureMessageJobService = Stub(FutureMessageJobService) {
    //         cancelAll(*_) >> new ResultGroup()
    //     }

    // 	when: "we delete a nonexistent tag"
    //     Result res = service.delete(-88L)

    // 	then:
    //     res.success == false
    //     res.errorMessages[0] == "tagService.delete.notFound"
    //     res.status == ResultStatus.NOT_FOUND

    // 	when: "we delete an existing team tag"
    //     res = service.delete(teTag1.id)
    //     assert res.success
    //     t1.merge(flush:true, failOnError:true)

    // 	then:
    //     res.status == ResultStatus.NO_CONTENT
    //     teTag1.phone.tags.contains(teTag1) == false
    //     teTag1.members.every { !it.tags.contains(teTag1) }
    // }

    // void "test delete for tag with future messages"() {
    //     given:
    //     service.futureMessageJobService = Mock(FutureMessageJobService)

    //     FutureMessage fMsg1 = new FutureMessage(record:tag1.record,
    //         type:FutureMessageType.CALL, message:"hi")
    //     fMsg1.save(flush:true, failOnError:true)
    //     int numFutureMsgs = tag1.record.countFutureMessages()

    //     when: "deleting"
    //     Result<Void> res = service.delete(tag1.id)
    //     ContactTag.withSession { it.flush() }

    //     then: "tag marked as deleted and all future messages cancelled"
    //     1 * service.futureMessageJobService.cancelAll({ it.size() == numFutureMsgs }) >> new ResultGroup()
    //     res.status == ResultStatus.NO_CONTENT
    //     tag1.isDeleted == true
    // }
}
