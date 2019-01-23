package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
@Secured(Roles.USER_ROLES)
@Transactional
class AnnouncementController extends BaseController {

    AnnouncementService announcementService

    @Override
    void index() {
        ControllerUtils.tryGetPhoneId(params.long("teamId"))
            .ifFail { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { Long pId ->
                respondWithCriteria(FeaturedAnnouncements.buildActiveForPhoneId(pId),
                    params,
                    FeaturedAnnouncements.forSort(),
                    MarshallerUtils.KEY_ANNOUNCEMENT)
            }
    }

    @Override
    void show() {
        Long id = params.long("id")
        doShow({ FeaturedAnnouncements.isAllowed(id) }, { FeaturedAnnouncements.mustFindForId(id) })
    }

    @Override
    void save() {
        doSave(MarshallerUtils.KEY_ANNOUNCEMENT, request, announcementService) { TypeMap body ->
            ControllerUtils.tryGetPhoneId(body.long("teamId"))
        }
    }

    @Override
    void update() {
        doUpdate(MarshallerUtils.KEY_ANNOUNCEMENT, request, announcementService) { TypeMap body ->
            FeaturedAnnouncements.isAllowed(params.long("id"))
        }
    }
}
