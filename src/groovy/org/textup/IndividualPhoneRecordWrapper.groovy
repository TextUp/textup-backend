package org.textup

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime

@GrailsTypeChecked
class IndividualPhoneRecordWrapper extends PhoneRecordWrapper {

    private final IndividualPhoneRecord individualWrapper

    IndividualPhoneRecordWrapper(IndividualPhoneRecord ipr1, PhoneRecordPermissions permissions,
        PhoneRecord overrides = null) {

        super(overrides ?: ipr1, permissions)
        individualWrapper = ipr1
    }

    // Methods
    // -------

    @Override
    IndividualPhoneRecordWrapper save() { individualWrapper.save() ? this : null }

    @Override
    Result<? extends IndividualPhoneRecord> tryUnwrap() {
        permissions.isOwner() ?
            IOCUtils.resultFactory.success(individualWrapper) :
            insufficientPermission()
    }

    Result<Void> tryDelete() {
        if (permissions.isOwner()) {
            individualWrapper.isDeleted = true
            individualWrapper.tryCancelFutureMessages().then { IOCUtils.resultFactory.success() }
        }
        else { insufficientPermission() }
    }

    // Getters
    // -------

    Result<Boolean> tryGetIsDeleted() {
        permissions.canView() ?
            IOCUtils.resultFactory.success(individualWrapper.isDeleted) :
            insufficientPermission()
    }

    Result<PhoneRecordStatus> tryGetNote() {
        permissions.canView() ?
            IOCUtils.resultFactory.success(individualWrapper.note) :
            insufficientPermission()
    }

    Result<List<ReadOnlyContactNumber>> tryGetSortedNumbers() {
        permissions.canView() ?
            IOCUtils.resultFactory.success(individualWrapper.sortedNumbers) :
            insufficientPermission()
    }

    @Override
    Result<Phone> tryGetPhone() {
        permissions.canModify() ?
            IOCUtils.resultFactory.success(individualWrapper.phone) :
            insufficientPermission()
    }

    // TODO do we need this?
    @Override
    Result<ReadOnlyPhone> tryGetReadOnlyPhone() {
        permissions.canView() ?
            IOCUtils.resultFactory.success(individualWrapper.phone) :
            insufficientPermission()
    }

    @Override
    Result<String> tryGetSecureName() {
        permissions.canView() ?
            IOCUtils.resultFactory.success(individualWrapper.secureName) :
            insufficientPermission()
    }

    @Override
    Result<String> tryGetPublicName() {
        permissions.canView() ?
            IOCUtils.resultFactory.success(individualWrapper.publicName) :
            insufficientPermission()
    }

    // Setters
    // -------

    Result<Void> trySetNameIfPresent(String name) {
        if (!name) {
            return IOCUtils.resultFactory.success()
        }
        if (permissions.canModify()) {
            individualWrapper.name = name
            IOCUtils.resultFactory.success()
        }
        else { insufficientPermission() }
    }

    Result<Void> trySetNoteIfPresent(String note) {
        if (!note) {
            return IOCUtils.resultFactory.success()
        }
        if (permissions.canModify()) {
            individualWrapper.note = note
            IOCUtils.resultFactory.success()
        }
        else { insufficientPermission() }
    }

    Result<ContactNumber> tryMergeNumber(BasePhoneNumber bNum, int preference) {
        permissions.canModify() ?
            individualWrapper.mergeNumber(bNum, preference) :
            insufficientPermission()
    }

    Result<Void> tryDeleteNumber(BasePhoneNumber bNum) {
        permissions.canModify() ?
            individualWrapper.deleteNumber(bNum) :
            insufficientPermission()
    }
}
