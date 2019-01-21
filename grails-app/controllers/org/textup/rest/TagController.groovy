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
class TagController extends BaseController {

    PhoneCache phoneCache
    TagService tagService

    @Transactional(readOnly=true)
    @Override
    void index() {
        ControllerUtils.tryGetPhoneOwner(body.long("teamId"))
            .then { Tuple<Long, PhoneOwnershipType> processed ->
                Tuple.split(processed) { Long ownerId, PhoneOwnershipType type ->
                    phoneCache.mustFindPhoneIdForOwner(ownerId, type)
                }
            }
            .then { Long pId ->
                respondWithCriteria(CLASS,
                    GroupPhoneRecords.buildForPhoneIdAndOptions(pId),
                    params,
                    false,
                    GroupPhoneRecords.forSort())
            }
            .ifFail { Result<?> failRes -> respondWithResult(CLASS, failRes) }
    }

    @Transactional(readOnly=true)
    @Override
    void show() {
        Long gprId = params.long("id")
        PhoneRecords.isAllowed(gprId)
            .then { GroupPhoneRecords.mustFindForId(gprId) }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    @Override
    void save() {
        tryGetJsonPayload(CLASS, request)
            .then { TypeMap body ->
                ControllerUtils.tryGetPhoneOwner(body.long("teamId")).curry(body)
            }
            .then { TypeMap body, Tuple<Long, PhoneOwnershipType> processed ->
                Tuple.split(processed) { Long ownerId, PhoneOwnershipType type ->
                    tagService.create(ownerId, type, body)
                }
            }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    @Override
    void update() {
        Long gprId = params.long("id")
        tryGetJsonPayload(CLASS, request)
            .then { TypeMap body -> PhoneRecords.isAllowed(gprId).curry(body) }
            .then { TypeMap body -> tagService.update(gprId, body) }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    @Override
    void delete() {
        Long gprId = params.long("id")
        PhoneRecords.isAllowed(gprId)
            .then { tagService.delete(gprId) }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }
}
