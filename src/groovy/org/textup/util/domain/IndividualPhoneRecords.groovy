package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class IndividualPhoneRecords {

    static Result<IndividualPhoneRecordWrapper> create(Phone p1, List<? extends BasePhoneNumber> bNums = []) {
        Records.create()
            .then { Record rec1 ->
                IndividualPhoneRecord ipr1 = new IndividualPhoneRecord(phone: p1, record: rec1)
                // need to save before adding numbers so that the domain is assigned an
                // ID to be associated with the ContactNumbers to avoid a TransientObjectException
                Utils.trySave(ipr1)
            }
            .then { IndividualPhoneRecord ipr1 ->
                ResultGroup<ContactNumber> resGroup = new ResultGroup<>()
                bNums.unique().eachWithIndex { BasePhoneNumber bNum, int preference ->
                    resGroup << ipr1.mergeNumber(bNum, preference)
                }
                if (resGroup.anyFailures) {
                    IOCUtils.resultFactory.failWithGroup(resGroup)
                }
                else { IOCUtils.resultFactory.success(ipr1, ResultStatus.CREATED) }
            }
    }

    static Result<Map<PhoneNumber, List<IndividualPhoneRecord>>> findEveryByNumbers(Phone p1,
        List<? extends BasePhoneNumber> bNums, boolean createIfAbsent) {

        // step 1: find all contact numbers that match the ones passed ine
        List<ContactNumber> cNums = ContactNumber.createCriteria().list {
            eq("owner.phone", p1)
            eq("owner.isDeleted", false)
            ne("owner.status", PhoneRecordStatus.BLOCKED)
            CriteriaUtils.inList(delegate, "number", bNums)
        } as List<ContactNumber>
        // step 2: group contacts by the passed-in phone numbers
        Map<PhoneNumber, List<IndividualPhoneRecord>> numberToPhoneRecords = [:]
            .withDefault { [] as List<IndividualPhoneRecord> }
        cNums.each { ContactNumber cNum -> numberToPhoneRecords[cNum] << cNum.owner }
        // step 3: if allowed, create new contacts for any phone numbers without any contacts
        if (createIfAbsent) {
            createIfNone(p1, numberToPhoneRecords)
        }
        else { IOCUtils.resultFactory.success(numberToPhoneRecords) }
    }

    // Helpers
    // -------

    protected static Result<Map<PhoneNumber, List<IndividualPhoneRecord>>> createIfNone(Phone p1,
        Map<PhoneNumber, List<IndividualPhoneRecord>> numberToPhoneRecords) {

        ResultGroup<IndividualPhoneRecord> resGroup = new ResultGroup<>()
        numberToPhoneRecords.each { PhoneNumber pNum, List<IndividualPhoneRecord> iprList ->
            if (iprList.isEmpty()) {
                resGroup << IndividualPhoneRecords.create(p1, [pNum])
                    .then { IndividualPhoneRecord ipr1 ->
                        iprList << ipr1
                        IOCUtils.resultFactory.success(ipr1)
                    }
            }
        }
        if (resGroup.anyFailures) {
            IOCUtils.failWithGroup(resGroup)
        }
        else { IOCUtils.resultFactory.success(numberToPhoneRecords) }
    }
}
