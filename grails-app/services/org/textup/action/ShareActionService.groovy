package org.textup.action

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class ShareActionService implements HandlesActions<PhoneRecord, Void> {

    SocketService socketService

    @Override
    boolean hasActions(Map body) { !!body?.doShareActions }

    @Override
    Result<Void> tryHandleActions(PhoneRecord pr1, Map body) {
        ActionContainer.tryProcess(ShareAction, body.doShareActions)
            .then { List<ShareAction> actions ->
                ResultGroup<?> resGroup = new ResultGroup<>()
                Map<SharePermission, HashSet<String>> permissionToNames = [:]
                    .withDefault { new HashSet<String>() }
                actions.each { ShareAction a1 ->
                    Phone p1 = a1.buildPhone()
                    switch (a1) {
                        case ShareAction.MERGE:
                            SharePermission perm1 = a1.buildSharePermission()
                            resGroup << tryStartShare(pr1, p1, a1perm1)
                            permissionToNames[a1perm1] << p1.owner.buildName()
                            break
                        default: // ShareAction.STOP
                            resGroup << stopShare(pr1, p1)
                            permissionToNames[SharePermission.NONE] << p1.owner.buildName()
                    }
                }
                resGroup.toEmptyResult(false).curry(permissionToNames)
            }
            .then { Map<SharePermission, HashSet<String>> permissionToNames ->
                AuthUtils.tryGetAuthUser().curry(permissionToNames)
            }
            .then { Map<SharePermission, HashSet<String>> permissionToNames, Staff s1 ->
                tryRecordSharingChanges(pr1.record, s1.toAuthor(), permissionToNames)
                    .logFail("handleActions: record sharing changes")
                    .toEmptyResult(false)
            }
    }

    // Helpers
    // -------

    protected Result<SharedContact> tryStartShare(PhoneRecord source, Phone shareWith,
        SharePermission permission) {

        Phones.canShare(source.phone.owner, shareWith.owner)
            .then { stopShare(source, shareWith) } // prevent duplicate sharing relationships
            .then {
                PhoneRecord pr1 = new PhoneRecord(shareSource: source, phone: shareWith,
                    permission: permission, record: source.record)
                DomainUtils.trySave(pr1)
            }
    }

    protected Result<Void> stopShare(PhoneRecord source, Phone shareWith) {
        List<PhoneRecord> prList = PhoneRecords
            .forActiveWithPhoneIds([shareWith.id])
            .build(PhoneRecords.forShareSourceIds([source.id]))
            .list()
        prList.each { PhoneRecord pr1 -> pr1.dateExpired = DateTime.now() }
        DomainUtils.trySaveAll(prList)
    }

    protected ResultGroup<RecordNote> tryRecordSharingChanges(Record rec1, Author author,
        Map<SharePermission, HashSet<String>> permissionToNames) {

        ResultGroup<RecordNote> resGroup = new ResultGroup()
        permissionToNames.each { SharePermission perm1, HashSet<String> names ->
            RecordNote rNote1 = new RecordNote(record: rec1,
                isReadOnly: true,
                noteContents: perm1.buildSummary(names),
                author: author)
            resGroup << DomainUtils.trySave(rNote1)
        }
        // push new system notes to the app
        socketService.sendItems(resGroup.payload)
        resGroup
    }
}
