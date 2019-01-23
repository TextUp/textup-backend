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
            type.toClass() :
            IOCUtils.resultFactory.failWithCodeAndStatus("recordUtils.determineClass.unknownType",
                ResultStatus.UNPROCESSABLE_ENTITY)
    }

    static DateTime adjustPosition(Long recordId, DateTime afterTime) {
        RecordItem beforeItem = afterTime ?
            RecordItem.buildForRecordIdsWithOptions([recordId], afterTime).list(max:1)[0] :
            null
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
        else { afterTime }
    }

    static Result<RecordItemRequest> buildRecordItemRequest(Long pId, TypeMap body) {
        Phones.mustFindActiveForId(pId)
            .then { Phone mutPhone1 ->
                Collection<? extends PhoneRecordWrapper> wrappers = PhoneRecords
                    .buildActiveForPhoneIds([pId])
                    .build(PhoneRecords.forIds(body.typedList(Long, "owners[]")))
                    .list()
                    *.toWrapper()
                boolean isGrouped = (body.string("exportFormatType") == EXPORT_TYPE_GROUPED)
                RecordItemRequest.tryCreate(mutPhone1, wrappers, isGrouped)
            }
            .then { RecordItemRequest iReq1 ->
                iReq1.with {
                    types = parseTypes(body.list("types[]"))
                    start = body.dateTime("start")
                    end = body.dateTime("end")
                }
                DomainUtils.tryValidate(iReq1, ResultStatus.CREATED)
            }
    }

    static RecordItemRequestSection buildSingleSection(Phone mutPhone1, List<RecordItem> rItems,
        Collection<? extends PhoneRecordWrapper> wrappers) {

        RecordItemRequestSection
            .tryCreate(mutPhone1.owner.buildName(), mutPhone1.number, rItems, wrappers)
            .logFail("buildSingleSection")
            .payload
    }

    static List<RecordItemRequestSection> buildSectionsByEntity(List<RecordItem> rItems,
        Collection<? extends PhoneRecordWrapper> wrappers) {

        Map<Long, Collection<RecordItem>> recordIdToItems = MapUtils
                .<Long, RecordItem>buildManyObjectsMap(rItems) { RecordItem i1 -> i1.record.id }
        ResultGroup
            .collect(wrappers) { PhoneRecordWrapper w1 ->
                w1.tryGetReadOnlyRecord()
                    .then { ReadOnlyRecord rec1 ->
                        w1.tryGetReadOnlyPhone().curry(rec1) // will show sharedWith phone
                    }
                    .then { ReadOnlyRecord rec1, ReadOnlyPhone p1 ->
                        RecordItemRequestSection.tryCreate(p1.name, p1.number,
                            recordIdToItems[rec1.id], [w1])
                    }
            }
            .logFail("buildSectionsByEntity")
            .payload
    }

    // Helpers
    // -------

    protected static Collection<Class<? extends RecordItem>> parseTypes(Collection<?> rawTypes) {
        HashSet<Class<? extends RecordItem>> types = new HashSet<>()
        rawTypes?.each { Object obj ->
            switch (obj as String) {
                case "text": types << RecordText; break;
                case "call": types << RecordCall; break;
                case "note": types << RecordNote
            }
        }
        types
    }
}
