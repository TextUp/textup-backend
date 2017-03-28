package org.textup

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.types.ResultType
import org.textup.util.CustomSpec
import spock.lang.Shared
import spock.lang.Specification
import static org.springframework.http.HttpStatus.*

@TestFor(OrganizationService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole])
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
        res.type == ResultType.MESSAGE_STATUS
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
        res.type == ResultType.VALIDATION
        res.payload.errorCount == 2

    	when: "we update with valid fields"
        String newName = "I am a new name"
        BigInteger newLat = 22G, newLon = 22G
        int newTimeout = Constants.DEFAULT_LOCK_TIMEOUT_MILLIS + 1
        updateInfo = [
            name:newName,
            timeout:newTimeout,
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
        res.payload.timeout == newTimeout
        res.payload.location.lat == newLat
        res.payload.location.lon == newLon
    }
}
