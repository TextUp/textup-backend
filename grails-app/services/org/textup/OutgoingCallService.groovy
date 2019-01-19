package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional

@GrailsTypeChecked
@Transactional
class OutgoingCallService {

    CallService callService

    Result<RecordCall> tryStart(BasePhoneNumber personalNum, IndividualPhoneRecordWrapper w1,
        Author author1) {

        if (!personalNum) {
            return IOCUtils.resultFactory.failWithCodeAndStatus(
                "outgoingMessageService.startBridgeCall.noPersonalNumber", // TODO
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        w1.tryGetPhone()
            .then { Phone p1 ->
                callService.start(p1.number,
                    [personalNum],
                    CallTwiml.infoForFinishBridge(w1.id),
                    p1.customAccountId)
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
