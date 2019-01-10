package org.textup.action

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional

@GrailsTypeChecked
@Transactional
class TagActionService implements HandlesActions<ContactTag, ContactTag> {

    @Override
    boolean hasActions(Map body) { !!body.doTagActions }

    @Override
    Result<ContactTag> tryHandleActions(ContactTag ct1, Map body) {
        ActionContainer.tryProcess(ContactTagAction, body.doTagActions)
            .then { List<ContactTagAction> actions ->
                // Do not need to check if each contact's phone matches the ContactTag's phone
                // because we may want to support tags that span multiple phones. The key thing
                // is that the logged-in user has appropriate permissions to modify this tag
                actions.each { ContactTagAction a1 ->
                    switch (a1) {
                        case ContactTagAction.ADD:
                            ct1.addToMembers(a1.contact)
                            break
                        default: // ContactTagAction.REMOVE
                            ct1.removeFromMembers(a1.contact)
                    }
                }
                DomainUtils.trySave(ct1)
            }
    }
}
