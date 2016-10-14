package org.textup

import com.sendgrid.SendGrid
import com.sendgrid.SendGridException
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.textup.validator.EmailEntity

@GrailsTypeChecked
@Transactional
class MailService {

    GrailsApplication grailsApplication
	MessageSource messageSource
	ResultFactory resultFactory

	// Signup at existing organization
    // -------------------------------

    Result<List<SendGrid.Response>> notifyAdminsOfPendingStaff(String pendingName,
        List<Staff> admins) {
        String body = getMessage("mail.pendingForAdmin.body", [pendingName]),
            subject = getMessage("mail.pendingForAdmin.subject")
        List<SendGrid.Response> successes = []
        List<Result> failures = []
        admins.each { Staff a1 ->
            EmailEntity to = new EmailEntity(name:a1.name, email:a1.email)
            Result res = sendMail(to, getDefaultFrom(), subject, body)
            if (res.success) { successes << res.payload }
            else { failures << res }
        }
        if (!successes.isEmpty()) {
            resultFactory.success(successes)
        }
        else if (successes.isEmpty() && !failures.isEmpty()) {
            failures[0]
        }
        else {
            resultFactory.failWithMessage("mailService.notifyAdminsOfPendingStaff.noAdmins")
        }
    }
    Result<SendGrid.Response> notifyPendingOfApproval(Staff approvedStaff) {
        String existingLink = config("textup.links.setupExistingOrg"),
            body = getMessage("mail.approveForPending.body",
                [approvedStaff.name, approvedStaff.org.name, approvedStaff.username,
                    existingLink]),
            subject = getMessage("mail.approveForPending.subject")
        EmailEntity to = new EmailEntity(name:approvedStaff.name, email:approvedStaff.email)
        sendMail(to, getDefaultFrom(), subject, body)
    }
    Result<SendGrid.Response> notifyPendingOfRejection(Staff rejectedStaff) {
        String body = getMessage("mail.rejectForPending.body", [rejectedStaff.org.name]),
            subject = getMessage("mail.rejectForPending.subject")
        EmailEntity to = new EmailEntity(name:rejectedStaff.name, email:rejectedStaff.email)
        sendMail(to, getDefaultFrom(), subject, body)
    }
    Result<SendGrid.Response> notifyStaffOfSignup(Staff s1, String password, String lockCode) {
        String link = config("textup.links.setupExistingOrg"),
            body = getMessage("mail.signupForStaff.body",
                [s1.name, s1.org.name, s1.username, password, lockCode, link]),
            subject = getMessage("mail.signupForStaff.subject",
                [s1.name, s1.org.name])
        EmailEntity to = new EmailEntity(name:s1.name, email:s1.email)
        sendMail(to, getDefaultFrom(), subject, body)
    }

    // Signup with new organization
    // ----------------------------

    Result<SendGrid.Response> notifySuperOfNewOrganization(String orgName) {
    	String body = getMessage("mail.newOrganizationForSuper.body", [orgName]),
    		subject = getMessage("mail.newOrganizationForSuper.subject")
        String name = config("textup.mail.self.name"),
            email = config("textup.mail.self.email")
        EmailEntity to = new EmailEntity(name:name, email:email)
        sendMail(to, getDefaultFrom(), subject, body)
    }
    Result<SendGrid.Response> notifyNewOrganizationOfApproval(Staff newOrgAdmin) {
        String newLink = config("textup.links.setupNewOrg"),
            body = getMessage("mail.approveForNewOrg.body",
                [newOrgAdmin.name, newOrgAdmin.org.name, newOrgAdmin.username, newLink]),
            subject = getMessage("mail.approveForNewOrg.subject")
        EmailEntity to = new EmailEntity(name:newOrgAdmin.name, email:newOrgAdmin.email)
        sendMail(to, getDefaultFrom(), subject, body)
    }
    Result<SendGrid.Response> notifyNewOrganizationOfRejection(Staff newOrgAdmin) {
        String body = getMessage("mail.rejectForNewOrg.body", [newOrgAdmin.org.name]),
            subject = getMessage("mail.rejectForNewOrg.subject")
        EmailEntity to = new EmailEntity(name:newOrgAdmin.name, email:newOrgAdmin.email)
        sendMail(to, getDefaultFrom(), subject, body)
    }

    // Reset password
    // --------------

    Result<SendGrid.Response> notifyPasswordReset(Staff s1, String token) {
        String resetLink = config("textup.links.passwordReset"),
            body = getMessage("mail.passwordReset.body", [s1.username, resetLink + token]),
    		subject = getMessage("mail.passwordReset.subject")
        EmailEntity to = new EmailEntity(name:s1.name, email:s1.email)
        sendMail(to, getDefaultFrom(), subject, body)
    }

    // Helper methods
    // --------------

    protected EmailEntity getDefaultFrom() {
        String name = config("textup.mail.standard.name"),
            email = config("textup.mail.standard.email")
    	new EmailEntity(name:name, email:email)
    }

    protected String getMessage(String code, List<String> options=[]) {
    	messageSource.getMessage(code, options as Object[], LCH.getLocale())
    }

    protected String config(String key) {
        grailsApplication.flatConfig[key]
    }

    protected Result<SendGrid.Response> sendMail(EmailEntity to, EmailEntity from, String subject,
    	String contents, String templateId=null) {
    	if (!to.validate()) {
            return resultFactory.failWithValidationErrors(to.errors)
        }
    	if (!from.validate()) {
            return resultFactory.failWithValidationErrors(from.errors)
        }
    	templateId = templateId ?: config("textup.apiKeys.sendGrid.templateIds.standard")
        SendGrid.Email email = new SendGrid.Email()
        email.with {
            addTo to.email
            addToName to.name
            setFrom from.email
            setFromName from.name
            setSubject subject
            setHtml contents
            setTemplateId templateId
        }
        try {
            String username = config("textup.apiKeys.sendGrid.username"),
                password = config("textup.apiKeys.sendGrid.password")
            SendGrid.Response response = new SendGrid(username, password).send(email)
            if (response.status) { resultFactory.success(response) }
            else { resultFactory.failWithMessage("resultFactory.echoMessage", [response.message]) }
        }
        catch (SendGridException e) {
            log.error("MailService.sendMail: ${e.message}")
            resultFactory.failWithThrowable(e)
        }
    }
}
