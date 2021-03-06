package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.Future
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.annotation.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class RecordService implements ManagesDomain.Creater<List<? extends RecordItem>>, ManagesDomain.Updater<? extends RecordItem>, ManagesDomain.Deleter {

    CallService callService
    LocationService locationService
    MediaService mediaService
    OutgoingCallService outgoingCallService
    OutgoingMessageService outgoingMessageService

    @RollbackOnResultFailure
    Result<List<? extends RecordItem>> tryCreate(Long pId, TypeMap body) {
        Future<?> future
        Phones.mustFindActiveForId(pId)
            .then { Phone p1 -> RecordUtils.tryDetermineClass(body).curry(p1) }
            .then { Phone p1, Class<? extends RecordItem> clazz ->
                mediaService.tryCreate(body).curry(p1, clazz)
            }
            .then { Phone p1, Class<? extends RecordItem> clazz,
                Tuple<MediaInfo, Future<Result<?>>> processed ->
                Tuple.split(processed) { MediaInfo mInfo, Future<Result<?>> fut1 ->
                    future = fut1
                    switch(clazz) {
                        case RecordText:
                            createText(p1, body, mInfo, fut1)
                            break
                        case RecordCall:
                            createCall(p1, body)
                            break
                        default:
                            createNote(p1, body, mInfo)
                    }
                }
            }
            .ifFailAndPreserveError { future?.cancel(true) }
    }

    @RollbackOnResultFailure
    Result<? extends RecordItem> tryUpdate(Long itemId, TypeMap body) {
        RecordItems.mustFindModifiableForId(itemId).then { RecordItem rItem1 ->
            if (rItem1.instanceOf(RecordNote)) {
                tryUpdateNote(rItem1 as RecordNote, body)
            }
            else if (rItem1.instanceOf(RecordCall) && body.boolean("endOngoing")) {
                tryEndOngoingCall(rItem1 as RecordCall)
            }
            else { IOCUtils.resultFactory.success(rItem1) }
        }
    }

    @RollbackOnResultFailure
    Result<Void> tryDelete(Long itemId) {
        RecordItems.mustFindModifiableForId(itemId)
            .then { RecordItem rItem1 ->
                rItem1.isDeleted = true
                DomainUtils.trySave(rItem1)
            }
            .then { Result.void() }
    }

    // Helpers
    // -------

    protected Result<List<RecordText>> createText(Phone p1, TypeMap body, MediaInfo mInfo = null,
        Future<Result<?>> future = null) {

        int max = ValidationUtils.MAX_NUM_TEXT_RECIPIENTS
        Recipients.tryCreate(p1, body.typedList(Long, "ids"), body.phoneNumberList("numbers"), max, true)
            .then { Recipients r1 ->
                TempRecordItem.tryCreate(body.trimmedString("contents"), mInfo, null).curry(r1)
            }
            .then { Recipients r1, TempRecordItem temp1 ->
                AuthUtils.tryGetActiveAuthUser().curry(r1, temp1)
            }
            .then { Recipients r1, TempRecordItem temp1, Staff authUser ->
                outgoingMessageService.tryStart(RecordItemType.TEXT, r1, temp1, Author.create(authUser), future)
            }
            .then { Tuple<List<? extends RecordItem>, Future<?>> processed ->
                IOCUtils.resultFactory.success(processed.first, ResultStatus.CREATED)
            }
    }

    protected Result<List<RecordCall>> createCall(Phone p1, TypeMap body) {
        Recipients.tryCreate(p1, body.typedList(Long, "ids"), body.phoneNumberList("numbers"), 1)
            .then { Recipients r1 -> r1.tryGetOneIndividual() }
            .then { IndividualPhoneRecordWrapper w1 -> AuthUtils.tryGetActiveAuthUser().curry(w1) }
            .then { IndividualPhoneRecordWrapper w1, Staff authUser ->
                outgoingCallService.tryStart(authUser.personalNumber, w1, Author.create(authUser))
            }
            .then { RecordCall rCall1 ->
                IOCUtils.resultFactory.success([rCall1], ResultStatus.CREATED)
            }
    }

    protected Result<List<RecordNote>> createNote(Phone p1, TypeMap body, MediaInfo mInfo = null) {
        Recipients.tryCreate(p1, body.typedList(Long, "ids"), body.phoneNumberList("numbers"), 1)
            .then { Recipients r1 -> r1.tryGetOne() }
            .then { PhoneRecordWrapper w1 ->
                TypeMap lInfo = body.typeMapNoNull("location")
                locationService.tryCreateOrUpdateIfPresent(null, lInfo).curry(w1)
            }
            .then { PhoneRecordWrapper w1, Location loc1 = null ->
                TempRecordItem.tryCreate(body.trimmedString("noteContents"), mInfo, loc1).curry(w1)
            }
            .then { PhoneRecordWrapper w1, TempRecordItem temp1 -> w1.tryGetRecord().curry(temp1) }
            .then { TempRecordItem temp1, Record rec1 -> RecordNote.tryCreate(rec1, temp1) }
            .then { RecordNote rNote1 -> AuthUtils.tryGetActiveAuthUser().curry(rNote1) }
            .then { RecordNote rNote1, Staff authUser ->
                trySetNoteFields(rNote1, body, Author.create(authUser))
            }
            .then { RecordNote rNote1 -> DomainUtils.trySave(rNote1) }
            .then { RecordNote rNote1 ->
                IOCUtils.resultFactory.success([rNote1], ResultStatus.CREATED)
            }
    }

    protected Result<? extends RecordItem> tryUpdateNote(RecordNote rNote1, TypeMap body) {
        Future<?> future
        AuthUtils.tryGetActiveAuthUser()
            .then { Staff authUser -> trySetNoteFields(rNote1, body, Author.create(authUser)) }
            .then { mediaService.tryCreateOrUpdate(rNote1, body) }
            .then { Future<Result<?>> fut1 ->
                future = fut1
                TypeMap lInfo = body.typeMapNoNull("location")
                locationService.tryCreateOrUpdateIfPresent(rNote1.location, lInfo)
            }
            .then { Location loc1 = null ->
                rNote1.location = loc1
                DomainUtils.trySave(rNote1)
            }
            .then { rNote1.tryCreateRevision() }
            .then { DomainUtils.trySave(rNote1) }
            .ifFailAndPreserveError { future?.cancel(true) }
    }

    protected Result<RecordNote> trySetNoteFields(RecordNote rNote1, TypeMap body, Author author1) {
        rNote1.with {
            author = author1
            if (body.noteContents != null) noteContents = body.trimmedString("noteContents")
            if (body.boolean("isDeleted") != null) isDeleted = body.boolean("isDeleted")
            if (body.after) {
                whenCreated = RecordUtils.adjustPosition(rNote1.record.id, body.dateTime("after"))
            }
        }
        DomainUtils.trySave(rNote1)
    }

    protected Result<? extends RecordItem> tryEndOngoingCall(RecordCall rCall1) {
        String parentCallId = rCall1.buildParentCallApiId()
        if (parentCallId && rCall1.isStillOngoing()) {
            Phone p1 = PhoneRecords.buildNotExpiredForRecordIds([rCall1.record.id])
                .build(PhoneRecords.forOwnedOnly())
                .build(PhoneRecords.returnsPhone())
                .list(max: 1)[0] as Phone
            callService
                .hangUpImmediately(parentCallId, p1?.customAccountId)
                .then { IOCUtils.resultFactory.success(rCall1) }
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("recordService.tooEarlyOrAlreadyEnded",
                ResultStatus.UNPROCESSABLE_ENTITY, [rCall1.id])
        }
    }
}
