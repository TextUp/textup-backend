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

@TestFor(AuthService)
@Domain([TagMembership, Contact, Phone, ContactTag, 
	ContactNumber, Record, RecordItem, RecordNote, RecordText, 
	RecordCall, RecordItemReceipt, PhoneNumber, SharedContact, 
	TeamMembership, StaffPhone, Staff, Team, Organization, 
	Schedule, Location, TeamPhone, WeeklySchedule, TeamContactTag])
@TestMixin(HibernateTestMixin)
class AuthServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }
    def setup() {
        super.setupData()
        //by default, we start out logged in 
        logIn()
    }
    def cleanup() { 
        super.cleanupData()
        logIn()
    }
    
    protected void logIn() {
        service.springSecurityService = [principal:[username:loggedInUsername]]
    }
    protected void logOut() {
        service.springSecurityService = [principal:[username:null]]
    }

    void "test logged in"() {
    	when: "we are logged out"
    	logOut()
    	
    	then: 
    	service.getLoggedInId() == null
		service.isLoggedIn(s1.id) == false 
		service.getLoggedIn() == null

		when: "we log in"
		logIn()

		then:
		service.getLoggedInId() == s1.id 
		service.isLoggedIn(s1.id) == true 
		service.getLoggedIn() == s1
    }

    void "test admin status"() {
    	given: "that we are admin at our org"
    	s1.status = Constants.STATUS_ADMIN
    	s1.save(flush:true, failOnError:true)

    	when: "we have an org we are not an admin at"
    	boolean isAdmin = service.isAdminAt(org2.id)

    	then: 
    	isAdmin == false

    	when: "we have an org we are the admin at"
    	isAdmin = service.isAdminAt(org.id)

    	then: 
    	isAdmin == true 

    	when: "we have a staff at our org"
    	isAdmin = service.isAdminAtSameOrgAs(s2.id)

    	then: 
    	isAdmin == true 

    	when: "we have a staff at a different org"
    	isAdmin = service.isAdminAtSameOrgAs(otherS2.id)

    	then: 
    	isAdmin == false 

    	when: "we have a team we are not a part of at an org we are admin at"
		isAdmin = service.isAdminForTeam(t2.id)
    	
    	then: 
    	isAdmin == true 

    	when: "we have a team at a different org"
		isAdmin = service.isAdminForTeam(otherT2.id)

    	then: 
    	isAdmin == false 
    }

    void "test permissions for staff"() {
    	given: "that we are admin at our org"
    	s1.status = Constants.STATUS_ADMIN
    	s1.save(flush:true, failOnError:true)

    	when: "You are this staff member"
    	boolean hasPermissions = service.hasPermissionsForStaff(s1.id)

    	then:
    	hasPermissions == true

    	when: "You are an admin at this staff member's organization"
    	hasPermissions = service.hasPermissionsForStaff(s3.id)

    	then:
    	hasPermissions == true

    	when: "You are on a same team as this staff member"
    	hasPermissions = service.hasPermissionsForStaff(s2.id)

    	then:
    	hasPermissions == true

    	when: "You have a staff member at another organization"
    	hasPermissions = service.hasPermissionsForStaff(otherS2.id)

    	then: 
    	hasPermissions == false

    	when: "You pass in an invalid id"
    	hasPermissions = service.hasPermissionsForStaff(-88L)

    	then: 
    	hasPermissions == false
    }

    void "test permissions for team"() {
    	given: "that we are admin at our org"
    	s1.status = Constants.STATUS_ADMIN
    	s1.save(flush:true, failOnError:true)

    	when: "You are on this team "
    	boolean hasPermissions = service.hasPermissionsForTeam(t1.id)

    	then:
    	service.belongsToSameTeamAs(t1.id) == true
    	hasPermissions == true 

    	when: "You are an admin at this team's organization"
    	hasPermissions = service.hasPermissionsForTeam(t2.id)

    	then:
    	service.belongsToSameTeamAs(t2.id) == false
    	hasPermissions == true 

    	when: "You have team from another organization"
    	hasPermissions = service.hasPermissionsForTeam(otherT2.id)

    	then: 
    	service.belongsToSameTeamAs(otherT2.id) == false
    	hasPermissions == false

    	when: "You have an invalid id"
    	hasPermissions = service.hasPermissionsForTeam(-88L)

    	then: 
    	service.belongsToSameTeamAs(-88L) == false
    	hasPermissions == false
    }

    void "test permissions for contacts"() {
    	when: "This is your contact"
    	boolean hasPermissions = service.hasPermissionsForContact(c1.id)

    	then:
    	hasPermissions == true

    	when: "This is another staff member at the same org's contacts"
    	hasPermissions = service.hasPermissionsForContact(c2.id)

    	then:
    	hasPermissions == false

    	when: "This is another staff member at a different org's contacts"
    	hasPermissions = service.hasPermissionsForContact(otherC2.id)

    	then:
    	hasPermissions == false

    	when: "This contact belongs to one of the teams you are on"
    	hasPermissions = service.hasPermissionsForContact(tC1.id)

    	then:
    	service.belongsToSameTeamAs(t1.id) == true
    	hasPermissions == true

    	when: "This contact belongs to one of the teams you are NOT on"
    	hasPermissions = service.hasPermissionsForContact(tC2.id)

    	then:
    	service.belongsToSameTeamAs(t2.id) == false
    	hasPermissions == false

    	when: "distinguishing between permitted and forbidden contacts"
    	ParsedResult<Long,Long> parsed = service.parseContactIdsByPermission([c1, c2, tC1, tC2, otherC2, otherTC2]*.id)
    	List<Long> validIds = [c1, tC1]*.id, invalidIds = [c2, tC2, otherC2, otherTC2]*.id
    	
    	then:
    	parsed.valid.every { it in validIds }
    	parsed.invalid.every { it in invalidIds }
    }

    void "test permissions for sharing"() {
    	when: "Get shared contact id for a contact that is shared with us"
    	Long scId = service.getSharedContactForContact(c2.id)

    	then:
        service.canShareContactWithStaff(c1.id, s2.id) == true
        service.canShareContactWithStaff(c2.id, s1.id) == true
    	scId == sc2.id

    	when: "distinguishing between contacts that are shared from forbidden contacts"
    	ParsedResult<SharedContact,Long> parsed = service.parseIntoSharedContactsByPermission([c1, c2, tC1]*.id)

    	then:
    	parsed.valid.every { it in [sc2] } //valid is a list of SharedContact instances
    	parsed.invalid.every { it in [c1, tC1]*.id } //invalid is a list of contact ids

    	when: "Get shared contact id for a contact that is NOT shared with us"
    	scId = service.getSharedContactForContact(tC1.id)

    	then:
    	scId == null

    	when: "we stop sharing the shared contact"
    	p2.stopSharingWith(p1)
    	p2.save(flush:true, failOnError:true)
    	scId = service.getSharedContactForContact(c2.id)

    	then:
    	scId == null

    	when: "distinguishing between contacts that are shared from forbidden contacts"
    	parsed = service.parseIntoSharedContactsByPermission([c1, c2, tC1]*.id)

    	then:
    	parsed.valid.isEmpty()
    	parsed.invalid.every { it in [c1, c2, tC1]*.id } //invalid is a list of contact ids
    }

    void "test permissions for tags"() {
    	when: "This tag belongs to you"
    	boolean hasPermissions = service.hasPermissionsForTag(tag1.id)

    	then:
    	hasPermissions == true

    	when: "This tag belongs to a team you are on"
    	hasPermissions = service.hasPermissionsForTag(teTag1.id)

    	then:
    	hasPermissions == true

    	when: "This is another staff member at the same org's contacts"
    	hasPermissions = service.hasPermissionsForTag(tag2.id)

    	then:
    	hasPermissions == false

    	when: "This is another staff member at a different org's contacts"
    	hasPermissions = service.hasPermissionsForTag(otherTag2.id)

    	then:
    	hasPermissions == false

    	when: "This contact belongs to one of the teams you are NOT on"
    	hasPermissions = service.hasPermissionsForTag(teTag2.id)

    	then:
    	hasPermissions == false

    	when: "distinguishing between permitted and forbidden tags"
    	ParsedResult<Long,Long> parsed = service.parseTagIdsByPermission([tag1, tag2, teTag1, teTag2, otherTag2, otherTeTag2]*.id)
    	List<Long> validIds = [tag1, teTag1]*.id, 
    		invalidIds = [tag2, teTag2, otherTag2, otherTeTag2]*.id
    	
    	then:
    	parsed.valid.every { it in validIds }
    	parsed.invalid.every { it in invalidIds }
    }

    void "test same contact and tag belonging to the same owner"() {
        expect:
        service.tagAndContactBelongToSame(tag1.id, c1.id)
        service.tagAndContactBelongToSame(tag1.id, c1_1.id)
        service.tagAndContactBelongToSame(tag1.id, c1_2.id)
        service.tagAndContactBelongToSame(tag2.id, c2.id)
        service.tagAndContactBelongToSame(teTag1.id, tC1.id)
        service.tagAndContactBelongToSame(teTag2.id, tC2.id)

        !service.tagAndContactBelongToSame(teTag1.id, tC2.id)
        !service.tagAndContactBelongToSame(teTag2.id, tC1.id)
        !service.tagAndContactBelongToSame(tag2.id, c1.id)
        !service.tagAndContactBelongToSame(tag2.id, tC1.id)
        !service.tagAndContactBelongToSame(null, tC1.id)
        !service.tagAndContactBelongToSame(tag2.id, null)
        !service.tagAndContactBelongToSame(null, null)
    }

    void "test permissions for record item"() {
    	when: "This item belongs to one of your contacts"
    	boolean hasPermissions = service.hasPermissionsForItem(rText1.id)

    	then:
    	hasPermissions == true

    	when: "This item belongs to a contact that is currently shared with you"
    	hasPermissions = service.hasPermissionsForItem(rText2.id)

    	then:
    	hasPermissions == true

    	when: "This item belongs to a contact of one of the teams you're on"
    	hasPermissions = service.hasPermissionsForItem(rTeText1.id)

    	then:
    	hasPermissions == true

    	when: "This item belongs to a different team at same org"
    	hasPermissions = service.hasPermissionsForItem(rTeText2.id)

    	then:
    	hasPermissions == false

    	when: "This item belongs to a staff member at a different org"
    	hasPermissions = service.hasPermissionsForItem(otherRText2.id)

    	then:
    	hasPermissions == false

    	when: "This item belongs to a team at a different org"
    	hasPermissions = service.hasPermissionsForItem(otherRTeText2.id)

    	then:
    	hasPermissions == false
    }
}
