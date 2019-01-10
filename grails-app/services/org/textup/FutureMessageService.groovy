package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.Future
import org.joda.time.DateTime
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class FutureMessageService {

    FutureMessageJobService futureMessageJobService
    MediaService mediaService
    ResultFactory resultFactory
    SocketService socketService

    // Create
    // ------

    Result<FutureMessage> createForContact(Long cId, Map body, String timezone = null) {
        this.create(Contact.get(cId)?.record, body, timezone)
    }
    Result<FutureMessage> createForSharedContact(Long scId, Map body, String timezone = null) {
        SharedContact sc1 = SharedContact.get(scId)
        if (!sc1) {
            return resultFactory.failWithCodeAndStatus(
                "futureMessageService.create.noRecordOrInsufficientPermissions",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        sc1.tryGetRecord().then { Record rec1 -> this.create(rec1, body, timezone) }
    }
    Result<FutureMessage> createForTag(Long ctId, Map body, String timezone = null) {
        this.create(ContactTag.get(ctId)?.record, body, timezone)
    }

    @RollbackOnResultFailure
    protected Result<FutureMessage> create(Record rec, Map body, String timezone = null) {
        if (!rec) {
            return resultFactory.failWithCodeAndStatus(
                "futureMessageService.create.noRecordOrInsufficientPermissions",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        Future<?> future
        SimpleFutureMessage fm0 = new SimpleFutureMessage(record: rec, language: rec.language)
        Result<FutureMessage> res = mediaService.tryProcess(fm0, body)
            .then { Tuple<WithMedia, Future<Result<MediaInfo>>> processed ->
                future = processed.second
                setFromBody(fm0, body, timezone, ResultStatus.CREATED)
            }
        if (!res.success && future) { future.cancel(true) }
        res
    }

    // Update
    // ------

    @RollbackOnResultFailure
    Result<FutureMessage> update(Long fId, Map body, String timezone = null) {
        FutureMessage fMsg = FutureMessage.get(fId)
        if (!fMsg) {
            return resultFactory.failWithCodeAndStatus("futureMessageService.update.notFound",
                ResultStatus.NOT_FOUND, [fId])
        }
        // need to set first to ensure that future message validators take into account
        // the media object too
        Future<?> future
        Result<FutureMessage> res = mediaService.tryProcess(fMsg, body)
            .then { Tuple<WithMedia, Future<Result<MediaInfo>>> processed ->
                future = processed.second
                setFromBody(fMsg, body, timezone)
            }
        if (!res.success && future) { future.cancel(true) }
        res
    }

    // Delete
    // ------

    @RollbackOnResultFailure
    Result<Void> delete(Long fId) {
        FutureMessage fMsg = FutureMessage.get(fId)
        if (!fMsg) {
            return resultFactory.failWithCodeAndStatus("futureMessageService.delete.notFound",
                ResultStatus.NOT_FOUND, [fId])
        }
        futureMessageJobService.unschedule(fMsg).then {
            fMsg.isDone = true
            if (fMsg.save()) {
                resultFactory.success()
            }
            else { resultFactory.failWithValidationErrors(fMsg.errors) }
        }
    }

    // Helper Methods
    // --------------

    protected Result<FutureMessage> setFromBody(FutureMessage fMsg, Map body,
        String timezone = null, ResultStatus status = ResultStatus.OK) {

        fMsg.with {
            if (body.notifySelf != null) notifySelf = body.notifySelf
            if (body.type) {
                type = TypeConversionUtils.convertEnum(FutureMessageType, body.type)
            }
            if (body.message) message = body.message
            // optional properties
            if (body.startDate) {
                startDate = DateTimeUtils.toDateTimeWithZone(body.startDate, timezone)
            }
            if (body.language) {
                language = TypeConversionUtils.convertEnum(VoiceLanguage, body.language)
            }
            // don't wrap endDate setter in if statement because we want to support nulling
            // endDate by omitting it from the passed-in body
            endDate = DateTimeUtils.toDateTimeWithZone(body.endDate, timezone)
        }
        if (fMsg.instanceOf(SimpleFutureMessage)) {
            SimpleFutureMessage sMsg = fMsg as SimpleFutureMessage
            // repeat count is nullable!
            sMsg.repeatCount = TypeConversionUtils.to(Integer, body.repeatCount)
            if (body.repeatIntervalInDays) {
                sMsg.repeatIntervalInDays = TypeConversionUtils.to(Integer, body.repeatIntervalInDays)
            }
        }
        // if timezone is provided, determine if we need to schedule a date to adjust to
        // account for daylight savings time
        if (timezone) {
            fMsg.checkScheduleDaylightSavingsAdjustment(DateTimeUtils.getZoneFromId(timezone))
        }
        // for some reason, calling save here instantly persists the message
        if (fMsg.validate()) {
            if (DomainUtils.isNew(fMsg) || fMsg.shouldReschedule) {
                Result res = futureMessageJobService.schedule(fMsg)
                if (!res.success) {
                    return resultFactory.failWithResultsAndStatus([res], res.status)
                }
            }
            // call save finally here to persist the message
            if (fMsg.save()) {
                socketService.sendFutureMessages([fMsg]) // socketService will refresh trigger
                resultFactory.success(fMsg, status)
            }
            else { resultFactory.failWithValidationErrors(fMsg.errors) }
        }
        else { resultFactory.failWithValidationErrors(fMsg.errors) }
    }
}
