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
        Map<Phone,SharePermission> sharedWithToPermission = new HashMap<>()
        Collection<Phone> stopSharingPhones = []
        ResultGroup<?> resGroup = new ResultGroup<>()
        ac1.actions.each { ShareContactAction a1 ->
            switch (a1) {
                case Constants.SHARE_ACTION_MERGE:
                    resGroup << c1.phone.share(c1, a1.phone, a1.permissionAsEnum)
                    sharedWithToPermission[a1.phone] = a1.permissionAsEnum
                    break
                default: // Constants.SHARE_ACTION_STOP
                    resGroup << c1.phone.stopShare(c1, a1.phone)
                    stopSharingPhones << a1.phone
            }
        }
        if (resGroup.anyFailures) {
            IOCUtils.resultFactory.failWithGroup(resGroup)
        }
        else {
            if (sharedWithToPermission || stopSharingPhones) {
                recordSharingChanges(c1.record, sharedWithToPermission, stopSharingPhones)
                    .logFail("SharedContactService.handleActions: record sharing changes")
            }
            IOCUtils.resultFactory.success()
        }
    }

    protected ResultGroup<RecordNote> recordSharingChanges(Record rec,
        Map<Phone,SharePermission> sharedWithToPermission, Collection<Phone> stopSharingPhones) {

        HashSet<String> stopNames = new HashSet<>(),
            viewNames = new HashSet<>(),
            delegateNames = new HashSet<>()
        stopSharingPhones.each { Phone p1 -> stopNames.addAll(p1.owner.buildAllStaff()*.name) }
        sharedWithToPermission.each { Phone p1, SharePermission perm1 ->
            HashSet<String> namesSet = (perm1 == SharePermission.DELEGATE) ? delegateNames : viewNames
            namesSet.addAll(p1.owner.buildAllStaff()*.name)
        }
        Author auth = authService.loggedInAndActive.toAuthor()
        ResultGroup<RecordNote> resGroup = new ResultGroup<>()
        if (stopNames) {
            resGroup.add(recordSharingChangesHelper(rec, stopNames, "note.sharing.stop", auth))
        }
        if (viewNames) {
            resGroup.add(recordSharingChangesHelper(rec, viewNames, "note.sharing.view", auth))
        }
        if (delegateNames) {
            resGroup.add(recordSharingChangesHelper(rec, delegateNames, "note.sharing.delegate", auth))
        }
        // push new system notes to the app
        socketService.sendItems(resGroup.payload)
            .logFail("SharedContactService.recordSharingChanges: sending items through socket")
        resGroup
    }

    protected Result<RecordNote> recordSharingChangesHelper(Record rec, HashSet<String> names,
        String code, Author auth) {
        List<String> namesList = new ArrayList<>(names)
        String namesString = CollectionUtils.joinWithDifferentLast(namesList, ", ", ", and "),
            contents = IOCUtils.getMessage(code, [namesString])
        RecordNote rNote1 = new RecordNote(record:rec, isReadOnly:true, noteContents:contents)
        rNote1.author = auth
        if (rNote1.save()) {
            IOCUtils.resultFactory.success(rNote1)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(rNote1.errors) }
    }
}
