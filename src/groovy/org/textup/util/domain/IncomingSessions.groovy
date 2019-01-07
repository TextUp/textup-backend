package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class IncomingSessions {

    static DetachedCriteria<IncomingSession> forPhoneIdWithOptions(Long phoneId,
        Boolean hasCall = null, Boolean hasText = null) {

        new DetachedCriteria(IncomingSession).build {
            eq("phone.id", phoneId)
            if (hasCall != null) {
                eq("isSubscribedToCall", hasCall)
            }
            if (hasText != null) {
                eq("isSubscribedToText", hasText)
            }
        }
    }
}
