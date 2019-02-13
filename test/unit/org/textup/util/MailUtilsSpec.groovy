package org.textup.util

import com.sendgrid.Response as SendGridResponse
import com.sendgrid.SendGrid
import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*
import spock.util.mop.ConfineMetaClassChanges

@TestMixin(GrailsUnitTestMixin)
class MailUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test building entities"() {
        expect:
        MailUtils.defaultFromEntity().validate() == true
        MailUtils.selfEntity().validate() == true
    }

    void "test getting template id"() {
        expect:
        MailUtils.getTemplateId(MailData.InvitedStaff) instanceof String
        MailUtils.getTemplateId(MailData.InvitedStaff).size() > 1
        MailUtils.getTemplateId(MailData.ApprovedStaff) instanceof String
        MailUtils.getTemplateId(MailData.ApprovedStaff).size() > 1
        MailUtils.getTemplateId(MailData.PendingStaff) instanceof String
        MailUtils.getTemplateId(MailData.PendingStaff).size() > 1
        MailUtils.getTemplateId(MailData.RejectedStaff) instanceof String
        MailUtils.getTemplateId(MailData.RejectedStaff).size() > 1
        MailUtils.getTemplateId(MailData.PendingOrg) instanceof String
        MailUtils.getTemplateId(MailData.PendingOrg).size() > 1
        MailUtils.getTemplateId(MailData.PasswordReset) instanceof String
        MailUtils.getTemplateId(MailData.PasswordReset).size() > 1
        MailUtils.getTemplateId(MailData.Notification) instanceof String
        MailUtils.getTemplateId(MailData.Notification).size() > 1

        and: "returns empty string for nonvalid classes"
        MailUtils.getTemplateId(Collection) == ""
    }

    // see global mock cleanup bug: https://github.com/spockframework/spock/issues/445
    @ConfineMetaClassChanges([SendGrid])
    void "test sending emal"() {
        given:
        GroovySpy(SendGrid, constructorArgs: [TestUtils.randString()], global: true) // mocking constructor
        SendGrid sendGrid = Mock()
        SendGridResponse mockResponse = Mock()
        EmailEntity fromEntity = MailUtils.defaultFromEntity()
        EmailEntity toEntity = MailUtils.selfEntity()
        String tId = TestUtils.randString()
        String key1 = TestUtils.randString()
        String val1 = TestUtils.randString()
        Map data = [(key1): val1]

        when:
        Result res = MailUtils.send(fromEntity, toEntity, tId, data)

        then:
        1 * new SendGrid(*_) >> sendGrid
        1 * sendGrid.api(*_) >> mockResponse
        1 * mockResponse.statusCode >> ResultStatus.OK.intStatus
        res.status == ResultStatus.NO_CONTENT
    }
}
