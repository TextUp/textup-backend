package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.util.RollbackOnResultFailure
import org.textup.validator.PhoneNumber

@GrailsTypeChecked
@Transactional
class SessionService {

	AuthService authService
	ResultFactory resultFactory

	// Create
	// ------

	Result<IncomingSession> createForTeam(Long tId, Map body) {
		create(Team.get(tId)?.phone, body)
	}
	Result<IncomingSession> createForStaff(Map body) {
		create(authService.loggedInAndActive?.phone, body)
	}

	@RollbackOnResultFailure
	protected Result<IncomingSession> create(Phone p1, Map body) {
		if (!p1) {
			return resultFactory.failWithCodeAndStatus("sessionService.create.noPhone",
				ResultStatus.UNPROCESSABLE_ENTITY)
		}
		PhoneNumber pNum = new PhoneNumber(number:body.number as String)
		if (!pNum.validate()) {
			return resultFactory.failWithValidationErrors(pNum.errors)
		}
		// find or create session
		IncomingSession sess1 = IncomingSession.findByPhoneAndNumberAsString(p1,
			pNum.number) ?: new IncomingSession(phone:p1)
		sess1.number = pNum
		// populate fields
		updateFields(sess1, body)
			.then({ IncomingSession is1 -> resultFactory.success(is1, ResultStatus.CREATED) })
	}

	// Update
	// ------

	@RollbackOnResultFailure
	Result<IncomingSession> update(Long sId, Map body) {
		IncomingSession sess1 = IncomingSession.get(sId)
		if (sess1) {
			updateFields(sess1, body)
		}
		else {
			resultFactory.failWithCodeAndStatus("sessionService.update.notFound",
				ResultStatus.NOT_FOUND, [sId])
		}
	}

	// Helpers
	// -------

	protected Result<IncomingSession> updateFields(IncomingSession s1, Map body) {
		if (body.isSubscribedToText != null) {
			s1.isSubscribedToText = TypeConversionUtils.to(Boolean, body.isSubscribedToText)
		}
		if (body.isSubscribedToCall != null) {
			s1.isSubscribedToCall = TypeConversionUtils.to(Boolean, body.isSubscribedToCall)
		}

		if (s1.save()) {
			resultFactory.success(s1)
		}
		else { resultFactory.failWithValidationErrors(s1.errors) }
	}
}
