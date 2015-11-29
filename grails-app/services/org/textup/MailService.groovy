package org.textup

import grails.transaction.Transactional
import com.sendgrid.SendGrid
import com.sendgrid.SendGridException
import org.springframework.context.i18n.LocaleContextHolder as LCH

@Transactional
class MailService {

	def messageSource
	def resultFactory

	/////////////////////////////////////
	// Signup at existing organization //
	/////////////////////////////////////

    Result notifyAdminsOfPendingStaff(String pendingName, List<Staff> admins) {
        String body = getMessage("mail.pendingForAdmin.body", [pendingName]),
            subject = getMessage("mail.pendingForAdmin.subject")
        List<Result> successes = [], failures = []
        admins.each { Staff a1 ->
            EmailEntity to = new EmailEntity(name:a1.name, email:a1.email)
            Result res = sendMail(to, getDefaultFrom(), subject, body)
            if (res.success) { successes << res }
            else { failures << res }
        }
        if (!sucesses.isEmpty()) { resultFactory.success() }
        if (sucesses.isEmpty() && !failures.isEmpty()) { failures[0] }
        else {
            resultFactory.failWithMessage("mailService.notifyAdminsOfPendingStaff.noAdmins")
        }
    }
    Result<SendGrid.Response> notifyPendingOfApproval(Staff approvedStaff) {
        def links = grailsApplication.config.textup.links
        String body = getMessage("mail.approveForPending.body", [approvedStaff.name, approvedStaff.org.name, approvedStaff.username, links.setupExistingOrg]),
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

    //////////////////////////////////
    // Signup with new organization //
    //////////////////////////////////

    Result<SendGrid.Response> notifySuperOfNewOrganization(String orgName) {
    	String body = getMessage("mail.newOrganizationForSuper.body", [orgName]),
    		subject = getMessage("mail.newOrganizationForSuper.subject")
        def selfMailConfig = grailsApplication.config.textup.mail.self
        EmailEntity to = new EmailEntity(name:selfMailConfig.name, email:selfMailConfig.email)
        sendMail(to, getDefaultFrom(), subject, body)
    }
    Result<SendGrid.Response> notifyNewOrganizationOfApproval(Staff newOrgAdmin) {
        def links = grailsApplication.config.textup.links
        String body = getMessage("mail.approveForNewOrg.body", [newOrgAdmin.name, newOrgAdmin.org.name, newOrgAdmin.username, links.setupNewOrg]),
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

    ////////////////////
    // Reset password //
    ////////////////////

    Result<SendGrid.Response> notifyPasswordReset(Staff s1, String token) {
        def links = grailsApplication.config.textup.links
        String body = getMessage("mail.passwordReset.body", [s1.username, links.passwordReset + token]),
    		subject = getMessage("mail.passwordReset.subject")
        EmailEntity to = new EmailEntity(name:s1.name, email:s1.email)
        sendMail(to, getDefaultFrom(), subject, body)
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    protected EmailEntity getDefaultFrom() {
    	def defaultMailConfig = grailsApplication.config.textup.mail.standard
    	new EmailEntity(name:defaultMailConfig.name, email:defaultMailConfig.email)
    }

    protected String getMessage(String code, List<String> options=[]) {
    	messageSource.getMessage(code, options as Object[], LCH.getLocale())
    }

    protected Result<SendGrid.Response> sendMail(EmailEntity to, EmailEntity from, String subject, 
    	String contents, String templateId=null) {
    	if (!to.validate()) { return resultFactory.failWithValidationErrors(to.errors) }
    	if (!from.validate()) { return resultFactory.failWithValidationErrors(from.errors) }
    	def sgConfig = grailsApplication.config.apiKeys.sendGrid
    	templateId = templateId ?: sgConfig.templateIds.standard
        SendGrid.Email email = new SendGrid.Email()
        email.with {
            addTo to.email
            addToName to.name
            setFrom from.email 
            setFromName from.name
            setSubject subject
            setText contents 
            setTemplateId templateId
        }
        try {
            SendGrid.Response response = new SendGrid(sgConfig.username, sgConfig.password).send(email)
            if (response.status) { resultFactory.success(response) }
            else { resultFactory.failWithMessage("resultFactory.echoMessage", [response.message]) }
        }
        catch (SendGridException e) {
            log.error("MailService.sendMail: ${e.message}")
            resultFactory.failWithThrowable(e)
        }
    }
}
