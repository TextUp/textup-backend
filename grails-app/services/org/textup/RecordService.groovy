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
class RecordService implements ManagesDomain.Creater<List<? extends RecordItem>>, ManagesDomain.Updater<RecordNote>, ManagesDomain.Deleter {

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
    Result<RecordNote> tryUpdate(Long noteId, TypeMap body) {
        Future<?> future
        RecordNotes.mustFindModifiableForId(noteId)
            .then { RecordNote rNote1 -> AuthUtils.tryGetActiveAuthUser().curry(rNote1) }
            .then { RecordNote rNote1, Staff authUser ->
                trySetNoteFields(rNote1, body, Author.create(authUser))
            }
            .then { RecordNote rNote1 ->
                mediaService.tryCreateOrUpdate(rNote1, body).curry(rNote1)
            }
            .then { RecordNote rNote1, Future<Result<?>> fut1 ->
                future = fut1
                TypeMap lInfo = body.typeMapNoNull("location")
                locationService.tryUpdate(rNote1.location, lInfo).curry(rNote1)
            }
            .then { RecordNote rNote1 -> rNote1.tryCreateRevision() }
            .then { RecordNote rNote1 -> DomainUtils.trySave(rNote1) }
            .ifFailAndPreserveError { future?.cancel(true) }
    }

    @RollbackOnResultFailure
    Result<Void> tryDelete(Long noteId) {
        RecordNotes.mustFindModifiableForId(noteId)
            .then { RecordNote rNote1 ->
                rNote1.isDeleted = true
                DomainUtils.trySave(rNote1)
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
                TempRecordItem.tryCreate(body.string("contents"), mInfo, null).curry(r1)
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
                Location loc1 = locationService.tryCreate(body.typeMapNoNull("location")).payload
                TempRecordItem.tryCreate(body.string("contents"), mInfo, loc1).curry(w1)
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

    protected Result<RecordNote> trySetNoteFields(RecordNote rNote1, TypeMap body, Author author1) {
        rNote1.with {
            author = author1
            if (body.contents != null) noteContents = body.string("contents")
            if (body.boolean("isDeleted") != null) isDeleted = body.boolean("isDeleted")
            if (body.after) {
                whenCreated = RecordUtils.adjustPosition(rNote1.record.id, body.dateTime("after"))
            }
        }
        DomainUtils.trySave(rNote1)
    }
}
