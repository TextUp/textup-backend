package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.TimeUnit
import org.joda.time.DateTime
import org.textup.annotation.*
import org.textup.rest.*
import org.textup.structure.*
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

    Result<Void> send(NotificationGroup notifGroup, NotificationFrequency freq1 = null) {
        ResultGroup<?> resGroup = new ResultGroup<>()
        notifGroup?.eachNotification(freq1) { ReadOnlyOwnerPolicy rop1, Notification notif1 ->
            resGroup << tokenService.tryGeneratePreviewInfo(rop1, notif1)
                .then { Token tok1 = null ->
                    NotificationInfo notifInfo = NotificationInfo.create(rop1, notif1)
                    if (rop1.method == NotificationMethod.TEXT) {
                        if (rop1.readOnlyStaff.hasPersonalNumber()) {
                            textService
                                .send(notif1.mutablePhone.number,
                                    [rop1.readOnlyStaff.personalNumber],
                                    notifInfo.buildTextMessage(freq1, tok1),
                                    notif1.mutablePhone.customAccountId)
                                .logFail("send: phone `${notif1.mutablePhone.id}`, staff `${rop1.readOnlyStaff.id}`")
                        }
                        else { Result.void() }
                    }
                    else { mailService.notifyMessages(rop1.readOnlyStaff, notifInfo, freq1, tok1) }
                }
        }
        resGroup.toEmptyResult(false)
    }

    Result<Notification> redeem(String token) {
        tokenService.tryFindPreviewInfo(token)
            .then { Tuple<Long, Notification> tup1 ->
                Tuple.split(tup1) { Long staffId = null, Notification notif1 ->
                    RequestUtils.trySet(RequestUtils.STAFF_ID, staffId)
                    DomainUtils.tryValidate(notif1)
                }
            }
    }
}
