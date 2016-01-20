package org.textup.rest

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectResult
import com.twilio.sdk.resource.instance.Call
import com.twilio.sdk.resource.list.RecordingList
import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.util.CustomSpec
import spock.lang.Shared
import spock.lang.Specification
import static org.springframework.http.HttpStatus.*

@TestFor(CallService)
@Domain([TagMembership, Contact, Phone, ContactTag,
    ContactNumber, Record, RecordItem, RecordNote, RecordText,
    RecordCall, RecordItemReceipt, PhoneNumber, SharedContact,
    TeamMembership, StaffPhone, Staff, Team, Organization,
    Schedule, Location, TeamPhone, WeeklySchedule, TeamContactTag])
@TestMixin(HibernateTestMixin)
class CallServiceSpec extends CustomSpec {

    private final String _testingCallSid = "testing123"
    private final int _numRecordings = 3

    static doWithSpring = {
        resultFactory(ResultFactory)
        twimlBuilder(TwimlBuilder)
        lockService(LockService) { bean -> bean.autoWire = true }
    }
    def setup() {
        super.setupData()

        ResultFactory resFac = getResultFactory()
        service.resultFactory = resFac
        service.twimlBuilder = getBean("twimlBuilder")
        service.lockService = getBean("lockService")
        service.s3Service = [putObject:{ String n, String k, InputStream i, ObjectMetadata m ->
            new PutObjectResult()
        }] as AmazonS3Client
        service.grailsApplication = [config:config]
        service.recordService = [
            updateCallStatus: { String a, String s, Integer d ->
                resFac.success()
            }, createIncomingRecordCall: { PhoneNumber f, Phone t, Map p ->
                resFac.success()
            }, createOutgoingRecordCall: { Phone f, PhoneNumber t, Map p ->
                resFac.success()
            }, createRecordCallForContact: { long c, String f, String t,
        Integer d, Map p ->
                resFac.success()
            }
        ] as RecordService
        service.metaClass.makeCallHelper = { String t, String f, String c ->
            [sid:_testingCallSid] as Call
        }
        service.metaClass.getCallFromSid = { String sid ->
            List recordings = []
            _numRecordings.times {
                recordings << [getMedia: { String ext -> new BufferedInputStream() },
                    delete: { -> true }]
            }
            [getRecordings: { -> recordings as RecordingList }] as Call
        }
    }
    def cleanup() {
        super.cleanupData()
    }

    /////////////////////////
    // Test helper methods //
    /////////////////////////

    void "test tryCall"() {
        // when: "fail to make call"

        // then: "no receipts added"

        // when: "successfully made call"

        // then:
        expect:
        1 == 2
    }

    void "test stopOnSuccessOrInternalError"() {
        // when: "no numbers"

        // then:

        // when: "one fails with a 500-level error"

        // then:

        // when: "some fail but one succeeds"

        // then:
        expect:
        1 == 2
    }

    void "test exists helper methods"() {
        // when: "team phone exists for number"

        // then:

        // when: "team phone does not exist for number"

        // then:

        // when: "staff phone exists for number"

        // then:

        // when: "staff phone does not exist for number"

        // then:
        expect:
        1 == 2
    }

    //////////////////////////////////
    // Place or retry outgoing call //
    //////////////////////////////////

    void "test call"() {
        // when: "invalid call"

        // then:

        // when: "successful call after 1 failure"

        // then:
        expect:
        1 == 2
    }

    void "test retry"() {
        // when: "nonexistent id"

        // then:

        // when: "contact not found for call"

        // then:

        // when: "no more numbers to retry"

        // then:

        // when: "retry and fail on remaining numbers"

        // then:

        // when: "successful retry"

        // then:
        expect:
        1 == 2
    }

    ///////////////
    // Voicemail //
    ///////////////

    void "test storing voicemail"() {
        // when: "invalid apiId"

        // then:

        // when: "invalid status"

        // then:

        // when: "all valid"

        // then: "calls have voicemail and contacts have updated timestamp"
        expect:
        1 == 2
    }

    ///////////////////////////////////////
    // Connecting incoming call to phone //
    ///////////////////////////////////////

    void "test connecting staff phone"() {
        // when: "we have a nonexistent 'to' number"

        // then:

        // when: "we have an unavailable staff"

        // then:

        // when: "we have an available staff"

        // then:
        expect:
        1 == 2
    }

    void "test connecting team phone"() {
        // when: "we have a nonexistent 'to' number"

        // then:

        // when: "none of the staff on the team are available"

        // then:

        // when: "at least one of the staff is available"

        // then:
        expect:
        1 == 2
    }

    ///////////////////////////////////////////
    // Handling incoming call for team phone //
    ///////////////////////////////////////////

    void "test incoming call to team phone"() {
        // when: "we dial a nonexistent 'to' number"

        // then:

        // when: "we dial a valid team phone"

        // then:
        expect:
        1 == 2
    }

    void "test handling digits in a call to team phone"() {
        // when: "we dial a nonexistent 'to' number"

        // then:

        // when: "we input invalid digits"

        // then:

        // when: "we input valid digits for tag with no messages"

        // then:

        // when: "we input valid digits for tag with messages"

        // then:
        expect:
        1 == 2
    }

    ///////////////////////////
    // Handling calling self //
    ///////////////////////////

    void "test handling digits when calling own staff phone"() {
        // when: "we dial a nonexistent staff phone"

        // then:

        // when: "digits are invalid"

        // then:

        // when: "digits are valid phone number"

        // then:

        // when: "digits are for a contact we don't own"

        // then:

        // when: "digits are for a valid contact"

        // then:
        expect:
        1 == 2
    }
}
