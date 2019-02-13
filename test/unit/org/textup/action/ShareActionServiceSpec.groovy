package org.textup.action

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import org.textup.validator.action.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
@TestFor(ShareActionService)
class ShareActionServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test has actions"() {
        expect:
        service.hasActions(doShareActions: null) == false
        service.hasActions(doShareActions: "hi")
    }

    void "test handling actions"() {
        given:
        String str1 = TestUtils.randString()
        SharePermission perm1 = SharePermission.VIEW

        Staff s1 = TestUtils.buildStaff()
        Phone tp1 = TestUtils.buildTeamPhone()
        Phone tp2 = TestUtils.buildTeamPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()

        ShareAction a1 = GroovyMock()
        ShareAction a2 = GroovyMock()
        MockedMethod tryProcess = MockedMethod.create(ActionContainer, "tryProcess") {
            Result.createSuccess([a1, a2])
        }
        MockedMethod tryStartShare = MockedMethod.create(service, "tryStartShare") {
            Result.createError([], ResultStatus.UNPROCESSABLE_ENTITY)
        }
        MockedMethod tryStopShare = MockedMethod.create(service, "tryStopShare") { Result.void() }
        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") {
            Result.createSuccess(s1)
        }
        MockedMethod tryRecordSharingChanges = MockedMethod.create(service, "tryRecordSharingChanges") { Result.void() }

        when:
        Result res = service.tryHandleActions(ipr1, [doShareActions: str1])

        then:
        a1.buildPhone() >> tp1
        a1.toString() >> ShareAction.MERGE
        a2.buildPhone() >> tp1
        tryProcess.latestArgs == [ShareAction, str1]
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        tryStartShare = MockedMethod.create(tryStartShare) { Result.void() }
        res = service.tryHandleActions(ipr1, [doShareActions: str1])

        then:
        a1.buildPhone() >> tp1
        a1.toString() >> ShareAction.MERGE
        a1.buildSharePermission() >> perm1
        tryStartShare.latestArgs == [ipr1, tp1, perm1]

        a2.buildPhone() >> tp2
        a2.toString() >> ShareAction.STOP
        tryStopShare.latestArgs == [ipr1, tp2]

        tryProcess.latestArgs == [ShareAction, str1]
        tryGetActiveAuthUser.hasBeenCalled
        tryRecordSharingChanges.latestArgs[0] == ipr1.record
        tryRecordSharingChanges.latestArgs[2] instanceof Map
        tryRecordSharingChanges.latestArgs[2].size() == 2
        tryRecordSharingChanges.latestArgs[2][perm1].size() == 1
        tryRecordSharingChanges.latestArgs[2][perm1][0] == tp1.buildName()
        tryRecordSharingChanges.latestArgs[2][SharePermission.NONE].size() == 1
        tryRecordSharingChanges.latestArgs[2][SharePermission.NONE][0] == tp2.buildName()
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        tryProcess?.restore()
        tryStartShare?.restore()
        tryStopShare?.restore()
        tryGetActiveAuthUser?.restore()
        tryRecordSharingChanges?.restore()
    }

    void "test recording sharing changes"() {
        given:
        Record rec1 = TestUtils.buildRecord()
        Author author1 = TestUtils.buildAuthor()
        String name1 = TestUtils.randString()
        String name2 = TestUtils.randString()
        String name3 = TestUtils.randString()
        Map permissionToNames = [
            (SharePermission.DELEGATE): [name1],
            (SharePermission.VIEW): [name2],
            (SharePermission.NONE): [name3]
        ]
        int noteBaseline = RecordNote.count()

        service.socketService = GroovyMock(SocketService)

        when:
        Result res = service.tryRecordSharingChanges(rec1, author1, permissionToNames)

        then:
        1 * service.socketService.sendItems(*_)
        res.status == ResultStatus.NO_CONTENT
        RecordNote.count() == noteBaseline + permissionToNames.size()

        and:
        RecordNote.findAllByRecord(rec1).each {
            assert it.author == author1
            assert it.isReadOnly == true
            assert it.noteContents != null
        }
    }

    void "test trying to stop share"() {
        given:
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()
        assert spr1.isActive()

        when:
        Result res = service.tryStopShare(spr1.shareSource, spr1.phone)

        then:
        res.status == ResultStatus.NO_CONTENT
        spr1.isActive() == false
    }

    void "test trying to start share"() {
        given:
        SharePermission perm1 = SharePermission.VIEW
        Phone shareWith = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(ipr1, shareWith)
        assert spr1.isActive()

        int prBaseline = PhoneRecord.count()

        MockedMethod canShare = MockedMethod.create(Phones, "canShare") { Result.void() }

        when:
        Result res = service.tryStartShare(ipr1, shareWith, perm1)

        then:
        canShare.hasBeenCalled
        res.status == ResultStatus.CREATED
        res.payload instanceof PhoneRecord
        res.payload != ipr1
        res.payload != spr1
        spr1.isActive() == false
        PhoneRecord.count() == prBaseline + 1

        cleanup:
        canShare?.restore()
    }
}
