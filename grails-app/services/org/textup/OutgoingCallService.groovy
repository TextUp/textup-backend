package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.annotation.*
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class OutgoingCallService {

    CallService callService

    Result<RecordCall> tryStart(BasePhoneNumber personalNum, IndividualPhoneRecordWrapper w1,
        Author author1) {

        if (!personalNum || !personalNum?.validate()) {
            return IOCUtils.resultFactory.failWithCodeAndStatus(
                "outgoingCallService.noPersonalNumber",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        w1.tryGetMutablePhone()
            .then { Phone mutPhone1 ->
                // for sharing, the outgoing calls will be bridged through the mutable phone
                // but will have a callerId of the original phone to preserve a single point of
                // contact for the client. See `CallTwiml.finishBridge` to see how the
                // original phone's number is added as the caller id after pickup
                callService.start(mutPhone1.number,
                    [personalNum],
                    CallTwiml.infoForFinishBridge(w1.id),
                    mutPhone1.customAccountId)
            }
            .then { TempRecordReceipt rpt -> afterBridgeCall(w1, author1, rpt) }
    }

    // Helpers
    // -------

    protected Result<RecordCall> afterBridgeCall(IndividualPhoneRecordWrapper w1, Author author1,
        TempRecordReceipt rpt) {

        w1.tryGetRecord()
            .then { Record rec1 -> rec1.storeOutgoing(RecordItemType.CALL, author1) }
            .then { RecordCall rCall1 ->
                rCall1.addReceipt(rpt)
                IOCUtils.resultFactory.success(rCall1, ResultStatus.CREATED)
            }
    }
}
