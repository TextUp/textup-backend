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
                Map<SharePermission, HashSet<String>> permissionToNames = [:]
                    .withDefault { new HashSet<String>() }
                ResultGroup
                    .<ShareAction, ?>collect(actions) { ShareAction a1 ->
                        Phone p1 = a1.buildPhone()
                        switch (a1) {
                            case ShareAction.MERGE:
                                permissionToNames[a1perm1] << p1.owner.buildName()
                                tryStartShare(pr1, p1, a1.buildSharePermission())
                                break
                            default: // ShareAction.STOP
                                permissionToNames[SharePermission.NONE] << p1.owner.buildName()
                                tryStopShare(pr1, p1)
                        }
                    }
                    .toEmptyResult(false)
                    .curry(permissionToNames)
            }
            .then { Map<SharePermission, HashSet<String>> permissionToNames ->
                AuthUtils.tryGetAuthUser().curry(permissionToNames)
            }
            .then { Map<SharePermission, HashSet<String>> permissionToNames, Staff s1 ->
                tryRecordSharingChanges(pr1.record, s1.toAuthor(), permissionToNames)
            }
    }

    // Helpers
    // -------

    protected Result<SharedContact> tryStartShare(PhoneRecord source, Phone shareWith,
        SharePermission permission) {

        Phones.canShare(source.phone.owner, shareWith.owner)
            .then { tryStopShare(source, shareWith) } // prevent duplicate sharing relationships
            .then { PhoneRecord.tryCreate(permission, source, shareWith) }
    }

    protected Result<Void> tryStopShare(PhoneRecord source, Phone shareWith) {
        List<PhoneRecord> prList = PhoneRecords.forActiveWithPhoneIds([shareWith.id])
            .build(PhoneRecords.forShareSourceIds([source.id]))
            .list()
        prList.each { PhoneRecord pr1 -> pr1.dateExpired = DateTime.now() }
        DomainUtils.trySaveAll(prList)
    }

    protected Result<Void> tryRecordSharingChanges(Record rec1, Author author,
        Map<SharePermission, HashSet<String>> permissionToNames) {

        ResultGroup
            .collect(permissionToNames) { SharePermission perm1, HashSet<String> names ->
                TempRecordItem.tryCreate(perm1.buildSummary(names), null, null)
                    .then { TempRecordItem temp1 -> RecordNote.tryCreate(rec1, temp1) }
                    .then { RecordNote rNote1 ->
                        rNote1.with {
                            author = author
                            isReadOnly = true
                        }
                        DomainUtils.trySave(rNote1)
                    }
            }
            .logFail("tryRecordSharingChanges")
            .toResult(false)
            .then { List<RecordNote> rNotes ->
                socketService.sendItems(rNotes)
                Result.void()
            }
    }
}
