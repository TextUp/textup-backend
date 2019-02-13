package org.textup.util

import com.sendgrid.ASM
import com.sendgrid.Mail
import com.sendgrid.Method as SendGridMethod
import com.sendgrid.Personalization
import com.sendgrid.Request as SendGridRequest
import com.sendgrid.Response as SendGridResponse
import com.sendgrid.SendGrid
import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class MailUtils {

    private static final ENDPOINT = "mail/send"
    private static final String TEMPLATE_CONFIG_ROOT = "textup.apiKeys.sendGrid.templateIds"

    static EmailEntity defaultFromEntity() {
        String name = Holders.flatConfig["textup.mail.standard.name"],
            email = Holders.flatConfig["textup.mail.standard.email"]
        EmailEntity.tryCreate(name, email)
            .logFail("defaultFromEntity")
            .payload as EmailEntity
    }

    static EmailEntity selfEntity() {
        String name = Holders.flatConfig["textup.mail.self.name"],
            email = Holders.flatConfig["textup.mail.self.email"]
        EmailEntity.tryCreate(name, email)
            .logFail("selfEntity")
            .payload as EmailEntity
    }

    static String getTemplateId(Class<?> clazz) {
        switch (clazz) {
            case MailData.InvitedStaff:
                Holders.flatConfig["${TEMPLATE_CONFIG_ROOT}.invited"]
                break
            case MailData.ApprovedStaff:
                Holders.flatConfig["${TEMPLATE_CONFIG_ROOT}.approved"]
                break
            case MailData.PendingStaff:
                Holders.flatConfig["${TEMPLATE_CONFIG_ROOT}.pendingStaff"]
                break
            case MailData.RejectedStaff:
                Holders.flatConfig["${TEMPLATE_CONFIG_ROOT}.rejected"]
                break
            case MailData.PendingOrg:
                Holders.flatConfig["${TEMPLATE_CONFIG_ROOT}.pendingOrg"]
                break
            case MailData.PasswordReset:
                Holders.flatConfig["${TEMPLATE_CONFIG_ROOT}.passwordReset"]
                break
            case MailData.Notification:
                Holders.flatConfig["${TEMPLATE_CONFIG_ROOT}.notification"]
                break
            default:
                ""
        }
    }

    static Result<Void> send(EmailEntity fromEntity, EmailEntity toEntity, String tId, Map data) {
        DomainUtils.tryValidateAll([fromEntity, toEntity])
            .then {
                // step 1: build the email, docs are incomplete so see source
                // source: https://github.com/sendgrid/sendgrid-java/blob/v4.3.0/src/main/java/com/sendgrid/helpers/mail/objects/Personalization.java
                Personalization pers1 = new Personalization()
                pers1.addTo(toEntity.toSendGridEmail())
                data.each { Object key, Object val ->
                    pers1.addDynamicTemplateData(key?.toString(), val)
                }
                ASM asm1 = new ASM()
                asm1.with {
                    groupId = getGroupId()
                }
                Mail mail1 = new Mail()
                mail1.with {
                    setASM(asm1)
                    addPersonalization(pers1)
                    from = fromEntity.toSendGridEmail()
                    templateId = tId
                }
                // step 2: build the request
                SendGridRequest req1 = new SendGridRequest()
                req1.with {
                    method = SendGridMethod.POST
                    endpoint = ENDPOINT
                    body = mail1.build()
                }
                // step 3: send the request
                try {
                    SendGrid sg = new SendGrid(getApiKey())
                    SendGridResponse resp1 = sg.api(req1)
                    if (ResultStatus.convert(resp1.statusCode).isSuccess) {
                        Result.void()
                    }
                    else { IOCUtils.resultFactory.failForSendGrid(resp1) }
                }
                catch (Throwable e) {
                    IOCUtils.resultFactory.failWithThrowable(e, "send", true)
                }
            }
    }

    // Helpers
    // -------

    protected static Integer getGroupId() {
        TypeUtils.to(Integer, Holders.flatConfig["textup.apiKeys.sendGrid.groupIds.account"])
    }

    protected static String getApiKey() { Holders.flatConfig["textup.apiKeys.sendGrid.apiKey"] }
}
