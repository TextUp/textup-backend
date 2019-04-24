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

@EqualsAndHashCode(includeFields = true)
@GrailsTypeChecked
@TupleConstructor(includeFields = true)
@Validateable
class NotificationGroup implements CanValidate {

    final Collection<Notification> notifications

    static constraints = {
        notifications cascadeValidation: true
    }

    static Result<NotificationGroup> tryCreate(Collection<Notification> many1) {
        Map<Phone, Collection<Notification>> phoneToNotifs = MapUtils
            .buildManyUniqueObjectsMap(many1) { Notification notif1 -> notif1.mutablePhone }
        ResultGroup
            .collectEntries(phoneToNotifs) { Phone mutPhone1, Collection<Notification> many2 ->
                Notification.tryCreate(mutPhone1)
                    .then { Notification notif1 ->
                        Collection<NotificationDetail> nds = CollectionUtils.mergeUnique(many2*.details)
                        nds.each { NotificationDetail nd1 -> notif1.addDetail(nd1) }
                        DomainUtils.tryValidate(notif1)
                    }
            }
            .toResult(false)
            .then { List<Notification> many2 ->
                Collection<Notification> notifs = Collections.unmodifiableCollection(many2)
                DomainUtils.tryValidate(new NotificationGroup(notifs), ResultStatus.CREATED)
            }
    }

    // Methods
    // -------

    boolean canNotifyAnyAllFrequencies() {
        notifications?.any { Notification notif1 -> notif1.canNotifyAny(null) }
    }

    Collection<? extends ReadOnlyOwnerPolicy> buildCanNotifyReadOnlyPoliciesAllFrequencies() {
        buildCanNotifyReadOnlyPolicies(null)
    }
    Collection<? extends ReadOnlyOwnerPolicy> buildCanNotifyReadOnlyPolicies(NotificationFrequency freq1) {
        HashSet<? extends ReadOnlyOwnerPolicy> canNotifyPolicies = new HashSet<>()
        notifications?.each { Notification notif1 ->
            canNotifyPolicies.addAll(notif1.buildCanNotifyReadOnlyPolicies(freq1))
        }
        CollectionUtils.ensureNoNull(canNotifyPolicies)
    }

    // if frequency is null then find all canNotify policies
    void eachNotification(NotificationFrequency freq1, Closure<?> action) {
        notifications?.each { Notification notif1 ->
            notif1.buildCanNotifyReadOnlyPolicies(freq1).each { ReadOnlyOwnerPolicy rop1 ->
                action.call(rop1, notif1)
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

    int getNumNotifiedForItem(RecordItem item, NotificationFrequency freq1 = null) {
        notifications?.inject(0) { int sum, Notification notif1 ->
            sum + notif1.getNumNotifiedForItem(item, freq1)
        } as Integer ?: 0
    }
}
