package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import org.hibernate.sql.JoinType
import org.joda.time.DateTime
import org.textup.*
import org.textup.override.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@Log4j
class IndividualPhoneRecordWrappers {

    @GrailsTypeChecked
    static Result<IndividualPhoneRecordWrapper> tryCreate(Phone p1) {
        IndividualPhoneRecord.tryCreate(p1).then { IndividualPhoneRecord ipr1 ->
            IOCUtils.resultFactory.success(ipr1.toWrapper(), ResultStatus.CREATED)
        }
    }

    @GrailsTypeChecked
    static Result<IndividualPhoneRecordWrapper> mustFindForId(Long iprId) {
        PhoneRecord pr1 = PhoneRecord.get(iprId)
        PhoneRecordWrapper w1 = pr1?.toWrapper()
        if (w1 instanceof IndividualPhoneRecordWrapper) {
            IOCUtils.resultFactory.success(w1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("individualPhoneRecordWrappers.notFound",
                ResultStatus.NOT_FOUND, [iprId])
        }
    }

    static DetachedJoinableCriteria<PhoneRecord> buildForPhoneIdWithOptions(Long phoneId,
        String query = null, Collection<PhoneRecordStatus> statuses = PhoneRecordStatus.VISIBLE_STATUSES,
        boolean onlyShared = false) {

        DetachedJoinableCriteria<PhoneRecord> criteria = buildActiveBase(query, statuses)
            .build { eq("phone.id", phoneId) }
        onlyShared ? criteria.build { isNotNull("shareSource") } : criteria // inner join
    }

    static DetachedJoinableCriteria<PhoneRecord> buildForSharedByIdWithOptions(Long sharedById,
        String query = null, Collection<PhoneRecordStatus> statuses = PhoneRecordStatus.VISIBLE_STATUSES) {

        buildActiveBase(query, statuses).build {
            shareSource { eq("phone.id", sharedById) }
        }
    }

    static Closure<List<IndividualPhoneRecordWrapper>> listAction(DetachedJoinableCriteria<PhoneRecord> criteria) {
        return { Map opts ->
            if (criteria) {
                criteria.build(forSort())
                    .list(opts)
                    *.toWrapper()
                    .findAll { it instanceof IndividualPhoneRecordWrapper }
            }
            else { [] }
        }
    }

    @GrailsTypeChecked
    static Result<List<IndividualPhoneRecordWrapper>> tryFindOrCreateEveryByPhoneAndNumbers(Phone p1,
        List<? extends BasePhoneNumber> bNums, boolean createIfAbsent) {
        // step 1: create any missing contacts
        IndividualPhoneRecords.tryFindOrCreateNumToObjByPhoneAndNumbers(p1, bNums, createIfAbsent)
            .then { Map<PhoneNumber, List<IndividualPhoneRecord>> numToPhoneRecs ->
                List<IndividualPhoneRecord> iprs = CollectionUtils.mergeUnique(numToPhoneRecs.values())
                // step 2: find shared contacts (excludes tags)
                List<PhoneRecord> sprs = PhoneRecords
                    .buildActiveForShareSourceIds(iprs*.id)
                    .list()
                // step 3: merge all together, removing duplicates
                Collection<IndividualPhoneRecordWrapper> wraps = CollectionUtils
                    .mergeUnique([sprs*.toWrapper(), iprs*.toWrapper()])
                    .findAll { it instanceof IndividualPhoneRecordWrapper }
                IOCUtils.resultFactory.success(wraps)
            }
    }

    // Helpers
    // -------

    static DetachedJoinableCriteria<PhoneRecord> buildActiveBase(String query,
        Collection<PhoneRecordStatus> statuses) {

        new DetachedJoinableCriteria(PhoneRecord)
            .build { ne("class", GroupPhoneRecord) } // only owned or shared individuals
            .build(forStatuses(statuses))
            .build(forQuery(query))
            .build(PhoneRecords.forActive())
    }

    protected static Closure forStatuses(Collection<PhoneRecordStatus> statuses) {
        return { CriteriaUtils.inList(delegate, "status", statuses) }
    }

    protected static Closure forQuery(String query) {
        String formattedQuery = StringUtils.toQuery(query)
        String possibleNum = StringUtils.cleanPhoneNumber(query)
        String numberQuery = StringUtils.toQuery(possibleNum)
        return {
            if (formattedQuery) {
                Collection<Long> searchIds = new DetachedCriteria(IndividualPhoneRecord)
                    .build {
                        or {
                            ilike("name", formattedQuery)
                            if (possibleNum) {
                                numbers { ilike("number", numberQuery) }
                            }
                        }
                    }
                    .build(CriteriaUtils.returnsId())
                    .list()
                or {
                    CriteriaUtils.inList(delegate, "id", searchIds)
                    CriteriaUtils.inList(delegate, "shareSource.id", searchIds)
                }
            }
        }
    }

    // For why sorting is separate, see `RecordItems.forSort`
    protected static Closure forSort() {
        return {
            order("status", "desc") // unread first then active
            // `DetachedCriteria` doesn't fully support `createAlias` so we have to
            // override and create our own. Also, without an alias, it seems like order by
            // associations doesn't work.
            createAliasWithJoin("record", "r1", JoinType.INNER_JOIN)
            order("r1.lastRecordActivity", "desc") // more recent first
        }
    }
}
