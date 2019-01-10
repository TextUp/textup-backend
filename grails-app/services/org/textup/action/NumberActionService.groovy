package org.textup.action

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional

@GrailsTypeChecked
@Transactional
class NumberActionService implements HandlesActions<IndividualPhoneRecord, IndividualPhoneRecord> {

    @Override
    boolean hasActions(Map body) { !!body.doNumberActions }

    @Override
    Result<IndividualPhoneRecord> tryHandleActions(IndividualPhoneRecord ipr1, Map body) {
        ActionContainer.tryProcess(ContactNumberAction, body.doNumberActions)
            .then { List<ContactNumberAction> actions ->
                ResultGroup<?> resGroup = new ResultGroup<>()
                actions.each { ContactNumberAction a1 ->
                    switch (a1) {
                        case ContactNumberAction.MERGE:
                            resGroup << ipr1.mergeNumber(a1.phoneNumber, a1.preference)
                            break
                        default: // ContactNumberAction.DELETE
                            resGroup << ipr1.deleteNumber(a1.phoneNumber)
                    }
                }
                resGroup.toResult()
            }
            .then { DomainUtils.trySave(ipr1) }
    }
}
