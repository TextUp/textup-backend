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

    @Transactional(readOnly=true)
    @Override
    void index() {
        TypeMap body = TypeMap.create(params)
        Collection<PhoneRecordStatus> statuses = body
            .toEnumList(PhoneRecordStatus, "status[]", PhoneRecordStatus.VISIBLE_STATUSES)
        if (body.tagId) {
            listForTag(statuses, body)
        }
        else { listForIds(statuses, body) }
    }

    @Transactional(readOnly=true)
    @Override
    void show() {
        Long id = params.long("id")
        doShow({ PhoneRecords.isAllowed(id) }, { IndividualPhoneRecordsWrappers.mustFindForId(id) })
    }

    @Override
    void save() {
        doSave(MarshallerUtils.KEY_CONTACT, request, contactService) { TypeMap body ->
            ControllerUtils.tryGetPhoneId(body.long("teamId"))
        }
    }

    @OptimisticLockingRetry
    @Override
    void update() {
        doUpdate(MarshallerUtils.KEY_CONTACT, request, contactService) { TypeMap body ->
            PhoneRecords.isAllowed(params.long("id"))
        }
    }

    @Override
    void delete() {
        doDelete(contactService) { PhoneRecords.isAllowed(params.long("id")) }
    }

    // Helpers
    // -------

    protected void listForTag(Collection<PhoneRecordStatus> statuses, TypeMap data) {
        Long gprId = data.long("tagId")
        PhoneRecords.isAllowed(gprId)
            .then { GroupPhoneRecords.mustFindForId(gprId) }
            .ifFail { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { GroupPhoneRecord gpr1 ->
                Collection<PhoneRecord> prs = ct1.getMembersByStatus(statuses)
                respondWithMany({ prs.size() },
                    { prs*.toWrapper() },
                    data,
                    MarshallerUtils.KEY_CONTACT)
            }
    }

    protected void listForIds(Collection<PhoneRecordStatus> statuses, TypeMap data) {
        ControllerUtils.tryGetPhoneId(data.long("teamId"))
            .ifFail { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { Long pId ->
                DetachedCriteria<PhoneRecord> criteria = buildCriteria(statuses, data)
                respondWithMany(CriteriaUtils.countAction(criteria),
                    IndividualPhoneRecordsWrappers.listAction(criteria),
                    data,
                    MarshallerUtils.KEY_CONTACT)
            }
    }

    protected DetachedCriteria<PhoneRecord> buildCriteria(Collection<PhoneRecordStatus> statuses,
        TypeMap data) {

        DetachedCriteria<PhoneRecord> criteria
        String searchVal = data.string("search")
        if (data.shareStatus == "sharedByMe") {
            criteria = IndividualPhoneRecordsWrappers
                .forSharedByIdWithOptions(pId, searchVal, statuses)
        }
        else {
            boolean onlyShared = (data.shareStatus == "sharedWithMe")
            criteria = IndividualPhoneRecordsWrappers
                .forPhoneIdWithOptions(pId, searchVal, statuses, onlyShared)
        }
        // further restrict by ids if provided
        if (body.list("ids[]")) {
            criteria = criteria.build(PhoneRecords.forIds(data.typedList(Long, "ids[]")))
        }
        criteria
    }
}
