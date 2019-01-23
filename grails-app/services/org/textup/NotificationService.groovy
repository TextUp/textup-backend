package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.TimeUnit
import org.joda.time.DateTime
import org.textup.annotation.*
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class NotificationService {

    MailService mailService
    TextService textService
    TokenService tokenService

    Result<Void> send(NotificationFrequency freq1, NotificationGroup notifGroup) {
        ResultGroup<?> resGroup = new ResultGroup<>()
        notifGroup.eachNotification(freq1) { OwnerPolicy op1, Notification notif1 ->
            resGroup << tokenService.tryGeneratePreviewInfo(op1, notif1)
                .then { Token tok1 = null ->
                    NotificationInfo notifInfo = NotificationInfo.create(op1, notif1)
                    if (op1.method == NotificationMethod.TEXT) {
                        textService
                            .send(notif1.mutablePhone.number,
                                [op1.staff.personalNumber],
                                notifInfo.buildTextMessage(tok1),
                                notif1.mutablePhone.customAccountId)
                            .logFail("send: text to staff `${s1.id}`")
                    }
                    else { mailService.notifyMessages(freq1, op1.staff, notifInfo, tok1) }
                }
        }
        resGroup.toEmptyResult(false)
    }

    Result<Notification> redeem(String token) {
        tokenService.tryFindPreviewInfo(token)
            .then { Tuple<Long, Notification> tup1 ->
                Tuple.split(tup1) { Long ownerPolicyId, Notification notif1 ->
                    RequestUtils.trySetOnRequest(RequestUtils.OWNER_POLICY_ID, ownerPolicyId)
                    DomainUtils.tryValidate(notif1)
                }
            }
    }
}
