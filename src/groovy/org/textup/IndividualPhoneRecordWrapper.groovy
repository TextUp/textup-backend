package org.textup

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime

@GrailsTypeChecked
class IndividualPhoneRecordWrapper extends PhoneRecordWrapper{

    private final IndividualPhoneRecord _pRec
    private final PhoneRecordPermissions _perm

    IndividualPhoneRecordWrapper(IndividualPhoneRecord phoneRecord, PhoneRecordPermissions permissions,
        PhoneRecord overrides = null) {

        super(overrides ?: phoneRecord, permissions)
        _pRec = ipRec
        _perm = permissions
    }

    // Methods
    // -------

    @Override
    Result<? extends IndividualPhoneRecordWrapper> trySave() { Utils.trySave(_pRec) }

    // Getters
    // -------

    Result<Boolean> tryGetIsDeleted() {
        _perm.canView() ?
            IOCUtils.resultFactory.success(_pRec.isDeleted) :
            insufficientPermission()
    }

    Result<PhoneRecordStatus> tryGetNote() {
        _perm.canView() ?
            IOCUtils.resultFactory.success(_pRec.note) :
            insufficientPermission()
    }

    Result<List<? extends ReadOnlyContactNumber>> tryGetSortedNumbers() {
        _perm.canView() ?
            IOCUtils.resultFactory.success(_pRec.sortedNumbers) :
            insufficientPermission()
    }

    // Setters
    // -------

    Result<Void> trySetIsDeletedIfPresent(Boolean isDeleted) {
        if (isDeleted == null) {
            return IOCUtils.resultFactory.success()
        }
        if (_perm.isOwner()) {
            _pRec.isDeleted = isDeleted
            IOCUtils.resultFactory.success()
        }
        else { insufficientPermission() }
    }

    Result<Void> trySetNameIfPresent(String name) {
        if (!name) {
            return IOCUtils.resultFactory.success()
        }
        if (_perm.canModify()) {
            _pRec.name = name
            IOCUtils.resultFactory.success()
        }
        else { insufficientPermission() }
    }

    Result<Void> trySetNoteIfPresent(String note) {
        if (!note) {
            return IOCUtils.resultFactory.success()
        }
        if (_perm.canModify()) {
            _pRec.note = note
            IOCUtils.resultFactory.success()
        }
        else { insufficientPermission() }
    }

    Result<ContactNumber> tryMergeNumber(BasePhoneNumber bNum, int preference) {
        _perm.canModify() ? _pRec.mergeNumber(bNum, preference) : insufficientPermission()
    }

    Result<Void> tryDeleteNumber(BasePhoneNumber bNum) {
        _perm.canModify() ? _pRec.deleteNumber(bNum) : insufficientPermission()
    }
}
