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
class NotificationDetail implements CanValidate {

    final PhoneRecordWrapper wrapper
    final HashSet<? extends RecordItem> items

    static constraints = {
        wrapper validator: { PhoneRecordWrapper val ->
            if (!val.permissions.canModify()) { ["insufficientPermission", val.id] }
        }
        items validator: { HashSet<? extends RecordItem> val, NotificationDetail obj ->
            Record rec1 = obj.wrapper?.tryGetRecord()?.payload
            if (rec1 && val?.any { RecordItem rItem1 -> rItem1.record.id != rec1.id }) {
                ["mismatched", rec1.id]
            }
        }
    }

    static Result<NotificationDetail> tryCreate(PhoneRecordWrapper w1) {
        NotificationDetail nd1 = new NotificationDetail(w1, new HashSet<? extends RecordItem>())
        DomainUtils.tryValidate(nd1, ResultStatus.CREATED)
    }

    // Methods
    // -------

    boolean anyAllowedItemsForOwnerPolicy(ReadOnlyOwnerPolicy rop1) {
        items.any { RecordItem rItem1 -> rItem1 && rop1?.isAllowed(rItem1.record.id) }
    }

    Collection<? extends RecordItem> buildAllowedItemsForOwnerPolicy(ReadOnlyOwnerPolicy rop1) {
        items.findAll { RecordItem rItem1 -> rItem1 && rop1?.isAllowed(rItem1.record.id) }
    }

    int countItemsForOutgoingAndOptions(boolean isOut, ReadOnlyOwnerPolicy rop1 = null,
        Class<? extends RecordItem> clazz = null) {

        items.count { RecordItem rItem1 ->
            rItem1 && rItem1.outgoing == isOut &&
                (!rop1 || rop1.isAllowed(rItem1.record.id)) && // owner policy is optional
                (!clazz || clazz.isAssignableFrom(rItem1.class)) // class is optional
        } as Integer
    }

    int countVoicemails(ReadOnlyOwnerPolicy rop1) {
        items.count { RecordItem rItem1 ->
            if (rItem1 instanceof RecordCall) {
                RecordCall rCall1 = rItem1 as RecordCall
                rop1?.isAllowed(rCall1.record.id) && rCall1.isVoicemail
            }
            else { false }
        } as Integer
    }
}
