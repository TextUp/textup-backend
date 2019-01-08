package org.textup

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime

@GrailsTypeChecked
class PhoneRecordWrapper {

    private final PhoneRecord _pRec
    private final PhoneRecordPermissions _perm

    PhoneRecordWrapper(PhoneRecord phoneRecord, PhoneRecordPermissions permissions) {
        _pRec = phoneRecord
        _perm = permissions
    }

    // Methods
    // -------

    Result<? extends PhoneRecordWrapper> trySave() { Utils.trySave(_pRec) }

    // Getters
    // -------

    Result<Long> tryGetId() {
        _perm.canView() ?
            IOCUtils.resultFactory.success(_pRec.id) :
            insufficientPermission()
    }

    Result<DateTime> tryGetLastTouched() {
        _perm.canView() ?
            IOCUtils.resultFactory.success(_pRec.lastTouched) :
            insufficientPermission()
    }

    Result<DateTime> tryGetWhenCreated() {
        _perm.canView() ?
            IOCUtils.resultFactory.success(_pRec.whenCreated) :
            insufficientPermission()
    }

    Result<Phone> tryGetPhone() {
        _perm.canModify() ?
            IOCUtils.resultFactory.success(_pRec.phone) :
            insufficientPermission()
    }

    // create ReadOnlyPhone interface
    Result<ReadOnlyPhone> tryGetReadOnlyPhone() {
        _perm.canView() ?
            IOCUtils.resultFactory.success(_pRec.phone) :
            insufficientPermission()
    }

    // TODO PhoneRecordStatus --> PhoneRecordStatus
    Result<PhoneRecordStatus> tryGetStatus() {
        _perm.canView() ?
            IOCUtils.resultFactory.success(_pRec.status) :
            insufficientPermission()
    }

    Result<Record> tryGetRecord() {
        _perm.canModify() ?
            IOCUtils.resultFactory.success(_pRec.record) :
            insufficientPermission()
    }

    Result<ReadOnlyRecord> tryGetReadOnlyRecord() {
        _perm.canView() ?
            IOCUtils.resultFactory.success(_pRec.record) :
            insufficientPermission()
    }

    Result<String> tryGetSecureName() {
        _perm.canView() ?
            IOCUtils.resultFactory.success(_pRec.secureName) :
            insufficientPermission()
    }

    Result<String> tryGetPublicName() {
        _perm.canView() ?
            IOCUtils.resultFactory.success(_pRec.publicName) :
            insufficientPermission()
    }

    // Setters
    // -------

    Result<Void> trySetStatusIfPresent(PhoneRecordStatus status) {
        if (!status) {
            return IOCUtils.resultFactory.success()
        }
        if (_perm.canView()) { // all sharing relationships have their own status
            _pRec.status = status
            _pRec.lastTouched = DateTime.now()
            IOCUtils.resultFactory.success()
        }
        else { insufficientPermission() }
    }

    // TODO
    Result<Void> trySetLanguageIfPresent(VoiceLanguage lang) {
        if (!lang) {
            return IOCUtils.resultFactory.success()
        }
        if (_perm.canModify()) {
            _pRec.record.language = lang
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
