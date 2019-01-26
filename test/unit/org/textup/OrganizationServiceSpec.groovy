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

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestFor(OrganizationService)
@TestMixin(HibernateTestMixin)
class OrganizationServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }
    def setup() {
        super.setupData()
        service.resultFactory = TestUtils.getResultFactory(grailsApplication)
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
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "organizationService.update.notFound"

    	when: "we update location with invalid fields"
        updateInfo = [location:[
            lat:-1000G,
            lon:-888G
        ]]
        res = service.update(org.id, updateInfo)

    	then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 2

    	when: "we update with valid fields"
        org.awayMessageSuffix = TestUtils.randString()
        org.save(flush: true, failOnError: true)

        String newName = "I am a new name"
        BigInteger newLat = 22G, newLon = 22G
        int newTimeout = Constants.DEFAULT_LOCK_TIMEOUT_MILLIS + 1
        updateInfo = [
            name:newName,
            awayMessageSuffix: "",
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
        res.payload.awayMessageSuffix == ""
        res.payload.location.lat == newLat
        res.payload.location.lon == newLon
    }
}
