package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.mixin.web.ControllerUnitTestMixin
import grails.validation.ValidationErrors
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
    Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
    AnnouncementReceipt, Role, StaffRole, FutureMessage, SimpleFutureMessage, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion, Token])
@TestMixin([HibernateTestMixin, ControllerUnitTestMixin])
@TestFor(NotificationService)
class NotificationServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
        service.resultFactory = TestUtils.getResultFactory(grailsApplication)
    }

    def cleanup() {
        cleanupData()
    }

    // Notification actions
    // --------------------

    void "test notification action errors"() {
        given: "baselines"
        int pBaseline = NotificationPolicy.count()

        when: "actions not a collection"
        Long recId = 8L
        Object data = "I am  not a collection"
        Result<Void> res = service.handleNotificationActions(p1, recId, data)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.any { it.contains("emptyOrNotACollection") }
        pBaseline == NotificationPolicy.count()

        when: "individual actions not maps"
        data = ["I am not a map"]
        res = service.handleNotificationActions(p1, recId, data)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.any { it.contains("emptyOrNotAMap") }
        pBaseline == NotificationPolicy.count()

        when: "properly formatted but invalid contents"
        data = [[test:"I am nonexistent"]]
        res = service.handleNotificationActions(p1, recId, data)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.any { it.contains("actionContainer.invalidActions") }
        pBaseline == NotificationPolicy.count()
    }

    void "test valid notification actions"() {
        given: "baselines"
        int pBaseline = NotificationPolicy.count()

        when: "valid for changing default"
        int recId = 8L
        Object data = [[
            action:Constants.NOTIFICATION_ACTION_DEFAULT,
            id:s1.id,
            level:NotificationLevel.ALL
        ]]
        Result<Void> res = service.handleNotificationActions(p1, recId, data)
        NotificationPolicy np1 = NotificationPolicy.listOrderById(order:"desc", max: 1)[0]

        then:
        res.success == true
        res.status == ResultStatus.NO_CONTENT
        res.payload == null
        pBaseline + 1 == NotificationPolicy.count()
        np1.staffId == s1.id
        np1.level == NotificationLevel.ALL
        np1.isInWhitelist(recId) == false
        np1.isInBlacklist(recId) == false

        when: "valid for enabling"
        data = [[
            action:Constants.NOTIFICATION_ACTION_ENABLE,
            id:s1.id
        ]]
        res = service.handleNotificationActions(p1, recId, data)
        np1 = NotificationPolicy.get(np1.id)

        then:
        res.success == true
        res.status == ResultStatus.NO_CONTENT
        res.payload == null
        pBaseline + 1 == NotificationPolicy.count()
        np1.staffId == s1.id
        np1.level == NotificationLevel.ALL
        np1.isInWhitelist(recId) == true
        np1.isInBlacklist(recId) == false

        when: "valid for disabling"
        data = [[
            action:Constants.NOTIFICATION_ACTION_DISABLE,
            id:s1.id
        ]]
        res = service.handleNotificationActions(p1, recId, data)

        then:
        res.success == true
        res.status == ResultStatus.NO_CONTENT
        res.payload == null
        pBaseline + 1 == NotificationPolicy.count()
        np1.staffId == s1.id
        np1.level == NotificationLevel.ALL
        np1.isInWhitelist(recId) == false
        np1.isInBlacklist(recId) == true
    }

    // Updating
    // --------

    void "test updating schedule at policy level"() {
        given: "currently logged-in is a staff without policy for this phone"
        String utcTimezone = "Etc/UTC"
        Staff staff1 = new Staff(username: UUID.randomUUID().toString(),
            name: "Name",
            password: "password",
            email: "hello@its.me",
            org: org)
        staff1.save(flush:true, failOnError:true)
        NotificationPolicy np1 = p1.owner.getOrCreatePolicyForStaff(staff1.id)
        np1.save(flush:true, failOnError:true)
        int sBaseline = Schedule.count()

        when: "update with valid non-schedule info"
        Result<NotificationPolicy> res = service.update(np1, [
            useStaffAvailability: false,
            manualSchedule: false,
            isAvailable: false
        ], utcTimezone)

        then: "no schedule created"
        res.success == true
        res.payload instanceof NotificationPolicy
        res.payload.useStaffAvailability == false
        res.payload.manualSchedule == false
        res.payload.isAvailable == false
        Schedule.count() == sBaseline

        when: "update with some invalid non-schedule info"
        res = service.update(np1, [
            useStaffAvailability: "invalid, not a boolean",
            manualSchedule: "invalid, not a boolean",
            isAvailable: true,
        ], utcTimezone)

        then: "invalid values ignored, valid values updated"
        res.success == true
        res.payload instanceof NotificationPolicy
        res.payload.useStaffAvailability == false
        res.payload.manualSchedule == false
        res.payload.isAvailable == true
        Schedule.count() == sBaseline

        when: "handling schedule with valid schedule-related updates"
        String mondayString = "0000:1230"
        res = service.update(np1, [schedule:[monday:[mondayString]]], utcTimezone)

        then: "a new schedule is created and all updates are made"
        res.success == true
        res.payload instanceof NotificationPolicy
        Schedule.count() == sBaseline + 1
        res.payload.useStaffAvailability == false
        res.payload.manualSchedule == false
        res.payload.isAvailable == true
        res.payload.schedule.monday == mondayString.replaceAll(":", ",") // different delimiter when saved

        when: "handling schedule with INVALID schedule-related updates"
        res = service.update(np1, [schedule:[monday:"invalid time range"]], utcTimezone)

        then: "error and no new schedule is created"
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "weeklySchedule.strIntsNotList"
        Schedule.count() == sBaseline + 1
    }

    // Collating notification recipients
    // ---------------------------------

    void "test notify staff"() {
        given: "a staff member with a personal phone who is a member of a team \
            and who has a contact shared by the team to the staff's personal phone"
        // asserting relationships
        assert s1 in t1.members
        assert s1.phone == p1 && c1.phone == p1
        assert t1.phone == tPh1 && tC1.phone == tPh1
        // sharing contact
        tPh1.stopShare(tC1)
        SharedContact shared1 = tPh1.share(tC1, p1, SharePermission.DELEGATE).payload
        shared1.save(flush:true, failOnError:true)
        // make all other staff members unavailable!
        t1.members.each { Staff otherStaff ->
            otherStaff.manualSchedule = true
            otherStaff.isAvailable = false
            otherStaff.save(flush:true, failOnError:true)
        }
        // ensuring that staff is available
        s1.manualSchedule = true
        s1.isAvailable = true
        s1.save(flush:true, failOnError:true)
        assert s1.isAvailableNow() == true

        when: "incoming text to the team's phone number"
        List<BasicNotification> bnList = service.build(tPh1, [tC1])

        then: "staff should only receive notification from the staff's personal \
            TextUp phone through the shared relationship"
        bnList.size() == 1 // because s1 is the only staff member available
        bnList[0].record.id == tC1.record.id
        bnList[0].owner.id == s1.phone.owner.id
        bnList[0].staff?.id == s1.id

        when: "staff is no longer shared on contact and incoming text to team number"
        shared1.stopSharing()
        shared1.save(flush:true, failOnError:true)

        bnList = service.build(tPh1, [tC1])

        then: "notification from the team TextUp phone only"
        bnList.size() == 1 // because s1 is the only staff member available
        bnList[0].record.id == tC1.record.id
        bnList[0].owner.id == tC1.phone.owner.id

        when: "staff is shared again but is no longer member of team and \
            incoming text to the team number"
        SharedContact shared2 = tPh1.share(tC1, p1, SharePermission.DELEGATE).payload
        shared2.save(flush:true, failOnError:true)

        t1.removeFromMembers(s1)
        t1.save(flush:true, failOnError:true)
        assert (s1 in t1.members) == false

        bnList = service.build(tPh1, [tC1])

        then: "notification from the staff's personal TextUp phone only because \
            of the shared relationship"
        bnList.size() == 1 // because s1 is the only staff member available
        bnList[0].record.id == tC1.record.id
        bnList[0].owner.id == s1.phone.owner.id
    }

    void "test getting phones to available now for contact ids"() {
        given: "phone with one unshared contact"
        Phone phone1 = new Phone(numberAsString:"3921920392")
        phone1.updateOwner(t1)
        phone1.save(flush:true, failOnError:true)
        Contact contact1 = phone1.createContact([:], ["12223334447"]).payload
        ContactTag tag1 = phone1.createTag([name:"Tag 1"]).payload
        phone1.save(flush:true, failOnError:true)

        Map<Long, Record> phoneIdToRecord = [:]
        Map<Long, Long> staffIdToPersonalPhoneId = [:]
        Map<Phone, List<Staff>> phonesToCanNotify = [:]

        when: "none available"
        t1.activeMembers.each {
            it.status = StaffStatus.STAFF
            it.manualSchedule = true
            it.isAvailable = false
        }
        t1.save(flush:true, failOnError:true)

        service.populateData(phoneIdToRecord, staffIdToPersonalPhoneId, phonesToCanNotify,
            phone1, [contact1])

        then: "map should be empty, should not have any entries"
        phonesToCanNotify.isEmpty() == true

        when: "has available, no contacts shared, passing in a contact"
        t1.activeMembers.each { it.isAvailable = true }
        t1.save(flush:true, failOnError:true)

        phoneIdToRecord.clear()
        staffIdToPersonalPhoneId.clear()
        phonesToCanNotify.clear()
        service.populateData(phoneIdToRecord, staffIdToPersonalPhoneId, phonesToCanNotify,
            phone1, [contact1])

        then: "only this phone to list of available now"
        phonesToCanNotify.size() == 1
        phonesToCanNotify[phone1] instanceof List
        t1.activeMembers.every { it in phonesToCanNotify[phone1] }

        when: "has available, no contacts shared, passing in both contact and tag"
        t1.activeMembers.each { it.isAvailable = true }
        t1.save(flush:true, failOnError:true)

        phoneIdToRecord.clear()
        staffIdToPersonalPhoneId.clear()
        phonesToCanNotify.clear()
        service.populateData(phoneIdToRecord, staffIdToPersonalPhoneId, phonesToCanNotify,
            phone1, [contact1], [tag1])

        then: "only this phone to list of available now, tag's record takes precedence over contact's record"
        phonesToCanNotify.size() == 1
        phonesToCanNotify[phone1] instanceof List
        phoneIdToRecord[phone1.id] != contact1.record
        phoneIdToRecord[phone1.id] == tag1.record
        t1.activeMembers.every { it in phonesToCanNotify[phone1] }

        when: "has available, has contacts shared"
        assert s1 in t1.activeMembers
        assert s1.phone
        Result<SharedContact> res = phone1.share(contact1, s1.phone, SharePermission.DELEGATE)
        assert res.success

        phoneIdToRecord.clear()
        staffIdToPersonalPhoneId.clear()
        phonesToCanNotify.clear()
        service.populateData(phoneIdToRecord, staffIdToPersonalPhoneId, phonesToCanNotify,
            phone1, [contact1])

        then: "this phone and other sharedWith phones too"
        phonesToCanNotify.size() == 2
        phonesToCanNotify[phone1] instanceof List
        t1.activeMembers.every { it in phonesToCanNotify[phone1] }
        phonesToCanNotify[s1.phone] instanceof List
        phonesToCanNotify[s1.phone].size() == 1
        phonesToCanNotify[s1.phone] == [s1]
    }

    // Sending notifications
    // ---------------------

    void "test building instructions for notification"() {
        given:
        String ownerName = TestUtils.randString()
        String initials = TestUtils.randString()
        PhoneOwnership owner1 = GroovyStub() { buildName() >> ownerName }
        BasicNotification bn1 = GroovyMock()
        Notification notif1 = GroovyMock()

        when: "is notification and is outgoing"
        String instr = service.buildInstructions(notif1, true)

        then:
        1 * notif1.owner >> owner1
        1 * notif1.otherInitials >> initials
        instr == "notificationService.outgoing.withFrom"

        when: "is notification and NOT outgoing"
        instr = service.buildInstructions(notif1, false)

        then:
        1 * notif1.owner >> owner1
        1 * notif1.otherInitials >> initials
        instr == "notificationService.incoming.withFrom"

        when: "is BASIC notification and is outgoing"
        instr = service.buildInstructions(bn1, true)

        then:
        1 * bn1.owner >> owner1
        instr == "notificationService.outgoing.noFrom"

        when: "is BASIC notification and NOT outgoing"
        instr = service.buildInstructions(bn1, false)

        then:
        1 * bn1.owner >> owner1
        instr == "notificationService.incoming.noFrom"
    }

    void "test sending for basic notification (no from)"() {
        given:
        service.tokenService = Mock(TokenService)
        service.textService = Mock(TextService)
        service.grailsApplication = grailsApplication

        String contents = TestUtils.randString()
        BasicNotification bn1 = [
            owner: [phone: p1],
            staff: s1,
            record: c1.record
        ] as BasicNotification
        PhoneNumber personalNum = new PhoneNumber(number: TestUtils.randPhoneNumber())

        when: "no personal phone"
        bn1.staff.personalPhoneAsString = null
        Result<Void> res = service.sendForNotification(bn1, false, contents)

        then:
        0 * service.tokenService._
        0 * service.textService._
        res.status == ResultStatus.NO_CONTENT

        when:
        bn1.staff.personalPhoneNumber = personalNum
        res = service.sendForNotification(bn1, false, contents)

        then:
        1 * service.tokenService.generateNotification(*_) >> new Result(payload: [token: "hi"] as Token)
        1 * service.textService.send(_, _, {
                it.contains("notificationService.send.notificationSuffix") &&
                    it.contains("notificationService.incoming.noFrom") &&
                    it.contains(grailsApplication.config.textup.links.notifyMessage)
            }) >> new Result()
        res.success == true
    }

    void "test sending for notification (includes from initials)"() {
        given:
        service.tokenService = Mock(TokenService)
        service.textService = Mock(TextService)
        service.grailsApplication = grailsApplication

        String contents = TestUtils.randString()
        Notification notif = new Notification(owner: p1.owner, record: c1.record, contents: "hi", staff: s1)
        s1.personalPhoneAsString = TestUtils.randPhoneNumber()

        when:
        Result<Void> res = service.sendForNotification(notif, false, contents)

        then:
        1 * service.tokenService.generateNotification(*_) >> new Result(payload: [token: "hi"] as Token)
        1 * service.textService.send(_, _, {
                it.contains("notificationService.send.notificationSuffix") &&
                    it.contains("notificationService.incoming.withFrom") &&
                    it.contains(grailsApplication.config.textup.links.notifyMessage)
            }) >> new Result()
        res.success == true
    }

    void "test sending overall"() {
        given:
        service.tokenService = Mock(TokenService)
        service.textService = Mock(TextService)
        service.grailsApplication = grailsApplication

        String contents = TestUtils.randString()
        BasicNotification bn1 = [
            owner: Stub(PhoneOwnership) { getPhone() >> p1 },
            staff: s1,
            record: c1.record
        ] as BasicNotification
        List<BasicNotification> notifs = []
        10.times { notifs << bn1 }

        when:
        ResultGroup<Void> resGroup = service.send(notifs, true, contents)

        then:
        notifs.size() * service.tokenService.generateNotification(*_) >>
            new Result(payload: [token: "hi"] as Token)
        notifs.size() * service.textService.send(*_) >> new Result()
        resGroup.successes.size() == notifs.size()
    }

    // Showing notifications
    // ---------------------

    void "test showing notification"() {
        given:
        service.tokenService = Mock(TokenService)
        String invalidTokenString = TestUtils.randString()
        Token tok1 = new Token(type: TokenType.NOTIFY_STAFF)
        tok1.data = [
            contents: "hi",
            phoneId: p1.id,
            outgoing: true,
            recordId: c1.record.id
        ]
        tok1.save(flush: true, failOnError: true)

        when: "data stored in token is invalid"
        Result<Notification> res = service.show(invalidTokenString)

        then:
        1 * service.tokenService.findNotification(*_) >>
            new Result(payload: [data: [:]] as Token)
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when: "token has valid data"
        res = service.show(tok1.token)

        then:
        1 * service.tokenService.findNotification(*_) >> new Result(payload: tok1)
        res.status == ResultStatus.OK
        res.payload instanceof Notification
    }
}
