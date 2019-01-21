package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime
import org.textup.*
import org.textup.util.*

@EqualsAndHashCode
@GrailsTypeChecked
@Validateable
class RecordItemRequest implements Validateable {

    Phone phone
    Collection<Class<? extends RecordItem>> types
    DateTime start
    DateTime end
    boolean groupByEntity = false
    Collection<PhoneRecordWrapper> wrappers

    static constraints = {
        types nullable: true
        start nullable: true
        end nullable: true
        wrappers validator: { Collection<PhoneRecordWrapper> val, RecordItemRequest obj ->
            if (val) {
                if (val.any { !it?.permission?.canView() }) {
                    return ["someNoPermissions"]
                }
                Collection<Long> phoneIds = ResultGroup
                    .collect(val) { PhoneRecordWrapper w1 -> w1.tryGetReadOnlyPhone() }
                    .payload*.id
                if (phoneIds.any { Long id -> id != obj.phone?.id }) {
                    return ["foreign"]
                }
            }
        }
    }

    static Result<RecordItemRequest> tryCreate(Phone p1, Collection<PhoneRecordWrapper> wrappers,
        boolean isGrouped) {

        RecordItemRequest iReq1 = new RecordItemRequest(phone: p1,
            wrappers: wrappers,
            groupByEntity: isGrouped)
        DomainUtils.tryValidate(iReq1, ResultStatus.CREATED)
    }

    // Methods
    // -------

    DetachedCriteria<RecordItem> getCriteria() {
        wrappers.isEmpty()
            ? RecordItems.buildForPhoneIdWithOptions(phone?.id, start, end, types)
            : RecordItems.buildForRecordIdsWithOptions(getRecordIds(), start, end, types)
    }

    List<RecordItemRequestSection> getSections(TypeMap params = null) {
        // when exporting, we want the oldest records first instead of most recent first
        DetachedCriteria<RecordItem> criteria = getCriteria()
        List<RecordItem> rItems = criteria.build(RecordItems.forSort(false))
            .list(ControllerUtils.buildPagination(params, criteria.call()))
        // group by entity only makes sense if we have entities and haven't fallen back
        // to getting record items for the phone overall
        if (!wrappers.isEmpty() && groupByEntity) {
            RecordUtils.buildSectionsByEntity(wrappers, rItems)
        }
        else {
            RecordItemRequestSection section1 = RecordUtils.buildSingleSection(p1, wrappers, rItems)
            section1 ? [section1] : []
        }
    }

    // Helpers
    // -------

    protected Collection<Long> getRecordIds() {
        ResultGroup.collect(wrappers) { PhoneRecordWrapper w1 -> w1.tryGetReadOnlyRecord() }
            .logFail("getRecordIds")
            .payload*.id
    }
}
