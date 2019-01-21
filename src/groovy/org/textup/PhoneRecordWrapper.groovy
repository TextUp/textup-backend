package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime
import org.textup.interface.*

@EqualsAndHashCode
@GrailsTypeChecked
class PhoneRecordWrapper implements Saveable<PhoneRecordWrapper> {

    private final PhoneRecord phoneRecord
    final PhoneRecordPermissions permissions

    PhoneRecordWrapper(PhoneRecord pr1, PhoneRecordPermissions permissions) {
        phoneRecord = pr1
        permissions = permissions
    }

    // Methods
    // -------

    PhoneWrapper save() { phoneRecord.save() ? this : null }

    Errors getErrors() { phoneRecord.errors }

    boolean validate() { phoneRecord.validate() }

    Result<? extends PhoneRecord> tryUnwrap() {
        permissions.isOwner() ?
            IOCUtils.resultFactory.success(phoneRecord) :
            insufficientPermission()
    }

    // Getters
    // -------

    Long getId() { phoneRecord.id }

    Result<DateTime> tryGetLastTouched() {
        permissions.canView() ?
            IOCUtils.resultFactory.success(phoneRecord.lastTouched) :
            insufficientPermission()
    }

    Result<DateTime> tryGetWhenCreated() {
        permissions.canView() ?
            IOCUtils.resultFactory.success(phoneRecord.whenCreated) :
            insufficientPermission()
    }

    Result<Phone> tryGetPhone() {
        permissions.canModify() ?
            IOCUtils.resultFactory.success(phoneRecord.phone) :
            insufficientPermission()
    }

    // TODO do we need this?
    // create ReadOnlyPhone interface
    Result<ReadOnlyPhone> tryGetReadOnlyPhone() {
        permissions.canView() ?
            IOCUtils.resultFactory.success(phoneRecord.phone) :
            insufficientPermission()
    }

    // TODO ContactStatus --> PhoneRecordStatus
    Result<PhoneRecordStatus> tryGetStatus() {
        permissions.canView() ?
            IOCUtils.resultFactory.success(phoneRecord.status) :
            insufficientPermission()
    }

    Result<Record> tryGetRecord() {
        permissions.canModify() ?
            IOCUtils.resultFactory.success(phoneRecord.record) :
            insufficientPermission()
    }

    Result<ReadOnlyRecord> tryGetReadOnlyRecord() {
        permissions.canView() ?
            IOCUtils.resultFactory.success(phoneRecord.record) :
            insufficientPermission()
    }

    Result<String> tryGetSecureName() {
        permissions.canView() ?
            IOCUtils.resultFactory.success(phoneRecord.secureName) :
            insufficientPermission()
    }

    Result<String> tryGetPublicName() {
        permissions.canView() ?
            IOCUtils.resultFactory.success(phoneRecord.publicName) :
            insufficientPermission()
    }

    // Setters
    // -------

    Result<Void> trySetStatusIfPresent(PhoneRecordStatus status) {
        if (!status) {
            return IOCUtils.resultFactory.success()
        }
        if (permissions.canView()) { // all sharing relationships have their own status
            phoneRecord.status = status
            phoneRecord.lastTouched = DateTime.now()
            IOCUtils.resultFactory.success()
        }
        else { insufficientPermission() }
    }

    Result<Void> trySetStatusIfNotBlocked(PhoneRecordStatus status) {
        if (permissions.canView()) { // all sharing relationships have their own status
            if (phoneRecord.status != PhoneRecordStatus.BLOCKED) {
                phoneRecord.status = status // do not update lastTouched timestamp
            }
            IOCUtils.resultFactory.success()
        }
        else { insufficientPermission() }
    }

    Result<Void> trySetLanguageIfPresent(VoiceLanguage lang) {
        if (!lang) {
            return IOCUtils.resultFactory.success()
        }
        if (permissions.canModify()) {
            phoneRecord.record.language = lang
            IOCUtils.resultFactory.success()
        }
        else { insufficientPermission() }
    }

    // Helpers
    // -------

    protected Result<?> insufficientPermission() { // TODO change
        IOCUtils.resultFactory.failWithCodeAndStatus("sharedContact.insufficientPermission",
                ResultStatus.FORBIDDEN)
    }
}
