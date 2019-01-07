package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.TimeUnit
import org.joda.time.DateTime
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class NotificationService {

    TokenService tokenService

    // TODO
    Result<List<OutgoingNotification>> build(Collection<? extends RecordItem> rItems) {
        // find phones
        // find all staff to send to for each phone
        // group eligible records to report on for each staff for each phone

        Phone.findEveryForItems(rItems).then { Map<Phone, List<RecordItem>> phoneToItems ->
            List<OutgoingNotification> notifs = []
            phoneToItems.each { Phone p1, List<RecordItem> phoneItems ->
                notifs << new OutgoingNotification(phone: p1, items: phoneItems)
            }
            IOCUtils.resultFactory.success(notifs)
        }
    }

    // TODO
    Result<List<OutgoingNotification>> collectIncoming(long timeSince, TimeUnit unit) {
        // find phones with changes in the specified time amount
        // find all staff to sent to for each phone
        // group eligible records to report on for each staff for each phone

        // TODO
        List<RecordItem> rItems = RecordItem.createCriteria().list {
            ne("class", RecordNote.class)
            ge("whenCreated", DateTime.now().minus(TimeUnit.toMillis(timeSince)))
            eq("outgoing", false)
            order("whenCreated", "asc")
        } as List<RecordItem>
        build(rItems)
    }

    // TODO
    // wrap this call to token service in case we need to do additional processing on redeemed
    // notification in the future
    Result<RedeemedNotification> redeem(String token) {
        tokenService.findNotification(token)
    }
}
