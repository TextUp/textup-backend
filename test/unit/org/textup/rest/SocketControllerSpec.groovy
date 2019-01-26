package org.textup.rest

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import groovy.json.JsonBuilder
import javax.servlet.http.HttpServletRequest
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestFor(SocketController)
@TestMixin(HibernateTestMixin)
class SocketControllerSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    private String _username, _token

    def setup() {
        setupData()
		controller.pusherService = new PusherTester("test", "test", "test")
    }
    def cleanup() {
        cleanupData()
    }

    protected String toJson(Closure data) {
    	JsonBuilder builder = new JsonBuilder()
    	builder(data)
    	builder.toString()
    }

    void "test without all required fields"() {
    	when:
    	request.method = "POST"
    	controller.save()

    	then:
    	response.status == HttpServletResponse.SC_FORBIDDEN
    }

    void "test forbidden from accessing private channel outside your own"() {
        when:
        request.method = "POST"
        request.userPrincipal = [
            getPrincipal: {
                [getUsername:{ loggedInUsername }] as UserDetails
            }
        ] as Authentication
        params.channel_name = "private-someothernameherechannel"
        params.socket_id = "socket"
        controller.save()

        then:
        response.status == HttpServletResponse.SC_FORBIDDEN
    }

    void "test successfully authenticate private channel"() {
    	when:
        request.method = "POST"
        request.userPrincipal = [
            getPrincipal: {
                [getUsername:{ loggedInUsername }] as UserDetails
            }
        ] as Authentication
        params.channel_name = "private-${loggedInUsername}"
        params.socket_id = "socket"
        controller.save()

        then:
        response.status == HttpServletResponse.SC_OK
        response.json.socketId == params.socket_id
        response.json.channelName == params.channel_name
    }
}
