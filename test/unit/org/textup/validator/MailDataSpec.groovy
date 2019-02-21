package org.textup.validator

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.apache.commons.validator.routines.UrlValidator
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class MailDataSpec extends Specification {

    @Shared
    UrlValidator urlValidator = new UrlValidator()

    void "test invited staff data validation"() {
        given:
        String val = TestUtils.randString()

        when:
        MailData.InvitedStaff dataObj = new MailData.InvitedStaff()

        then:
        dataObj.validate() == false

        when:
        dataObj = new MailData.InvitedStaff(inviter: val,
            invitee: val,
            username: val,
            password: val,
            lockCode: val)

        then:
        dataObj.validate()
        urlValidator.isValid(dataObj.link)
    }

    void "test approved staff data validation"() {
        given:
        String val = TestUtils.randString()

        when:
        MailData.ApprovedStaff dataObj = new MailData.ApprovedStaff()

        then:
        dataObj.validate() == false

        when:
        dataObj = new MailData.ApprovedStaff(name: val,
            username: val,
            org: val)

        then:
        dataObj.validate()
        urlValidator.isValid(dataObj.link)
    }

    void "test pending staff data validation"() {
        given:
        String val = TestUtils.randString()

        when:
        MailData.PendingStaff dataObj = new MailData.PendingStaff()

        then:
        dataObj.validate() == false

        when:
        dataObj = new MailData.PendingStaff(staff: val, org: val)

        then:
        dataObj.validate()
        urlValidator.isValid(dataObj.link)
    }

    void "test rejected staff data validation"() {
        given:
        String val = TestUtils.randString()

        when:
        MailData.RejectedStaff dataObj = new MailData.RejectedStaff()

        then:
        dataObj.validate() == false

        when:
        dataObj = new MailData.RejectedStaff(name: val, username: val)

        then:
        dataObj.validate()
    }

    void "test pending org data validation"() {
        given:
        String val = TestUtils.randString()

        when:
        MailData.PendingOrg dataObj = new MailData.PendingOrg()

        then:
        dataObj.validate() == false

        when:
        dataObj = new MailData.PendingOrg(org: val)

        then:
        dataObj.validate()
        urlValidator.isValid(dataObj.link)
    }

    void "test password reset data validation"() {
        given:
        String val = TestUtils.randString()

        when:
        MailData.PasswordReset dataObj = new MailData.PasswordReset()

        then:
        dataObj.validate() == false

        when:
        dataObj = new MailData.PasswordReset(name: val, username: val)

        then:
        !dataObj.validate()
        !urlValidator.isValid(dataObj.link)

        when:
        dataObj.link = TestUtils.randLinkString()

        then:
        dataObj.validate()
        urlValidator.isValid(dataObj.link)
    }

    void "test notification data validation"() {
        given:
        String val = TestUtils.randString()

        when:
        MailData.Notification dataObj = new MailData.Notification()

        then:
        dataObj.validate() == false

        when:
        dataObj = new MailData.Notification(staffName: val,
            phoneName: val,
            phoneNumber: val,
            timePeriodDescription: val,
            incomingDescription: val,
            outgoingDescription: val)

        then: "link is optional"
        dataObj.validate()
        !urlValidator.isValid(dataObj.link)

        when:
        dataObj.link = TestUtils.randLinkString()

        then:
        dataObj.validate()
        urlValidator.isValid(dataObj.link)
        dataObj.numIncoming == 0
        dataObj.numOutgoing == 0
    }
}
