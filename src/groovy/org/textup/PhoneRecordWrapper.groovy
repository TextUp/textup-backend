package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime
import org.springframework.validation.Errors
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*

@EqualsAndHashCode
@GrailsTypeChecked
class PhoneRecordWrapper implements CanSave<PhoneRecordWrapper> {

    private final PhoneRecord phoneRecord
    final PhoneRecordPermissions permissions

    PhoneRecordWrapper(PhoneRecord pr1, PhoneRecordPermissions permissions) {
        phoneRecord = pr1
        permissions = permissions
    }

    // Methods
    // -------

    PhoneRecordWrapper save() { phoneRecord.save() ? this : null }

    Errors getErrors() { phoneRecord.errors }

    boolean validate() { phoneRecord.validate() }

    Result<? extends PhoneRecord> tryUnwrap() {
        permissions.isOwner() ?
            IOCUtils.resultFactory.success(phoneRecord) :
            insufficientPermission()
    }

    boolean isOverridden() { false }

    // Getters
    // -------

    Class getWrappedClass() { phoneRecord.class }

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

    Result<Phone> tryGetMutablePhone() {
        permissions.canModify() ?
            IOCUtils.resultFactory.success(phoneRecord.phone) :
            insufficientPermission()
    }

    Result<ReadOnlyPhone> tryGetReadOnlyMutablePhone() {
        permissions.canView() ?
            IOCUtils.resultFactory.success(phoneRecord.phone) :
            insufficientPermission()
    }

    Result<Phone> tryGetOriginalPhone() {
        permissions.canModify() ?
            IOCUtils.resultFactory.success(phoneRecord.phone) :
            insufficientPermission()
    }

    Result<ReadOnlyPhone> tryGetReadOnlyOriginalPhone() {
        permissions.canView() ?
            IOCUtils.resultFactory.success(phoneRecord.phone) :
            insufficientPermission()
    }

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
            return Result.void()
        }
        if (permissions.canView()) { // all sharing relationships have their own status
            phoneRecord.status = status
            phoneRecord.lastTouched = DateTime.now()
            Result.void()
        }
        else { insufficientPermission() }
    }

    Result<Void> trySetStatusIfNotBlocked(PhoneRecordStatus status) {
        if (permissions.canView()) { // all sharing relationships have their own status
            if (phoneRecord.status != PhoneRecordStatus.BLOCKED) {
                phoneRecord.status = status // do not update lastTouched timestamp
            }
            Result.void()
        }
        else { insufficientPermission() }
    }

    Result<Void> trySetLanguageIfPresent(VoiceLanguage lang) {
        if (!lang) {
            return Result.void()
        }
        if (permissions.canModify()) {
            phoneRecord.record.language = lang
            Result.void()
        }
        else { insufficientPermission() }
    }

    // Helpers
    // -------

    protected Result<?> insufficientPermission() { // TODO msg
        IOCUtils.resultFactory.failWithCodeAndStatus("sharedContact.insufficientPermission",
                ResultStatus.FORBIDDEN)
    }
}
