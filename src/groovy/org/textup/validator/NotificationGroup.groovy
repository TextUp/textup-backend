package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
@Validateable
class NotificationGroup implements Validateable, Dehydratable<NotificationGroup.Dehydrated> {

    private final Collection<Notification> notifications

    static constraints = {
        notifications cascadeValidation: true
    }

    static class Dehydrated implements Rehydratable<NotificationGroup> {

        private final Collection<Long> itemIds

        @Override
        Result<NotificationGroup> tryRehydrate() {
            Collection<RecordItem> rItems = AsyncUtils.getAllIds(RecordItem, itemIds)
            NotificationUtils.tryBuildNotificationGroup(rItems)
        }
    }

    static Result<NotificationGroup> tryCreate(Collection<Notification> many1) {
        Map<Phone, Collection<Notification>> phoneToNotifs = MapUtils
            .buildManyObjectsMap(many1) { Notification notif1 -> notif1.mutablePhone }
        ResultGroup
            .collect(phoneToNotifs) { Phone mutPhone1, Collection<Notification> many2 ->
                Notification.tryCreate(mutPhone1)
                    .then { Notification notif1 ->
                        CollectionUtils.mergeUnique(many2*.details)
                            .each { NotificationDetail nd1 -> notif1.addDetail(nd1) }
                        DomainUtils.tryValidate(notif1)
                    }
            }
            .toResult(false)
            .then { List<Notification> many2 ->
                NotificationGroup notifGroup = new NotificationGroup(notifications: many2)
                DomainUtils.tryValidate(notifGroup, ResultStatus.CREATED)
            }
    }

    // Methods
    // -------

    @Override
    NotificationGroup.Dehydrated dehydrate() {
        Collection<Long> itemIds = CollectionUtils.mergeUnique(*notifications.*details*.items*.id)
        new NotificationGroup.Dehydrated(itemIds: itemIds)
    }

    boolean canNotifyAny(NotificationFrequency freq1) {
        notifications?.any { Notification notif1 -> notif1.canNotifyAny(freq1) }
    }

    Collection<OwnerPolicy> buildCanNotifyPolicies(NotificationFrequency freq1) {
        CollectionUtils.mergeUnique(*notifications*.buildCanNotifyPolicies(freq1))
    }

    void eachNotification(NotificationFrequency freq1, Closure<?> action) {
        notifications.each { Notification notif1 ->
            notif1.buildCanNotifyPolicies(freq1).each { OwnerPolicy op1 ->
                action.call(op1, notif1)
            }
        }
    }

    void eachItem(Closure<?> action) {
        CollectionUtils.mergeUnique(*notifications.*details*.items)
            .each { RecordItem rItem1 -> action.call(rItem1) }
    }

    // Properties
    // ----------

    int getNumNotifiedForItem(NotificationFrequency freq1, RecordItem item) {
        notifications.inject(0) { int sum, Notification notif1 ->
            sum + notif1.getNumNotifiedForItem(item)
        }
    }
}
