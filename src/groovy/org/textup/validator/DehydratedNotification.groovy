package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
@Validateable
class DehydratedNotification implements CanValidate, Rehydratable<Notification> {

    final Long phoneId
    final Collection<Long> itemIds

    static Result<DehydratedNotification> tryCreate(Notification notif1) {
        DomainUtils.tryValidate(notif1)
            .then { tryCreate(notif1.mutablePhone.id, notif1.itemIds) }
    }

    static Result<DehydratedNotification> tryCreate(Long pId, Collection<Long> itemIds) {
        DehydratedNotification dn1 = new DehydratedNotification(phoneId: pId,
            itemIds: Collections.unmodifiableCollection(itemIds))
        DomainUtils.tryValidate(dn1, ResultStatus.CREATED)
    }

    // Methods
    // -------

    @Override
    Result<Notification> tryRehydrate() {
        Collection<? extends RecordItem> rItems = AsyncUtils.getAllIds(RecordItem, itemIds)
        NotificationUtils.tryBuildNotificationsForItems(rItems, [phoneId])
            .then { List<Notification> notifs -> DomainUtils.tryValidate(notifs[0]) }
    }
}
