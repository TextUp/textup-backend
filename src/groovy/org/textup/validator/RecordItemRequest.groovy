package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
@Validateable
class RecordItemRequest implements CanValidate {

    private static final String DEFAULT_START = "beginning"
    private static final String DEFAULT_END = "end"

    Phone mutablePhone // when shared, this is the mutable, not original, phone
    Collection<Class<? extends RecordItem>> types
    DateTime start
    DateTime end
    boolean groupByEntity = false
    Collection<? extends PhoneRecordWrapper> wrappers

    static constraints = {
        types nullable: true
        start nullable: true
        end nullable: true
        wrappers validator: { Collection<PhoneRecordWrapper> val, RecordItemRequest obj ->
            if (val) {
                if (val.any { !it?.permissions?.canView() }) {
                    return ["someNoPermissions"]
                }
                Collection<Long> pIds = WrapperUtils.mutablePhoneIdsIgnoreFails(val)
                if (pIds.any { Long id -> id != obj.mutablePhone?.id }) {
                    return ["foreign"]
                }
            }
        }
    }

    static Result<RecordItemRequest> tryCreate(Phone p1, Collection<PhoneRecordWrapper> wrappers,
        boolean isGrouped) {

        RecordItemRequest iReq1 = new RecordItemRequest(mutablePhone: p1,
            wrappers: wrappers,
            groupByEntity: isGrouped)
        DomainUtils.tryValidate(iReq1, ResultStatus.CREATED)
    }

    // Methods
    // -------

    List<RecordItemRequestSection> buildSections(Map params = null) {
        // when exporting, we want the oldest records first instead of most recent first
        DetachedCriteria<RecordItem> criteria = getCriteria()
        Integer total = criteria.count() as Integer
        List<RecordItem> rItems = criteria.build(RecordItems.forSort(false))
            .list(ControllerUtils.buildPagination(params, total))
        // group by entity only makes sense if we have entities and haven't fallen back
        // to getting record items for the phone overall
        if (!wrappers.isEmpty() && groupByEntity) {
            RecordUtils.buildSectionsByEntity(rItems, wrappers)
        }
        else {
            RecordItemRequestSection section1 = RecordUtils
                .buildSingleSection(mutablePhone, rItems, wrappers)
            section1 ? [section1] : []
        }
    }

    String buildFormattedStart(String tz) {
        start ?
            JodaUtils.FILE_TIMESTAMP_FORMAT.print(JodaUtils.toDateTimeWithZone(start, tz)) :
            DEFAULT_START
    }

    String buildFormattedEnd(String tz) {
        end ?
            JodaUtils.FILE_TIMESTAMP_FORMAT.print(JodaUtils.toDateTimeWithZone(end, tz)) :
            DEFAULT_END
    }

    // Properties
    // ----------

    DetachedCriteria<RecordItem> getCriteria() {
        wrappers.isEmpty()
            ? RecordItems.buildForPhoneIdWithOptions(mutablePhone?.id, start, end, types)
            : RecordItems.buildForRecordIdsWithOptions(WrapperUtils.recordIdsIgnoreFails(wrappers),
                start, end, types)
    }
}
