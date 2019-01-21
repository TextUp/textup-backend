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
class AnnouncementController extends BaseController {

    AnnouncementService announcementService
    PhoneCache phoneCache

    @Transactional(readOnly=true)
    @Override
    void index() {
        ControllerUtils.tryGetPhoneOwner(params.long("teamId"))
            .then { Tuple<Long, PhoneOwnershipType> processed ->
                Tuple.split(processed) { Long ownerId, PhoneOwnershipType type ->
                    phoneCache.mustFindPhoneIdForOwner(ownerId, type)
                }
            }
            .then { Long pId ->
                respondWithCriteria(FeaturedAnnouncements.buildActiveForPhoneId(pId),
                    params,
                    FeaturedAnnouncements.forSort(),
                    KEY)
            }
            .ifFail { Result<?> failRes -> respondWithResult(failRes, KEY) }
    }

    @Transactional(readOnly=true)
    @Override
    void show() {
        doShow(params.long("id"),
            { Long id -> FeaturedAnnouncements.isAllowed(id) },
            { Long id -> FeaturedAnnouncements.mustFindForId(id) })
        // TODO
        // Long aId =  params.long("id")
        // FeaturedAnnouncements.isAllowed(aId)
        //     .then { FeaturedAnnouncements.mustFindForId(aId) }
        //     .anyEnd { Result<?> res -> respondWithResult(res, KEY) }
    }

    @Override
    void save() {
        doSave(MarshallerUtils.KEY_ANNOUNCEMENT, request, announcementService) { TypeMap body ->
            ControllerUtils.tryGetPhoneId(body.long("teamId"))
        }
        // // TODO
        // RequestUtils.tryGetJsonBody(request, KEY)
        //     .then { TypeMap body ->
        //         ControllerUtils.tryGetPhoneOwner(body.long("teamId")).curry(body)
        //     }
        //     .then { TypeMap body, Tuple<Long, PhoneOwnershipType> processed ->
        //         Tuple.split(processed) { Long ownerId, PhoneOwnershipType type ->
        //             announcementService.create(ownerId, type, body)
        //         }
        //     }
        //     .anyEnd { Result<?> res -> respondWithResult(res, KEY) }
    }

    @Override
    void update() {
        doUpdate(MarshallerUtils.KEY_ANNOUNCEMENT, request, announcementService) { TypeMap body ->
            FeaturedAnnouncements.isAllowed(params.long("id"))
        }
        // // TODO
        // Long aId = params.long("id")
        // RequestUtils.tryGetJsonBody(request, KEY)
        //     .then { TypeMap body -> FeaturedAnnouncements.isAllowed(aId).curry(body) }
        //     .then { TypeMap body -> announcementService.update(aId, body) }
        //     .anyEnd { Result<?> res -> respondWithResult(res, KEY) }
    }
}
