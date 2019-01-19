package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class SessionService {

	@RollbackOnResultFailure
	Result<IncomingSession> create(Long ownerId, PhoneOwnershipType type, TypeMap body) {
		Phones.mustFindActiveForOwner(ownerId, type)
			.then { Phone p1 -> PhoneNumber.tryCreate(body.string("number")).curry(p1) }
			.then { Phone p1, PhoneNumber pNum ->
				IncomingSessions.mustFindForPhoneAndNumber(p1, pNum, true)
			}
			.then { IncomingSession is1 -> trySetFields(is1, body) }
			.then { IncomingSession is1 -> DomainUtils.trySave(is1, ResultStatus.CREATED) }
	}

	@RollbackOnResultFailure
	Result<IncomingSession> update(Long isId, TypeMap body) {
		IncomingSessions.mustFindForId(isId)
			.then { IncomingSession is1 -> trySetFields(is1, body) }
	}

	// Helpers
	// -------

	protected Result<IncomingSession> trySetFields(IncomingSession is1, TypeMap body) {
		is1.with {
			if (body.bool("isSubscribedToText") != null) {
				isSubscribedToText = body.bool("isSubscribedToText")
			}
			if (body.bool("isSubscribedToCall") != null) {
				isSubscribedToCall = body.bool("isSubscribedToCall")
			}
		}
		DomainUtils.trySave(is1)
	}
}
