package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.validator.*

@GrailsTypeChecked
@Secured(Roles.PUBLIC)
@Transactional
class NotifyController extends BaseController {

	NotificationService notificationService

    @Override
    void show() {
        respondWithResult(notificationService.redeem(params.string("id")))
    }
}
