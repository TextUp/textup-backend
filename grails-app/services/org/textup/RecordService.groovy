package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.Future
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.type.*
import org.textup.util.RollbackOnResultFailure
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class RecordService {

    AuthService authService
    MediaService mediaService
    OutgoingMessageService outgoingMessageService
    ResultFactory resultFactory

    // Create
    // ------

    ResultGroup<RecordItem> create(Long phoneId, TypeConvertingMap body) {
        Phone p1 = Phone.get(phoneId)
        if (!p1) {
            return resultFactory.failWithCodeAndStatus("recordService.create.noPhone",
                ResultStatus.UNPROCESSABLE_ENTITY).toGroup()
        }
        Staff authUser = authService.loggedInAndActive
        if (!p1.owner.all.any { Staff s2 -> s2.id == authUser.id }) {
            return resultFactory.failWithCodeAndStatus("phone.notOwner", ResultStatus.FORBIDDEN).toGroup()
        }
        Result<Class<RecordItem>> res = RecordUtils.determineClass(body)
        if (!res.success) { return res.toGroup() }
        switch(res.payload) {
            case RecordText: createText(p1, body); break;
            case RecordCall: createCall(p1, body).toGroup(); break;
            default: createNote(p1, body).toGroup() // RecordNote
        }
    }

    // Don't roll back because this creates a ResultGroup of many individual Results.
    // We don't want to throw away the results that actually successfuly completed
    protected ResultGroup<RecordItem> createText(Phone p1, TypeConvertingMap body) {
        // step 1: if needed, build media with placeholder assets
        Tuple<MediaInfo, Future<Result<MediaInfo>>> mediaTuple
        if (mediaService.hasMediaActions(body)) {
            Result<Tuple<MediaInfo, Future<Result<MediaInfo>>>> mediaRes = mediaService
                .tryProcess(new MediaInfo(), body)
            if (mediaRes.success) {
                mediaTuple = mediaRes.payload
            }
            else { return mediaRes.toGroup() }
        }
        // step 2: validating receipients
        Result<OutgoingMessage> msgRes = RecordUtils
            .buildOutgoingMessageTarget(p1, body, mediaTuple?.first)
        if (!msgRes.success) { return msgRes.toGroup() }
        // step 3: return new domain objects and continue processing media separately
        Staff authUser = authService.loggedInAndActive
        outgoingMessageService
            .processMessage(p1, msgRes.payload, authUser, mediaTuple?.second)
            .first
    }

    @RollbackOnResultFailure
    protected Result<RecordItem> createCall(Phone p1, TypeConvertingMap body) {
        RecordUtils.buildOutgoingCallTarget(p1, body).then { Contactable cont1 ->
            outgoingMessageService.startBridgeCall(p1, cont1, authService.loggedInAndActive)
        }
    }

    @RollbackOnResultFailure
    protected Result<RecordItem> createNote(Phone p1, TypeConvertingMap body) {
        RecordUtils.buildNoteTarget(p1, body).then { Record rec1 ->
            mergeNote(new RecordNote(record: rec1), body, ResultStatus.CREATED)
        }
    }
    protected Result<RecordNote> mergeNote(RecordNote note1, TypeConvertingMap body,
        ResultStatus status = ResultStatus.OK) {

        Future <?> future
        // need to add media object to note BEFORE we create the validator because we need
        // the validator object to take the media object into account when determining if the
        // note has complete information
        Result<RecordNote> res = mediaService.tryProcess(note1, body)
            .then { Tuple<WithMedia, Future<Result<MediaInfo>>> processed ->
                RecordNote note2 = processed.first as RecordNote
                future = processed.second
                TempRecordNote tempNote = new TempRecordNote(info: body,
                    note: note2,
                    after: body.after ? DateTimeUtils.toDateTimeWithZone(body.after) : null)
                tempNote.toNote(authService.loggedInAndActive.toAuthor())
            }
            .then { RecordNote note2 -> note2.tryCreateRevision() }
            .then { RecordNote note2 -> resultFactory.success(note2, status) }
        if (!res.success && future) { future.cancel(true) }
        res
    }

    // Update note
    // -----------

    @RollbackOnResultFailure
    Result<RecordItem> update(Long noteId, TypeConvertingMap body) {
        RecordNote note1 = RecordNote.get(noteId)
        if (!note1) {
            return resultFactory.failWithCodeAndStatus("recordService.update.notFound",
                ResultStatus.NOT_FOUND, [noteId])
        }
        if (note1.isReadOnly) {
            return resultFactory.failWithCodeAndStatus("recordService.update.readOnly",
                ResultStatus.FORBIDDEN, [noteId])
        }
        mergeNote(note1, body)
    }

    // Delete note
    // -----------

    @RollbackOnResultFailure
    Result<Void> delete(Long noteId) {
        RecordNote note1 = RecordNote.get(noteId)
        if (!note1) {
            return resultFactory.failWithCodeAndStatus("recordService.delete.notFound",
                ResultStatus.NOT_FOUND, [noteId])
        }
        if (note1.isReadOnly) {
            return resultFactory.failWithCodeAndStatus("recordService.delete.readOnly",
                ResultStatus.FORBIDDEN, [noteId])
        }
        note1.isDeleted = true
        if (note1.save()) {
            resultFactory.success()
        }
        else { resultFactory.failWithValidationErrors(note1.errors) }
    }
}
