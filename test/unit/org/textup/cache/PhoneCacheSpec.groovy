package org.textup.cache

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import grails.plugin.cache.GrailsValueWrapper
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.test.runtime.DirtiesRuntime
import org.springframework.cache.Cache
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class PhoneCacheSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    PhoneCache pCache

    def setup() {
        TestUtils.standardMockSetup()

        pCache = new PhoneCache()
        IOCUtils.metaClass."static".getPhoneCache = { -> pCache }
    }

    void "test try updating owner"() {
        given:
        Long ownerId = TestUtils.randIntegerUpTo(88)
        PhoneOwnershipType type = PhoneOwnershipType.values()[0]
        Long pId = TestUtils.randIntegerUpTo(88)

        expect: "simple pass-through for notifying cache of changes"
        pCache.updateOwner(null, null, null) == null
        pCache.updateOwner(ownerId, type, pId) == pId
    }

    void "test finding phone id of any status given owner"() {
        when:
        Phone p1 = TestUtils.buildStaffPhone()

        then:
        p1.isActive() == false

        when:
        Long pId = pCache.findAnyPhoneIdForOwner(null, null)

        then:
        pId == null

        when:
        pId = pCache.findAnyPhoneIdForOwner(p1.owner.ownerId, p1.owner.type)

        then:
        pId == p1.id
    }

    void "test must find phone id of any status given owner"() {
        when:
        Phone p1 = TestUtils.buildStaffPhone()

        then:
        p1.isActive() == false

        when:
        Result res = pCache.mustFindAnyPhoneIdForOwner(null, null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = pCache.mustFindAnyPhoneIdForOwner(p1.owner.ownerId, p1.owner.type)

        then:
        res.status == ResultStatus.OK
        res.payload == p1.id
    }

    void "test finding phone object given owner"() {
        when:
        Phone inactivePhone = TestUtils.buildStaffPhone()
        Phone activePhone = TestUtils.buildStaffPhone()
        activePhone.tryActivate(TestUtils.randPhoneNumber(), TestUtils.randString())

        then:
        inactivePhone.isActive() == false
        activePhone.isActive() == true

        expect:
        pCache.findPhone(inactivePhone.owner.id, inactivePhone.owner.type, false) == null
        pCache.findPhone(inactivePhone.owner.id, inactivePhone.owner.type, true) == inactivePhone

        and:
        pCache.findPhone(activePhone.owner.id, activePhone.owner.type, false) == activePhone
        pCache.findPhone(activePhone.owner.id, activePhone.owner.type, true) == activePhone
    }
}
