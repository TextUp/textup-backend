package org.textup.util

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime
import org.textup.*
import org.textup.validator.*
import org.textup.type.*

@GrailsTypeChecked
class RecordUtils {

    // TODO remove?
    static List<Class<? extends RecordItem>> parseTypes(Collection<?> rawTypes) {
        if (!rawTypes) {
            return []
        }
        HashSet<Class<? extends RecordItem>> types = new HashSet<>()
        rawTypes.each { Object obj ->
            switch (obj as String) {
                case "text": types << RecordText; break;
                case "call": types << RecordCall; break;
                case "note": types << RecordNote
            }
        }
        new ArrayList<Class<? extends RecordItem>>(types)
    }

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

    static Result<RecordItemRequest> buildRecordItemRequest(Phone p1, TypeMap body,
        boolean groupByEntity) {
        Collection<Class<? extends RecordItem>> types = RecordUtils.parseTypes(body.list("types[]"))
        DateTime start = DateTimeUtils.toDateTimeWithZone(body.since),
            end = DateTimeUtils.toDateTimeWithZone(body.before)
        RecordItemRequest itemRequest = new RecordItemRequest(phone: p1,
            types: types,
            start: start,
            end: end,
            groupByEntity: groupByEntity,
            contacts: new ContactRecipients(phone: p1,
                ids: TypeConversionUtils.allTo(Long, body.list("contactIds[]"))),
            sharedContacts: new SharedContactRecipients(phone: p1,
                ids: TypeConversionUtils.allTo(Long, body.list("sharedContactIds[]"))),
            tags: new ContactTagRecipients(phone: p1,
                ids: TypeConversionUtils.allTo(Long, body.list("tagIds[]"))))
        if (itemRequest.validate()) {
            IOCUtils.resultFactory.success(itemRequest)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(itemRequest.errors) }
    }
}
