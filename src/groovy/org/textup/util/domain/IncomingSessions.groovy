package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class IncomingSessions {

    static Result<Long> isAllowed(Long thisId) {
        AuthUtils.tryGetAuthId()
            .then { Long authId -> AuthUtils.isAllowed(buildForAuth(thisId, authId).count() > 0) }
            .then { IOCUtils.resultFactory.success(thisId) }
    }

    static Result<IncomingSession> mustFindForId(Long isId) {
        IncomingSession is1 = IncomingSession.get(isId)
        if (is1) {
            IOCUtils.resultFactory.success(is1)
        }
        else {
            resultFactory.failWithCodeAndStatus("sessionService.update.notFound", // TODO
                ResultStatus.NOT_FOUND, [isId])
        }
    }

    static Result<IncomingSession> mustFindForPhoneAndNumber(Phone p1, BasePhoneNumber bNum,
        boolean createIfAbsent) {

        IncomingSession is1 = IncomingSession.findByPhoneAndNumberAsString(p1, bNum?.number)
        if (is1) {
            IOCUtils.resultFactory.success(is1)
        }
        else {
            if (createIfAbsent) {
                IncomingSession.tryCreate(p1, bNum)
            }
            else {
                IOCUtils.resultFactory.failWithCodeAndStatus("", // TODO
                    ResultStatus.NOT_FOUND)
            }
        }
    }

    static DetachedCriteria<IncomingSession> buildForPhoneIdWithOptions(Long phoneId,
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

    // Helpers
    // -------

    protected static DetachedCriteria<IncomingSession> buildForAuth(Long thisId, Long authId) {
        new DetachedCriteria(IncomingSession).build {
            idEq(thisId)
            "in"("phone", Phones.buildAllPhonesForStaffId(authId))
        }
    }
}
