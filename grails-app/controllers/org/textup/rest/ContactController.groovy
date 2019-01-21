package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Secured(Roles.USER_ROLES)
class ContactController extends BaseController {

    ContactService contactService
    DuplicateService duplicateService

    @Transactional(readOnly=true)
    @Override
    void index() {
        TypeMap body = TypeMap.create(params)
        if (body.list("ids[]")) {
            listForIds(body)
        }
        else if (body.tagId) {
            listForTag(body)
        }
        else { listForPhone(body) }
    }

    @Transactional(readOnly=true)
    @Override
    void show() {
        doShow(params.long("id"),
            { Long id -> PhoneRecords.isAllowed(id) },
            { Long id -> IndividualPhoneRecordsWrappers.mustFindForId(id) })

        // // TODO
        // Long prId = params.long("id")
        // PhoneRecords.isAllowed(prId)
        //     .then { IndividualPhoneRecordsWrappers.mustFindForId(prId) }
        //     .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    @Override
    void save() {
        doSave(MarshallerUtils.KEY_CONTACT, request, contactService) { TypeMap body ->
            ControllerUtils.tryGetPhoneId(body.long("teamId"))
        }

        // tryGetJsonPayload(CLASS, request)
        //     .then { TypeMap body ->
        //         ControllerUtils.tryGetPhoneOwner(body.long("teamId")).curry(body)
        //     }
        //     .then { TypeMap body, Tuple<Long, PhoneOwnershipType> processed ->
        //         Tuple.split(processed) { Long ownerId, PhoneOwnershipType type ->
        //             contactService.create(ownerId, type, body)
        //         }
        //     }
        //     .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    @OptimisticLockingRetry
    @Override
    void update() {
        doUpdate(MarshallerUtils.KEY_ANNOUNCEMENT, request, announcementService) { TypeMap body ->
            FeaturedAnnouncements.isAllowed(params.long("id"))
        }
        // // TODO
        // Long prId = params.long("id")
        // tryGetJsonPayload(CLASS, request)
        //     .then { TypeMap body -> PhoneRecords.isAllowed(prId).curry(body) }
        //     .then { TypeMap body -> contactService.update(prId, body) }
        //     .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    @Override
    void delete() {
        doDelete(contactService) { PhoneRecords.isAllowed(params.long("id")) }

        // TODO
        // Long prId = params.long("id")
        // PhoneRecords.isAllowed(prId)
        //     .then { contactService.delete(prId) }
        //     .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    // Helpers
    // -------

    protected void listForIds(TypeMap body) {
        DetachedCriteria<PhoneRecord> query = IndividualPhoneRecordsWrappers
            .buildForIds(body.typedList(Long, "ids[]"))
        respondWithMany(IndividualPhoneRecordWrapper,
            CriteriaUtils.countAction(query),
            IndividualPhoneRecordsWrappers.listAction(query),
            body)
    }

    protected void listForTag(TypeMap body) {
        Long gprId = body.long("tagId")
        PhoneRecords.isAllowed(gprId)
            .then { GroupPhoneRecords.mustFindForId(gprId) }
            .ifFail { Result<?> failRes -> respondWithResult(CLASS, failRes) }
            .thenEnd { GroupPhoneRecord gpr1 ->
                Collection<PhoneRecord> prs = ct1.getMembersByStatus(body.list("status[]"))
                respondWithMany(IndividualPhoneRecordsWrappers,
                    { prs.size() },
                    { prs*.toWrapper() })
            }
    }

    protected void listForPhone(TypeMap body) {
        ControllerUtils.tryGetPhoneOwner(body.long("teamId"))
            .then { Tuple<Long, PhoneOwnershipType> processed ->
                Tuple.split(processed) { Long ownerId, PhoneOwnershipType type ->
                    phoneCache.mustFindPhoneIdForOwner(ownerId, type)
                }
            }
            .ifFail { Result<?> failRes -> respondWithResult(CLASS, failRes) }
            .thenEnd { Long pId ->
                List<PhoneRecordStatus> statuses = body.toEnumList(PhoneRecordStatus, "status[]",
                    PhoneRecordStatus.VISIBLE_STATUSES)
                String searchVal = body.string("search")
                DetachedCriteria<PhoneRecord> query
                if (body.shareStatus == "sharedByMe") {
                    query = IndividualPhoneRecordsWrappers
                        .forSharedByIdWithOptions(pId, searchVal, statuses)
                }
                else if (body.shareStatus == "sharedWithMe") {
                    query = IndividualPhoneRecordsWrappers
                        .forPhoneIdWithOptions(pId, searchVal, statuses, true)
                }
                else {
                    query = IndividualPhoneRecordsWrappers
                        .forPhoneIdWithOptions(pId, searchVal, statuses)
                }
                respondWithMany(IndividualPhoneRecordWrapper,
                    CriteriaUtils.countAction(query),
                    IndividualPhoneRecordsWrappers.listAction(query),
                    body)
            }
    }
}
