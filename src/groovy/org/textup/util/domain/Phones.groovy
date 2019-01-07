package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class Phones {

    static Result<Phone> create(Long ownerId, PhoneOwnershipType type) {
        Phone p1 = new Phone()
        p1.owner = new PhoneOwnership(ownerId: ownerId, type: type)
        if (p1.save()) {
            IOCUtils.resultFactory.success(p1)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(p1.errors) }
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
        if (p1.save()) {
            IOCUtils.resultFactory.success(p1)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(p1.errors) }
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

    static DetachedCriteria<Phone> forAllPhonesFromStaffId(Long staffId) {
        new DetachedCriteria(Phone).build {
            isNotNull("numberAsString")
            or {
                and {
                    eq("owner.type", PhoneOwnershipType.INDIVIDUAL)
                    eq("owner.ownerId", staffId)
                }
                and {
                    eq("owner.type", PhoneOwnershipType.GROUP)
                    eq("owner.ownerId", Teams
                        .forStaffId(staffId)
                        .build(CriteriaUtils.buildForId()))
                }
            }
        }
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static HashSet<Phone> findEveryForRecords(Collection<Long> recIds) {
        HashSet<Phone> allPhones = new HashSet<>()
        if (recIds) {
            List<Phone> phones = Phone.createCriteria().listDistinct {
                or {
                    "in"("id", PhoneRecords
                        .forRecordIds(recIds)
                        .build(PhoneRecords.buildForPhoneId()))
                    "in"("id", SharedContact
                        .forRecordIds(recIds)
                        .build(SharedContact.buildForSharedWithId()))
                }
            } as List<Phone>
            allPhones.addAll(phones)
        }
        allPhones
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Result<Map<Phone, Collection<RecordItem>>> findEveryForItems(Collection<RecordItem> rItems) {
        Collection<Long> recIds = rItems*.record*.id
        Map<Long, HashSet<RecordItem>> recordIdToItems = [:].withDefault { new HashSet<RecordItem>() }
        rItem.each { RecordItem rItem1 -> recordIdToItems[rItem1.record.id] << rItem }


        // TODO aim to consolidate into a unified paradigm for accessing ALL records with one call
        // to avoid having to this two step process everywhere

        // populate with items that the phones directly own
        Map<Phone, HashSet<RecordItem>> phoneToItems = [:].withDefault { new HashSet<RecordItem>() }
        List<PhoneRecord> phoneRecs = PhoneRecords.forRecordIds(recIds).list()
        phoneRecs.each { PhoneRecord pr1 ->
            phoneToItems[pr1.phone].addAll(recordIdToItems[pr1.record.id])
        }
        // populate with items that the phones can access via sharing
        List<SharedContact> sharedContacts = SharedContact.forRecordIds(recIds).list()
        sharedContacts.each { SharedContact sc1 ->
            sc1.tryGetRecord().then { Record rec1 ->
                phoneToItems[sc1.sharedWith].addAll(recordIdToItems[rec1.id])
            }
        }
        IOCUtils.resultFactory.success(phoneToItems)
    }
}
