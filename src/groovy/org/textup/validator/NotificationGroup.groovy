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
class NotificationGroup implements CanValidate {

    private final Collection<Notification> notifications

    static constraints = {
        notifications cascadeValidation: true
    }

    static Result<NotificationGroup> tryCreate(Collection<Notification> many1) {
        Map<Phone, Collection<Notification>> phoneToNotifs = MapUtils
            .buildManyObjectsMap(many1) { Notification notif1 -> notif1.mutablePhone }
        ResultGroup
            .collect(phoneToNotifs) { Phone mutPhone1, Collection<Notification> many2 ->
                Notification.tryCreate(mutPhone1)
                    .then { Notification notif1 ->
                        Collection<NotificationDetail> nds = CollectionUtils.mergeUnique(many2*.details)
                        nds.each { NotificationDetail nd1 -> notif1.addDetail(nd1) }
                        DomainUtils.tryValidate(notif1)
                    }
            }
            .toResult(false)
            .then { List<Notification> many2 ->
                NotificationGroup notifGroup = new NotificationGroup(many2)
                DomainUtils.tryValidate(notifGroup, ResultStatus.CREATED)
            }
    }

    // Methods
    // -------

    boolean canNotifyAny(NotificationFrequency freq1) {
        notifications?.any { Notification notif1 -> notif1.canNotifyAny(freq1) }
    }

    Collection<OwnerPolicy> buildCanNotifyPolicies(NotificationFrequency freq1) {
        CollectionUtils.mergeUnique(notifications*.buildCanNotifyPolicies(freq1))
    }

    void eachNotification(NotificationFrequency freq1, Closure<?> action) {
        notifications.each { Notification notif1 ->
            notif1.buildCanNotifyPolicies(freq1).each { OwnerPolicy op1 ->
                action.call(op1, notif1)
            }
        }
    }

    void eachItem(Closure<?> action) {
        Collection<? extends RecordItem> rItems = CollectionUtils
            .mergeUnique(notifications*.items)
        rItems.each { RecordItem rItem1 -> action.call(rItem1) }
    }

    // Properties
    // ----------

    int getNumNotifiedForItem(NotificationFrequency freq1, RecordItem item) {
        notifications.inject(0) { int sum, Notification notif1 ->
            sum + notif1.getNumNotifiedForItem(freq1, item)
        } as Integer
    }
}
