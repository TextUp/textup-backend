package org.textup

import com.sendgrid.ASM
import com.sendgrid.Mail
import com.sendgrid.Method as SendGridMethod
import com.sendgrid.Personalization
import com.sendgrid.Request as SendGridRequest
import com.sendgrid.Response as SendGridResponse
import com.sendgrid.SendGrid
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.textup.validator.EmailEntity

@GrailsTypeChecked
@Transactional
class MailService {

    GrailsApplication grailsApplication
	ResultFactory resultFactory

    // Welcome
    // -------

    Result<SendGridResponse> notifyInvitation(Staff invitedBy, Staff invited, String password,
        String lockCode) {

        String templateId = getTemplateId("invited")
        Map<String, String> data = [
            inviter: invitedBy.name,
            invitee: invited.name,
            username: invited.username,
            password: password,
            lockCode: lockCode,
            link: getLink("setupAccount")
        ]
        EmailEntity to = new EmailEntity(name:invited.name, email:invited.email)
        sendMail(to, getDefaultFrom(), templateId, data)
    }
    Result<SendGridResponse> notifyApproval(Staff approvedStaff) {
        String templateId = getTemplateId("approved")
        Map<String, String> data = [
            name: approvedStaff.name,
            username: approvedStaff.username,
            org: approvedStaff.org?.name,
            link: getLink("setupAccount")
        ]
        EmailEntity to = new EmailEntity(name:approvedStaff.name, email:approvedStaff.email)
        sendMail(to, getDefaultFrom(), templateId, data)
    }

    // Status
    // ------

    Result<List<SendGridResponse>> notifyAboutPendingStaff(Staff pendingStaff, List<Staff> admins) {
        String templateId = getTemplateId("pendingStaff")
        Map<String, String> data = [
            staff: pendingStaff.name,
            org: pendingStaff.org?.name,
            link: getLink("adminDashboard")
        ]
        List<SendGridResponse> successes = []
        List<Result> failures = []
        admins.each { Staff a1 ->
            EmailEntity to = new EmailEntity(name:a1.name, email:a1.email)
            Result res = sendMail(to, getDefaultFrom(), templateId, data)
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
            resultFactory.failWithCodeAndStatus("mailService.notifyAboutPendingStaff.noAdmins",
                ResultStatus.BAD_REQUEST)
        }
    }
    Result<SendGridResponse> notifyAboutPendingOrg(Organization newOrg) {
        String templateId = getTemplateId("pendingOrg")
        Map<String, String> data = [
            org: newOrg.name,
            link: getLink("superDashboard")
        ]
        String name = config("textup.mail.self.name"),
            email = config("textup.mail.self.email")
        EmailEntity to = new EmailEntity(name:name, email:email)
        sendMail(to, getDefaultFrom(), templateId, data)
    }
    Result<SendGridResponse> notifyRejection(Staff rejectedStaff) {
        String templateId = getTemplateId("rejected")
        Map<String, String> data = [
            name: rejectedStaff.name,
            username: rejectedStaff.username
        ]
        EmailEntity to = new EmailEntity(name:rejectedStaff.name, email:rejectedStaff.email)
        sendMail(to, getDefaultFrom(), templateId, data)
    }

    // Reset password
    // --------------

    Result<SendGridResponse> notifyPasswordReset(Staff s1, String token) {
        String templateId = getTemplateId("passwordReset")
        Map<String, String> data = [
            name: s1.name,
            username: s1.username,
            link: getLink("passwordReset") + token
        ]
        EmailEntity to = new EmailEntity(name:s1.name, email:s1.email)
        sendMail(to, getDefaultFrom(), templateId, data)
    }

    // Send email
    // ----------

    protected Result<SendGridResponse> sendMail(EmailEntity toEntity, EmailEntity fromEntity,
        String templateId1, Map<String, String> data=[:]) {
        if (!toEntity.validate()) {
            return resultFactory.failWithValidationErrors(toEntity.errors)
        }
        if (!fromEntity.validate()) {
            return resultFactory.failWithValidationErrors(fromEntity.errors)
        }
        // "personalize" the email
        Personalization pers1 = new Personalization()
        pers1.addTo(toEntity.toEmail())
        data.each { String key, String val ->
            pers1.addSubstitution(formatSubstitutionKey(key), val)
        }
        // build the email
        ASM asm = new ASM()
        Integer groupId = TypeConversionUtils.to(Integer, config("textup.apiKeys.sendGrid.groupIds.account"))
        if (groupId != null) {
            asm.setGroupId(groupId)
        }
        Mail mail1 = new Mail()
        mail1.with {
            setASM(asm)
            addPersonalization(pers1)
            from = fromEntity.toEmail()
            templateId = templateId1
        }
        // build the request
        SendGridRequest req1 = new SendGridRequest()
        req1.with {
            method = SendGridMethod.POST
            endpoint = "mail/send"
            body = mail1.build()
        }
        // send the request
        try {
            SendGrid sg = new SendGrid(config("textup.apiKeys.sendGrid.apiKey"))
            SendGridResponse resp1 = sg.api(req1)
            if (ResultStatus.convert(resp1.statusCode).isSuccess) {
                resultFactory.success(resp1)
            }
            else { resultFactory.failForSendGrid(resp1) }
        }
        catch (IOException e) {
            log.error("MailService.sendMail: ${e.message}")
            e.printStackTrace()
            resultFactory.failWithThrowable(e)
        }
    }

    // Utility methods
    // ---------------

    protected EmailEntity getDefaultFrom() {
        String name = config("textup.mail.standard.name"),
            email = config("textup.mail.standard.email")
    	new EmailEntity(name:name, email:email)
    }

    protected String getTemplateId(String templateName) {
        config("textup.apiKeys.sendGrid.templateIds.${templateName}")
    }

    protected String getLink(String linkName) {
        config("textup.links.${linkName}")
    }

    protected String config(String key) {
        grailsApplication.flatConfig[key]
    }

    protected String formatSubstitutionKey(String rawKey) {
        "-${rawKey}-"
    }
}
