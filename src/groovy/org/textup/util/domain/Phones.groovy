package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class Phones {

    static Result<Phone> create(Long ownerId, PhoneOwnershipType type) {
        Phone p1 = new Phone()
        p1.owner = new PhoneOwnership(ownerId: ownerId, type: type, phone: p1)
        Utils.trySave(p1)
    }

    static Result<Phone> update(Phone p1, Object awayMsg, Object voice, Object lang, Object useVoicemail) {
        if (awayMsg) {
            p1.awayMessage = awayMsg
        }
        if (voice) {
            p1.voice = TypeConversionUtils.convertEnum(VoiceType, voice)
        }
        if (lang) {
            p1.language = TypeConversionUtils.convertEnum(VoiceLanguage, lang)
        }
        if (useVoicemail != null) {
            p1.useVoicemailRecordingIfPresent = TypeConversionUtils
                .to(Boolean, useVoicemail, p1.useVoicemailRecordingIfPresent)
        }
        Utils.trySave(p1)
    }

    static Result<Phone> mustFindForOwner(Long ownerId, PhoneOwnershipType type, boolean createIfAbsent) {
        Phone p1 = Holders.applicationContext.getBean(PhoneCache).findPhone(ownerId, type, false)
        if (p1) {
            IOCUtils.resultFactory.success(p1)
        }
        else {
            if (createIfAbsent) {
                Phones.create(ownerId, type)
            }
            else { // TODO add message
                IOCUtils.resultFactory.failWithCodeAndStatus("phone.notFound", ResultStatus.NOT_FOUND)
            }
        }
    }

    static DetachedCriteria<Phone> buildForOwnerIdAndType(Long ownerId, PhoneOwnershipType type) {
        new DetachedCriteria(Phone)
            .build {
                eq("owner.ownerId", ownerId)
                eq("owner.type", type)
            }
            .build(Phones.forActive())
    }

    static DetachedCriteria<Phone> buildAllPhonesForStaffId(Long staffId) {
        new DetachedCriteria(Phone)
            .build {
                or {
                    and {
                        eq("owner.type", PhoneOwnershipType.INDIVIDUAL)
                        eq("owner.ownerId", staffId)
                    }
                    and {
                        eq("owner.type", PhoneOwnershipType.GROUP)
                        eq("owner.ownerId", Teams
                            .buildForStaffIds([staffId])
                            .build(CriteriaUtils.returnsId()))
                    }
                }
            }
            .build(Phones.forActive())
    }

    static Closure forActive() {
        return { // TODO update as we change the definition of what it means for a phone to be active
            isNotNull("numberAsString")
        }
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static HashSet<Phone> findEveryForRecords(Collection<Long> recIds) {
        HashSet<Phone> allPhones = new HashSet<>()
        if (recIds) {
            List<Phone> phones = PhoneRecords.buildActiveForRecordIds(recIds)
                .build(PhoneRecords.returnsPhone())
                .list() as List<Phone>
            allPhones.addAll(phones)
        }
        allPhones
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Result<Map<Phone, Collection<RecordItem>>> findEveryModifiableForItems(Collection<RecordItem> rItems) {
        Collection<Long> recIds = rItems*.record*.id
        // build map of record ids to items to link records to their items later on
        Map<Long, HashSet<RecordItem>> recordIdToItems = [:].withDefault { new HashSet<RecordItem>() }
        rItems.each { RecordItem rItem1 -> recordIdToItems[rItem1.record.id] << rItem1 }
        // populate with items
        Map<Phone, HashSet<RecordItem>> phoneToItems = [:].withDefault { new HashSet<RecordItem>() }
        List<PhoneRecordWrapper> wrappers = PhoneRecords.buildActiveForPhoneIds(recIds)
            .list()
            *.toWrapper()
        wrappers.each { PhoneRecordWrapper w1 ->
            w1.tryGetRecord()
                .then { Record rec1 -> w1.tryGetPhone().curry(rec1) }
                .then { Record rec1, Phone p1 ->
                    phoneToItems[p1].addAll(recordIdToItems[rec1.id])
                }
        }
        IOCUtils.resultFactory.success(phoneToItems)
    }

    static Result<Void> canShare(PhoneOwnership owner, PhoneOwnership target) {
        PhoneOwnershipType ownerType = owner.type,
            targetType = target.type
        Boolean canShare
        if (ownerType == PhoneOwnershipType.INDIVIDUAL) {
            if (targetType == PhoneOwnershipType.INDIVIDUAL) {
                canShare = Teams.hasTeamsInCommon(owner.ownerId, target.ownerId)
            }
            else { // individual --> group
                canShare = Teams.teamContainsMember(target.ownerId, owner.ownerId)
            }
        }
        else {
            if (targetType == PhoneOwnershipType.INDIVIDUAL) {
                canShare = Teams.teamContainsMember(owner.ownerId, target.ownerId)
            }
            else { // group --> group
                canShare = owner.allowSharingWithOtherTeams
            }
        }
        canShare ?
            IOCUtils.resultFactory.success() :
            IOCUtils.resultFactory.failWithCodeAndStatus("phone.share.cannotShare",
                ResultStatus.FORBIDDEN, [target.buildName()])
    }
}
