package org.textup.action

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional

@GrailsTypeChecked
@Transactional
class NumberActionService
    implements HandlesActions<IndividualPhoneRecordWrapper, IndividualPhoneRecordWrapper> {

    @Override
    boolean hasActions(Map body) { !!body.doNumberActions }

    @Override
    Result<IndividualPhoneRecordWrapper> tryHandleActions(IndividualPhoneRecordWrapper w1, Map body) {
        ActionContainer.tryProcess(ContactNumberAction, body.doNumberActions)
            .then { List<ContactNumberAction> actions ->
                ResultGroup<?> resGroup = new ResultGroup<>()
                actions.each { ContactNumberAction a1 ->
                    switch (a1) {
                        case ContactNumberAction.MERGE:
                            resGroup << w1.tryMergeNumber(a1.buildPhoneNumber(), a1.preference)
                            break
                        default: // ContactNumberAction.DELETE
                            resGroup << w1.tryDeleteNumber(a1.buildPhoneNumber())
                    }
                }
                resGroup.toEmptyResult(false)
            }
            .then { DomainUtils.trySave(w1) }
    }
}
