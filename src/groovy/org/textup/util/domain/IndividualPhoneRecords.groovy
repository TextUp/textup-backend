package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class IndividualPhoneRecords {

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
                resGroup << IndividualPhoneRecord.create(p1, [pNum])
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
