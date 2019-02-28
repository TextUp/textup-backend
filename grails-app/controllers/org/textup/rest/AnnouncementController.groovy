package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Secured(["ROLE_USER", "ROLE_ADMIN"])
@Transactional
class AnnouncementController extends BaseController {

    AnnouncementService announcementService

    @Override
    def index() {
        ControllerUtils.tryGetPhoneId(params.long("teamId"))
            .ifFailAndPreserveError { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { Long pId ->
                respondWithCriteria(FeaturedAnnouncements.buildActiveForPhoneId(pId),
                    params,
                    FeaturedAnnouncements.forSort(),
                    MarshallerUtils.KEY_ANNOUNCEMENT)
            }
    }

    @Override
    def show() {
        Long id = params.long("id")
        doShow({ FeaturedAnnouncements.isAllowed(id) }, { FeaturedAnnouncements.mustFindForId(id) })
    }

    @Override
    def save() {
        doSave(MarshallerUtils.KEY_ANNOUNCEMENT, request, announcementService) {
            ControllerUtils.tryGetPhoneId(params.long("teamId"))
        }
    }

    @Override
    def update() {
        doUpdate(MarshallerUtils.KEY_ANNOUNCEMENT, request, announcementService) {
            FeaturedAnnouncements.isAllowed(params.long("id"))
        }
    }
}
