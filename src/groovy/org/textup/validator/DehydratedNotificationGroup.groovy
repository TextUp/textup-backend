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
class DehydratedNotificationGroup implements CanValidate, Rehydratable<NotificationGroup> {

    private final Collection<Long> itemIds

    static Result<DehydratedNotificationGroup> tryCreate(NotificationGroup notifGroup) {
        DomainUtils.tryValidate(notifGroup).then {
            Collection<Long> itemIds = CollectionUtils.mergeUnique(notifGroup.notifications*.itemIds)
            DehydratedNotificationGroup dng1 = new DehydratedNotificationGroup(itemIds: itemIds)
            DomainUtils.tryValidate(dng1, ResultStatus.CREATED)
        }
    }

    // Methods
    // -------

    @Override
    Result<NotificationGroup> tryRehydrate() {
        Collection<RecordItem> rItems = AsyncUtils.getAllIds(RecordItem, itemIds)
        NotificationUtils.tryBuildNotificationGroup(rItems)
    }
}
