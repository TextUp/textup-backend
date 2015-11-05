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

@TestFor(OrganizationService)
@Domain([TagMembership, Contact, Phone, ContactTag, 
	ContactNumber, Record, RecordItem, RecordNote, RecordText, 
	RecordCall, RecordItemReceipt, PhoneNumber, SharedContact, 
	TeamMembership, StaffPhone, Staff, Team, Organization, 
	Schedule, Location, TeamPhone, WeeklySchedule, TeamContactTag])
@TestMixin(HibernateTestMixin)
class OrganizationServiceSpec extends CustomSpec {

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

    void "test update"() {
    	when: "we try to update a nonexistent organization"
        Map updateInfo = [:]
        Result res = service.update(-88L, updateInfo)

    	then: 
        res.success == false 
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "organizationService.update.notFound"
        res.payload.status == NOT_FOUND

    	when: "we update location with invalid fields"
        updateInfo = [location:[
            lat:-1000G, 
            lon:-888G
        ]]
        res = service.update(org.id, updateInfo)

    	then: 
        res.success == false 
        res.type == Constants.RESULT_VALIDATION
        res.payload.errorCount == 2

    	when: "we update with valid fields"
        String newName = "I am a new name"
        BigInteger newLat = 22G, newLon = 22G
        updateInfo = [
            name:newName,
            location:[
                lat:newLat, 
                lon:newLon
            ]
        ]
        res = service.update(org.id, updateInfo)

    	then: 
    	res.success == true
        res.payload instanceof Organization
        res.payload.name == newName
        res.payload.location.lat == newLat
        res.payload.location.lon == newLon
    }
}
