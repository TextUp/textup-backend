package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
@Validateable
class NotificationDetail implements CanValidate {

    final PhoneRecordWrapper wrapper
    final HashSet<? extends RecordItem> items = new HashSet<>()

    static constraints = {
        wrapper validator: { PhoneRecordWrapper val ->
            if (!val.permissions.canModify()) { ["insufficientPermission"] }
        }
        items validator: { HashSet<? extends RecordItem> val, NotificationDetail obj ->
            Record rec1 = obj.wrapper?.tryGetRecord()?.payload
            if (rec1 && val?.any { RecordItem rItem1 -> rItem1.record.id != rec1.id }) {
                ["mismatched", rec1.id]
            }
        }
    }

    static Result<NotificationDetail> tryCreate(PhoneRecordWrapper w1) {
        DomainUtils.tryValidate(new NotificationDetail(wrapper: w1), ResultStatus.CREATED)
    }

    // Methods
    // -------

    Collection<? extends RecordItem> buildAllowedItemsForOwnerPolicy(OwnerPolicy op1) {
        items.findAll { RecordItem rItem1 -> op1.isAllowed(rItem1.id) }
    }

    int countItemsForOutgoingAndOptions(boolean isOut, OwnerPolicy op1 = null,
        Class<? extends RecordItem> clazz = null) {

        items.count { RecordItem rItem1 ->
            rItem1.outgoing == isOut &&
                (!op1 || op1.isAllowed(rItem1.id)) && // owner policy is optional
                (!clazz || clazz.isAssignableFrom(rItem1.class)) // class is optional
        } as Integer
    }

    int countVoicemails(OwnerPolicy op1) {
        items.count { RecordItem rItem1 ->
            if (rItem1 instanceof RecordCall) {
                RecordCall rCall1 = rItem1 as RecordCall
                op1.isAllowed(rCall1.id) && rCall1.isVoicemail
            }
            else { false }
        } as Integer
    }
}
