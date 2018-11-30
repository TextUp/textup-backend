package org.textup

import org.textup.test.*
import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.type.OrgStatus
import org.textup.type.StaffStatus
import org.textup.type.SharePermission
import org.textup.util.*
import spock.lang.Shared
import spock.lang.Specification

@TestFor(AuthService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole,
    IncomingSession, FeaturedAnnouncement, AnnouncementReceipt, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class AuthServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
        logIn() //by default, we start out logged in
    }

    def cleanup() {
        cleanupData()
        logOut()
    }

    protected void logIn() {
        service.springSecurityService = [getPrincipal: {
            [username:loggedInUsername]
        }] as SpringSecurityService
    }
    protected void logOut() {
        service.springSecurityService = [getPrincipal: {
            [username:null]
        }] as SpringSecurityService
    }

    void "test logged in"() {
    	when: "we are logged out"
    	logOut()

    	then:
		service.isLoggedIn(s1.id) == false
		service.getLoggedIn() == null
        service.isLoggedInAndActive(s1.id) == false
        service.getLoggedInAndActive() == null

		when: "we log in as active"
        s1.org.status = OrgStatus.APPROVED
        s1.status = StaffStatus.STAFF
        s1.save(flush:true, failOnError:true)
		logIn()

		then:
		service.isLoggedIn(s1.id) == true
		service.getLoggedIn() == s1
        service.isLoggedInAndActive(s1.id) == true
        service.getLoggedInAndActive() == s1

        when: "we are inactive"
        s1.status = StaffStatus.PENDING
        s1.save(flush:true, failOnError:true)

        then:
        service.isLoggedIn(s1.id) == true
        service.getLoggedIn() == s1
        service.isLoggedInAndActive(s1.id) == false
        service.getLoggedInAndActive() == null
    }

    void "test admin status"() {
    	given: "that we are admin at our org"
        s1.org.status = OrgStatus.APPROVED
    	s1.status = StaffStatus.ADMIN
    	s1.save(flush:true, failOnError:true)

    	expect: "we have an org we are not an admin at"
    	service.isAdminAt(org2.id) == false

    	and: "we have an org we are the admin at"
    	service.isAdminAt(org.id) == true

    	and: "we have a staff at our org"
    	service.isAdminAtSameOrgAs(s2.id) == true

    	and: "we have a staff at a different org"
    	service.isAdminAtSameOrgAs(otherS2.id) == false

    	and: "we have a team we are not a part of at an org we are admin at"
		service.isAdminForTeam(t2.id) == true

    	and: "we have a team at a different org"
		service.isAdminForTeam(otherT2.id) == false
    }

    void "test permissions for contacts"() {
        given: "we are active"
        s1.org.status = OrgStatus.APPROVED
        s1.status = StaffStatus.STAFF
        s1.save(flush:true, failOnError:true)

        Contact deletedContact = s1.phone.createContact([isDeleted:true], [TestUtils.randPhoneNumber()]).payload
        deletedContact.save(flush:true, failOnError:true)

        expect: "This is your contact"
        service.hasPermissionsForContact(c1.id) == true

        and: "This is your contact but it is deleted"
        service.hasPermissionsForContact(deletedContact.id) == false

        and: "This is another staff member at the same org's contacts"
        service.hasPermissionsForContact(c2.id) == false

        and: "This is another staff member at a different org's contacts"
        service.hasPermissionsForContact(otherC2.id) == false

        and: "This contact belongs to one of the teams you are on"
        service.hasPermissionsForContact(tC1.id) == true

        and: "This contact belongs to one of the teams you are NOT on"
        service.hasPermissionsForContact(tC2.id) == false
    }

    void "test permissions for sharing"() {
        given: "we are active"
        s1.org.status = OrgStatus.APPROVED
        s1.status = StaffStatus.STAFF
        s1.save(flush:true, failOnError:true)

        when: "Get shared contact id for a contact that is shared with us"
        Long scId = service.getSharedContactIdForContact(c2.id)

        then:
        scId == sc2.id

        when: "Get shared contact id for a contact that is NOT shared with us"
        scId = service.getSharedContactIdForContact(tC1.id)

        then:
        scId == null

        when: "we stop sharing the shared contact"
        p2.stopShare(p1)
        p2.save(flush:true, failOnError:true)
        scId = service.getSharedContactIdForContact(c2.id)

        then:
        scId == null

        when: "shared contact belongs to a team that we are NOT on"
        assert (otherT1 in s1.teams) == false
        Phone teamPhone = otherT1.phone
        Contact contactToBeShared = p2.contacts[0]
        SharedContact teamSharedContact = p2.share(contactToBeShared,
            teamPhone, SharePermission.DELEGATE).payload
        p2.merge(flush:true)
        scId = service.getSharedContactIdForContact(contactToBeShared.id)

        then:
        scId == null

        when: "shared contact belongs to a team that we are on"
        teamPhone = s1.teams[0].phone
        teamSharedContact = p2.share(contactToBeShared,
            teamPhone, SharePermission.DELEGATE).payload
        teamSharedContact.save(flush:true, failOnError:true)
        scId = service.getSharedContactIdForContact(contactToBeShared.id)

        then:
        scId == teamSharedContact.id

        when: "a contact previously shared with us is deleted"
        contactToBeShared.isDeleted = true
        contactToBeShared.merge(flush:true)

        scId = service.getSharedContactIdForContact(contactToBeShared.id)

        then: "no longer have sharing permissions"
        scId == null
    }

    void "test permissions for team as admin"() {
    	given: "that we are admin at our org"
        s1.org.status = OrgStatus.APPROVED
    	s1.status = StaffStatus.ADMIN
    	s1.save(flush:true, failOnError:true)

    	expect: "You are on this team"
    	service.hasPermissionsForTeam(t1.id) == true

    	and: "You are an admin at this team's organization"
    	service.hasPermissionsForTeam(t2.id) == true

    	and: "You have team from another organization"
    	service.hasPermissionsForTeam(otherT2.id) == false

    	and: "You have an invalid id"
    	service.hasPermissionsForTeam(-88L) == false
    }

    void "test permissions for team as staff"() {
        given: "that we are staff at our org"
        s1.org.status = OrgStatus.APPROVED
        s1.status = StaffStatus.STAFF
        s1.save(flush:true, failOnError:true)

        expect: "You are on this team"
        service.hasPermissionsForTeam(t1.id) == true

        and: "You are not an admin at this team's organization"
        service.hasPermissionsForTeam(t2.id) == false

        and: "You have team from another organization"
        service.hasPermissionsForTeam(otherT2.id) == false

        and: "You have an invalid id"
        service.hasPermissionsForTeam(-88L) == false
    }

    void "test permissions for staff as admin"() {
        given: "that we are admin at our org"
        s1.org.status = OrgStatus.APPROVED
        s1.status = StaffStatus.ADMIN
        s1.save(flush:true, failOnError:true)

        expect: "You are this staff member"
        service.hasPermissionsForStaff(s1.id) == true

        and: "You are an admin at this staff member's organization"
        service.hasPermissionsForStaff(s3.id) == true

        and: "You are on a same team as this staff member"
        service.hasPermissionsForStaff(s2.id) == true

        and: "You have a staff member at another organization"
        service.hasPermissionsForStaff(otherS2.id) == false

        and: "You pass in an invalid id"
        service.hasPermissionsForStaff(-88L) == false
    }

    void "test permissions for staff as staff"() {
        given: "that we are staff at our org"
        s1.org.status = OrgStatus.APPROVED
        s1.status = StaffStatus.STAFF
        s1.save(flush:true, failOnError:true)

        expect: "You are this staff member"
        service.hasPermissionsForStaff(s1.id) == true

        and: "You are not an admin or on same team"
        service.hasPermissionsForStaff(s3.id) == false

        and: "You are on a same team as this staff member"
        service.hasPermissionsForStaff(s2.id) == true

        and: "You have a staff member at another organization"
        service.hasPermissionsForStaff(otherS2.id) == false

        and: "You pass in an invalid id"
        service.hasPermissionsForStaff(-88L) == false
    }

    void "test permissions for tags"() {
        given: "we are active"
        s1.org.status = OrgStatus.APPROVED
        s1.status = StaffStatus.STAFF
        s1.save(flush:true, failOnError:true)

        ContactTag deletedTag = s1.phone.createTag([name:TestUtils.randPhoneNumber(), isDeleted:true]).payload
        deletedTag.save(flush:true, failOnError:true)

    	expect: "This tag belongs to you"
    	service.hasPermissionsForTag(tag1.id) == true

        and: "This tag belongs to you but it is deleted"
        service.hasPermissionsForTag(deletedTag.id) == false

    	and: "This tag belongs to a team you are on"
    	service.hasPermissionsForTag(teTag1.id) == true

    	and: "This is another staff member at the same org's contacts"
    	service.hasPermissionsForTag(tag2.id) == false

    	and: "This is another staff member at a different org's contacts"
    	service.hasPermissionsForTag(otherTag2.id) == false

    	and: "This contact belongs to one of the teams you are NOT on"
    	service.hasPermissionsForTag(teTag2.id) == false
    }

    void "test permissions for record"() {
        given: "we are active"
        s1.org.status = OrgStatus.APPROVED
        s1.status = StaffStatus.STAFF
        s1.save(flush:true, failOnError:true)

    	expect: "This item belongs to one of your contacts"
    	service.hasPermissionsForRecord(rText1.record) == true

    	and: "This item belongs to a contact that is currently shared with you"
    	service.hasPermissionsForRecord(rText2.record) == true

    	and: "This item belongs to a contact of one of the teams you're on"
    	service.hasPermissionsForRecord(rTeText1.record) == true

    	and: "This item belongs to a different team at same org"
    	service.hasPermissionsForRecord(rTeText2.record) == false

    	and: "This item belongs to a staff member at a different org"
    	service.hasPermissionsForRecord(otherRText2.record) == false

    	and: "This item belongs to a team at a different org"
    	service.hasPermissionsForRecord(otherRTeText2.record) == false
    }

    void "test permissions for session"() {
        given: "we are active"
        s1.org.status = OrgStatus.APPROVED
        s1.status = StaffStatus.STAFF
        s1.save(flush:true, failOnError:true)

        when: "This tag belongs to you"
        String num = "1233920399"
        IncomingSession sess1 = new IncomingSession(phone:p1, numberAsString:num)
        sess1.save(flush:true, failOnError:true)

        then:
        service.hasPermissionsForSession(sess1.id) == true

        when: "This tag belongs to a team you are on"
        sess1 = new IncomingSession(phone:tPh1, numberAsString:num)
        sess1.save(flush:true, failOnError:true)

        then:
        service.hasPermissionsForSession(sess1.id) == true

        when: "This is another staff member at the same org"
        sess1 = new IncomingSession(phone:p2, numberAsString:num)
        sess1.save(flush:true, failOnError:true)

        then:
        service.hasPermissionsForSession(sess1.id) == false

        when: "This is another staff member at a different org"
        sess1 = new IncomingSession(phone:otherP1, numberAsString:num)
        sess1.save(flush:true, failOnError:true)

        then:
        service.hasPermissionsForSession(sess1.id) == false

        when: "This contact belongs to one of the teams you are NOT on"
        sess1 = new IncomingSession(phone:tPh2, numberAsString:num)
        sess1.save(flush:true, failOnError:true)

        then:
        service.hasPermissionsForSession(sess1.id) == false
    }

    void "test permissions for announcement"() {
        given: "we are active"
        s1.org.status = OrgStatus.APPROVED
        s1.status = StaffStatus.STAFF
        s1.save(flush:true, failOnError:true)

        when: "This tag belongs to you"
        String msg = "hi!"
        DateTime expires = DateTime.now().plusMinutes(30)
        FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
            message:msg, expiresAt:expires)
        announce.save(flush:true, failOnError:true)

        then:
        service.hasPermissionsForAnnouncement(announce.id) == true

        when: "This tag belongs to a team you are on"
        announce = new FeaturedAnnouncement(owner:tPh1,
            message:msg, expiresAt:expires)
        announce.save(flush:true, failOnError:true)

        then:
        service.hasPermissionsForAnnouncement(announce.id) == true

        when: "This is another staff member at the same org"
        announce = new FeaturedAnnouncement(owner:p2,
            message:msg, expiresAt:expires)
        announce.save(flush:true, failOnError:true)

        then:
        service.hasPermissionsForAnnouncement(announce.id) == false

        when: "This is another staff member at a different org"
        announce = new FeaturedAnnouncement(owner:otherP1,
            message:msg, expiresAt:expires)
        announce.save(flush:true, failOnError:true)

        then:
        service.hasPermissionsForAnnouncement(announce.id) == false

        when: "This contact belongs to one of the teams you are NOT on"
        announce = new FeaturedAnnouncement(owner:tPh2,
            message:msg, expiresAt:expires)
        announce.save(flush:true, failOnError:true)

        then:
        service.hasPermissionsForAnnouncement(announce.id) == false
    }
}
