package org.textup.action

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.joda.time.DateTime
import org.textup.*
import org.textup.structure.*
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
                        String pName = p1.owner.buildName()
                        switch (a1) {
                            case ShareAction.MERGE:
                                SharePermission perm1 = a1.buildSharePermission()
                                permissionToNames[perm1] << pName
                                tryStartShare(pr1, p1, perm1)
                                break
                            default: // ShareAction.STOP
                                permissionToNames[SharePermission.NONE] << pName
                                tryStopShare(pr1, p1)
                        }
                    }
                    .toEmptyResult(false)
                    .curry(permissionToNames)
            }
            .then { Map<SharePermission, HashSet<String>> permissionToNames ->
                AuthUtils.tryGetActiveAuthUser().curry(permissionToNames)
            }
            .then { Map<SharePermission, HashSet<String>> permissionToNames, Staff s1 ->
                tryRecordSharingChanges(pr1.record, Author.create(s1), permissionToNames)
            }
    }

    // Helpers
    // -------

    protected Result<PhoneRecord> tryStartShare(PhoneRecord source, Phone shareWith,
        SharePermission permission) {

        Phones.canShare(source.phone.owner, shareWith.owner)
            .then { tryStopShare(source, shareWith) } // prevent duplicate sharing relationships
            .then { PhoneRecord.tryCreate(permission, source, shareWith) }
    }

    protected Result<Void> tryStopShare(PhoneRecord source, Phone shareWith) {
        List<PhoneRecord> prList = PhoneRecords.buildActiveForPhoneIds([shareWith.id])
            .build(PhoneRecords.forShareSourceIds([source.id]))
            .list()
        prList.each { PhoneRecord pr1 -> pr1.dateExpired = DateTime.now() }
        DomainUtils.trySaveAll(prList)
    }

    protected Result<Void> tryRecordSharingChanges(Record rec1, Author author,
        Map<SharePermission, ? extends Collection<String>> permissionToNames) {

        ResultGroup
            .collectEntries(permissionToNames) { SharePermission perm1, Collection<String> names ->
                TempRecordItem.tryCreate(perm1.buildSummary(names), null, null)
                    .then { TempRecordItem temp1 -> RecordNote.tryCreate(rec1, temp1) }
                    .then { RecordNote rNote1 ->
                        rNote1.author = author
                        rNote1.isReadOnly = true
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
