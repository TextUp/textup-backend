package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.gorm.DetachedCriteria
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.annotation.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Secured([Roles.USER, Roles.ADMIN])
@Transactional
class ContactController extends BaseController {

    ContactService contactService

    @Override
    void index() {
        TypeMap qParams = TypeMap.create(params)
        Collection<PhoneRecordStatus> statuses = qParams
            .enumList(PhoneRecordStatus, "status[]") ?: PhoneRecordStatus.VISIBLE_STATUSES
        if (qParams.tagId) {
            listForTag(statuses, qParams)
        }
        else { listForIds(statuses, qParams) }
    }

    @Override
    void show() {
        Long id = params.long("id")
        doShow({ PhoneRecords.isAllowed(id) }, { IndividualPhoneRecordWrappers.mustFindForId(id) })
    }

    @Override
    void save() {
        doSave(MarshallerUtils.KEY_CONTACT, request, contactService) {
            ControllerUtils.tryGetPhoneId(params.long("teamId"))
        }
    }

    @OptimisticLockingRetry
    @Override
    void update() {
        doUpdate(MarshallerUtils.KEY_CONTACT, request, contactService) {
            PhoneRecords.isAllowed(params.long("id"))
        }
    }

    @Override
    void delete() {
        doDelete(contactService) { PhoneRecords.isAllowed(params.long("id")) }
    }

    // Helpers
    // -------

    protected void listForTag(Collection<PhoneRecordStatus> statuses, TypeMap qParams) {
        Long gprId = qParams.long("tagId")
        PhoneRecords.isAllowed(gprId)
            .then { GroupPhoneRecords.mustFindForId(gprId) }
            .ifFail { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { GroupPhoneRecord gpr1 ->
                Collection<PhoneRecord> prs = gpr1.getMembersByStatus(statuses)
                respondWithMany({ prs.size() },
                    { prs*.toWrapper() },
                    qParams,
                    MarshallerUtils.KEY_CONTACT)
            }
    }

    protected void listForIds(Collection<PhoneRecordStatus> statuses, TypeMap qParams) {
        ControllerUtils.tryGetPhoneId(qParams.long("teamId"))
            .ifFail { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { Long pId ->
                DetachedCriteria<PhoneRecord> criteria = buildCriteria(pId, statuses, qParams)
                respondWithMany(CriteriaUtils.countAction(criteria),
                    IndividualPhoneRecordWrappers.listAction(criteria),
                    qParams,
                    MarshallerUtils.KEY_CONTACT)
            }
    }

    protected DetachedCriteria<PhoneRecord> buildCriteria(Long pId,
        Collection<PhoneRecordStatus> statuses, TypeMap qParams) {

        DetachedCriteria<PhoneRecord> criteria
        String searchVal = qParams.string("search")
        if (qParams.shareStatus == "sharedByMe") {
            criteria = IndividualPhoneRecordWrappers
                .buildForSharedByIdWithOptions(pId, searchVal, statuses)
        }
        else {
            boolean onlyShared = (qParams.shareStatus == "sharedWithMe")
            criteria = IndividualPhoneRecordWrappers
                .buildForPhoneIdWithOptions(pId, searchVal, statuses, onlyShared)
        }
        // further restrict by ids if provided
        if (qParams.list("ids[]")) {
            criteria = criteria.build(PhoneRecords.forIds(qParams.typedList(Long, "ids[]")))
        }
        criteria
    }
}
