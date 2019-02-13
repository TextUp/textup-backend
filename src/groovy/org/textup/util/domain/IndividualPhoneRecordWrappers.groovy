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

        buildActiveBase(query, statuses).build { eq("ssource1.phone.id", sharedById) }
    }

    static Closure<List<IndividualPhoneRecordWrapper>> listAction(DetachedCriteria<PhoneRecord> criteria) {
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

    // For hasMany left join, see: https://stackoverflow.com/a/45193881
    protected static Closure forQuery(String query) {
        String formattedQuery = StringUtils.toQuery(query)
        PhoneNumber pNum1 = PhoneNumber.create(query)
        String numberQuery = pNum1.validate() ? StringUtils.toQuery(pNum1.number) : null
        return {
            // need to use createAlias because this property is not on the superclass
            createAliasWithJoin("numbers", "indnumbers1", JoinType.LEFT_OUTER_JOIN)
            createAliasWithJoin("shareSource", "ssource1", JoinType.LEFT_OUTER_JOIN)
            createAliasWithJoin("ssource1.numbers", "ssourcenums1", JoinType.LEFT_OUTER_JOIN)
            if (formattedQuery) {
                or {
                    ilike("name", formattedQuery)
                    ilike("ssource1.name", formattedQuery)
                    // don't include the numbersQuery if null or else will match all
                    if (numberQuery) {
                        ilike("indnumbers1.number", numberQuery)
                        ilike("ssourcenums1.number", numberQuery)
                    }
                }
            }
        }
    }

    // For why sorting is separate, see `RecordItems.forSort`
    protected static Closure forSort() {
        return {
            order("status", "desc") // unread first then active
            record {
                order("lastRecordActivity", "desc") // more recent first
            }
        }
    }
}
