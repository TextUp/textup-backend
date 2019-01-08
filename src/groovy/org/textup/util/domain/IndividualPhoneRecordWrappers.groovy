package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import org.hibernate.criterion.CriteriaSpecification
import groovy.util.logging.Log4j

@GrailsTypeChecked
@Log4j
class IndividualPhoneRecordWrappers {

    // TODO move to service?
    static Result<IndividualPhoneRecordWrapper> update(IndividualPhoneRecordWrapper w1,
        Object name, Object note, Object lang, Object status) {

        w1.trySetNameIfPresent(TypeConversionUtils.to(String, name))
            .then {
                w1.trySetNoteIfPresent(TypeConversionUtils.to(String, note))
            }
            .then {
                w1.trySetLanguageIfPresent(TypeConversionUtils.convertEnum(VoiceLanguage, lang))
            }
            .then {
                w1.trySetStatusIfPresent(TypeConversionUtils.convertEnum(PhoneRecordStatus, status))
            }
            .then { w1.trySave() }
    }

    static DetachedCriteria<PhoneRecord> buildForPhoneIdWithOptions(Long phoneId,
        String query = null, Collection<PhoneRecordStatus> statuses = PhoneRecordStatus.VISIBLE_STATUSES,
        boolean onlyShared = false) {

        DetachedCriteria<PhoneRecord> query = buildBase(query, statuses)
            .build { eq("phone.id", phoneId) }
        onlyShared ? query.build { isNotNull("shareSource") } : query // inner join
    }

    static DetachedCriteria<PhoneRecord> buildForSharedByIdWithOptions(Long sharedById,
        String query = null, Collection<PhoneRecordStatus> statuses = PhoneRecordStatus.VISIBLE_STATUSES) {

        buildBase(query, statuses).build { eq("shareSource.phone.id", phoneId) } // inner join
    }

    // For why sorting is separate, see `RecordItems.forSort`
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Closure forSort() {
        return {
            order("status", "desc") // unread first then active
            order("record.lastRecordActivity", "desc") // more recent first
        }
    }

    // Helpers
    // -------

    static DetachedCriteria<PhoneRecord> buildBase(String query,
        Collection<PhoneRecordStatus> statuses) {

        new DetachedCriteria(PhoneRecord)
            .build(forStatuses(phoneId, statuses))
            .build(forQuery(query))
            .build(PhoneRecords.forActive())
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Closure forStatuses(Long phoneId,
        Collection<PhoneRecordStatus> statuses) {

        return { CriteriaUtils.inList(delegate, "status", statuses) }
    }

    // For hasMany left join, see: https://stackoverflow.com/a/45193881
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Closure forQuery(String query) {
        if (!query) {
            return { }
        }
        return {
            // need to use createAlias because this property is not on the superclass
            createAlias("numbers", "indNumbers1", CriteriaSpecification.LEFT_JOIN)
            or {
                // TODO how does same property name for two subclasses work??
                String formattedQuery = StringUtils.toQuery(query)
                ilike("name", formattedQuery)
                shareSource(CriteriaSpecification.LEFT_JOIN) { ilike("name", formattedQuery) }
                // don't include the numbers constraint if not number or else will match all
                String cleanedAsNumber = StringUtils.cleanPhoneNumber(query)
                if (cleanedAsNumber) {
                    String numberQuery = StringUtils.toQuery(cleanedAsNumber)
                    ilike("indNumbers1.number", numberQuery)
                    shareSource(CriteriaSpecification.LEFT_JOIN) {
                        numbers(CriteriaSpecification.LEFT_JOIN) { ilike("number", numberQuery) }
                    }
                }
            }
        }
    }
}
