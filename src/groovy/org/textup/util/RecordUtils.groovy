package org.textup.util

import grails.compiler.GrailsTypeChecked
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class RecordUtils {

    private static final String EXPORT_TYPE_GROUPED = "groupByEntity"

    static Result<Class<RecordItem>> tryDetermineClass(TypeMap body) {
        RecordItemType type = body.enum(RecordItemType, "type")
        type ?
            IOCUtils.resultFactory.success(type.toClass()) :
            IOCUtils.resultFactory.failWithCodeAndStatus("recordUtils.noClassForUnknownType",
                ResultStatus.UNPROCESSABLE_ENTITY)
    }

    static DateTime adjustPosition(Long recordId, DateTime afterTime) {
        RecordItem beforeItem
        if (afterTime) {
            // the afterTime is usually the whenCreated timestamp of the item we need to be after
            // Therefore, we add 1 millisecond so that the new whenCreated is actually right after
            beforeItem = RecordItems.buildForRecordIdsWithOptions([recordId], afterTime.plusMillis(1))
                .build(RecordItems.forSort(false)) // want older items first
                .list(max: 1)[0]
        }
        if (beforeItem) {
            Long val  = new Duration(afterTime, beforeItem.whenCreated).millis / 2 as Long,
                min = ValidationUtils.MIN_NOTE_SPACING_MILLIS,
                max = ValidationUtils.MAX_NOTE_SPACING_MILLIS
            // # millis to be add should be half the # of millis between time we need to be after
            // and the time that we need to be before (to avoid passing next item)
            // BUT this # must be between the specified lower and upper bounds
            long plusAmount = Utils.inclusiveBound(val, min, max)
            // set note's whenCreated to the DateTime we need to be after plus an offset
            afterTime.plus(plusAmount)
        }
        else {
            // if no item to place this new item before, then this means that we are trying to
            // add an item after the most recent item in this record. Therefore, there's no need
            // to manipulate the whenCreated timestamp and we just return the current time to allow
            // for enough space to insert in additional items in between this item and the earlier
            // one if we want to in the future
            JodaUtils.utcNow()
        }
    }

    static Result<RecordItemRequest> buildRecordItemRequest(Long pId, TypeMap body) {
        Phones.mustFindActiveForId(pId)
            .then { Phone mutPhone1 ->
                Collection<? extends PhoneRecordWrapper> wrappers = PhoneRecords
                    .buildNotExpiredForPhoneIds([pId])
                    .build(PhoneRecords.forIds(body.typedList(Long, "owners[]")))
                    .list()
                    *.toWrapper()
                boolean isGrouped = (body.string("exportFormatType") == EXPORT_TYPE_GROUPED)
                RecordItemRequest.tryCreate(mutPhone1, wrappers, isGrouped)
            }
            .then { RecordItemRequest iReq1 ->
                iReq1.with {
                    types = body.enumList(RecordItemType, "types[]")*.toClass()
                    start = body.dateTime("start")
                    end = body.dateTime("end")
                }
                DomainUtils.tryValidate(iReq1, ResultStatus.CREATED)
            }
    }

    static RecordItemRequestSection buildSingleSection(Phone mutPhone1, List<RecordItem> rItems,
        Collection<? extends PhoneRecordWrapper> wrappers) {

        RecordItemRequestSection
            .tryCreate(mutPhone1?.buildName(), mutPhone1?.number, rItems, wrappers)
            .logFail("buildSingleSection")
            .payload as RecordItemRequestSection
    }

    static List<RecordItemRequestSection> buildSectionsByEntity(List<RecordItem> rItems,
        Collection<? extends PhoneRecordWrapper> wrappers) {

        Map<Long, Collection<RecordItem>> recordIdToItems = MapUtils
                .<Long, RecordItem>buildManyUniqueObjectsMap(rItems) { RecordItem i1 -> i1.record.id }
        ResultGroup
            .collect(wrappers) { PhoneRecordWrapper w1 ->
                w1.tryGetReadOnlyRecord()
                    .then { ReadOnlyRecord rec1 ->
                        // While overall the `RecordItemRequest` is for the mutable phone, show
                        // `shareSource` phone for sharing relationships when in a single section
                        w1.tryGetReadOnlyOriginalPhone().curry(rec1)
                    }
                    .then { ReadOnlyRecord rec1, ReadOnlyPhone p1 ->
                        RecordItemRequestSection.tryCreate(p1.buildName(), p1.number,
                            recordIdToItems[rec1.id], [w1])
                    }
            }
            .logFail("buildSectionsByEntity")
            .payload
    }
}
