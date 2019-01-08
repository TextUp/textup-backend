package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class SharedContactService {

    AuthService authService
    SocketService socketService

    Result<Void> handleActions(Contact c1, Map body) {
        ActionContainer ac1 = new ActionContainer<>(ShareContactAction, body.doShareActions)
        if (!ac1.validate()) {
            return IOCUtils.resultFactory.failWithValidationErrors(ac1.errors)
        }
        ResultGroup<?> resGroup = new ResultGroup<>()
        HashSet<String> stopNames = new HashSet<>(),
            viewNames = new HashSet<>(),
            delegateNames = new HashSet<>()
        ac1.actions.each { ShareContactAction a1 ->
            HashSet<String> nameList
            switch (a1) {
                case Constants.SHARE_ACTION_MERGE:
                    resGroup << tryStartShare(c1, a1.phone, a1.permissionAsEnum)
                    if (a1.permissionAsEnum == SharePermission.DELEGATE) {
                        nameList = delegateNames
                    }
                    else { nameList = viewNames }
                    break
                default: // Constants.SHARE_ACTION_STOP
                    resGroup << stopShare(c1, a1.phone)
                    nameList = stopNames
            }
            nameList << a1.phone.owner.buildName()
        }
        if (resGroup.anyFailures) {
            IOCUtils.resultFactory.failWithGroup(resGroup)
        }
        else {
            tryRecordSharingChanges(c1.record, stopNames, viewNames, delegateNames)
                .logFail("handleActions: record sharing changes")
            IOCUtils.resultFactory.success()
        }
    }

    // Helpers
    // -------

    protected Result<SharedContact> tryStartShare(IndividualPhoneRecord source, Phone shareWith,
        SharePermission permission) {

        Phones.canShare(source.phone.owner, shareWith.owner)
            .then { stopShare(source, shareWith) } // prevent duplicate sharing relationships
            .then {
                PhoneRecord pr1 = new PhoneRecord(shareSource: source, phone: shareWith,
                    permission: permission, record: source.record)
                Utils.trySave(pr1)
            }
    }

    protected Result<Void> stopShare(IndividualPhoneRecord ipr1, Phone shareWith) {
        List<PhoneRecord> prList = PhoneRecords
            .forActiveWithPhoneIds([shareWith.id])
            .build(PhoneRecords.forShareSourceIds([ipr1.id]))
            .list()
        prList.each { PhoneRecord pr1 -> pr1.dateExpired = DateTime.now() }
        Utils.trySaveAllAsResult(prList)
    }

    protected ResultGroup<RecordNote> tryRecordSharingChanges(Record rec,
        HashSet<String> stopNames, HashSet<String> viewNames, HashSet<String> delegateNames) {

        Author auth = authService.loggedInAndActive.toAuthor()
        ResultGroup<RecordNote> resGroup = new ResultGroup<>()
        if (stopNames) {
            resGroup << addRecordNote(rec, stopNames, "note.sharing.stop", auth)
        }
        if (viewNames) {
            resGroup << addRecordNote(rec, viewNames, "note.sharing.view", auth)
        }
        if (delegateNames) {
            resGroup << addRecordNote(rec, delegateNames, "note.sharing.delegate", auth)
        }
        // push new system notes to the app
        socketService.sendItems(resGroup.payload)
            .logFail("tryRecordSharingChanges: sending items through socket")
        resGroup
    }

    protected Result<RecordNote> addRecordNote(Record rec, HashSet<String> names, String code,
        Author author) {

        List<String> namesList = new ArrayList<>(names)
        String namesString = CollectionUtils.joinWithDifferentLast(namesList, ", ", ", and "),
            contents = IOCUtils.getMessage(code, [namesString])
        RecordNote rNote1 = new RecordNote(record: rec, isReadOnly: true, noteContents: contents)
        rNote1.author = author
        Utils.trySave(rNote1)
    }
}
