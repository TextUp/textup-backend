package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
@TupleConstructor(includeFields = true)
@Validateable
class DehydratedNotification implements CanValidate, Rehydratable<Notification> {

    final Collection<Long> itemIds
    final Long phoneId

    static Result<DehydratedNotification> tryCreate(Notification notif1) {
        DomainUtils.tryValidate(notif1)
            .then { tryCreate(notif1.mutablePhone.id, notif1.itemIds) }
    }

    static Result<DehydratedNotification> tryCreate(Long pId, Collection<Long> itemIds) {
        DehydratedNotification dn1 = new DehydratedNotification(
            Collections.unmodifiableCollection(itemIds),
            pId)
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
