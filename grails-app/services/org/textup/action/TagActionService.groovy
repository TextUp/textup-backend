package org.textup.action

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class TagActionService implements HandlesActions<GroupPhoneRecord, GroupPhoneRecord> {

    @Override
    boolean hasActions(Map body) { !!body.doTagActions }

    @Override
    Result<GroupPhoneRecord> tryHandleActions(GroupPhoneRecord gpr1, Map body) {
        ActionContainer.tryProcess(GroupMemberAction, body.doTagActions)
            .then { List<GroupMemberAction> actions ->
                // Do not need to check if each contact's phone matches the tag's phone
                // because we may want to support tags that span multiple phones. The key thing
                // is that the logged-in user has appropriate permissions to modify this tag
                actions.each { GroupMemberAction a1 ->
                    switch (a1) {
                        case GroupMemberAction.ADD:
                            gpr1.members.addToPhoneRecords(a1.buildPhoneRecord())
                            break
                        default: // GroupMemberAction.REMOVE
                            gpr1.members.removeFromPhoneRecords(a1.buildPhoneRecord())
                    }
                }
                DomainUtils.trySave(gpr1)
            }
    }
}
