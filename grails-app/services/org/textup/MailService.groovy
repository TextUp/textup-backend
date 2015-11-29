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

    Result<SendGrid.Response> notifyAdminOfPendingStaff() {

    }
    Result<SendGrid.Response> notifyPendingOfApproval() {

    }
    Result<SendGrid.Response> notifyPendingOfRejection() {

    }

    //////////////////////////////////
    // Signup with new organization //
    //////////////////////////////////

    Result<SendGrid.Response> notifySuperOfNewOrganization() {
    	String body = getMessage("mail.passwordReset.body", [s1.username, resetLink]),
    		subject = getMessage("mail.passwordReset.subject")
        EmailEntity to = new EmailEntity(name:s1.name, email, s1.email)
        sendMail(to, getDefaultFrom(), subject, body)
    }
    Result<SendGrid.Response> notifyNewOrganizationOfApproval() {

    }
    Result<SendGrid.Response> notifyNewOrganizationOfRejection() {

    }

    ////////////////////
    // Reset password //
    ////////////////////

    Result<SendGrid.Response> notifyPasswordReset(Staff s1, String resetLink) {
    	String body = getMessage("mail.passwordReset.body", [s1.username, resetLink]),
    		subject = getMessage("mail.passwordReset.subject")
        EmailEntity to = new EmailEntity(name:s1.name, email, s1.email)
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
