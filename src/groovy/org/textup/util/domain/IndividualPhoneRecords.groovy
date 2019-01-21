package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class IndividualPhoneRecords {

    static Result<IndividualPhoneRecord> mustFindActiveForId(Long iprId) {
        IndividualPhoneRecord ipr1 = new DetachedCriteria(IndividualPhoneRecord)
            .build { idEq(iprId) }
            .build(PhoneRecords.forActive())
            .get()
        if (ipr1) {
            IOCUtils.resultFactory.success(ipr1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("", // TODO
                ResultStatus.NOT_FOUND, [iprId])
        }
    }

    static List<IndividualPhoneRecord> findEveryByIdsAndPhoneId(Collection<Long> ids, Long pId) {
        new DetachedCriteria(PhoneRecord)
            .build {
                CriteriaUtils.inList(delegate, "id", ids)
                eq("phone.id", pId)
            }
            .list()
    }

    static Result<Map<PhoneNumber, List<IndividualPhoneRecord>>> tryFindEveryByNumbers(Phone p1,
        List<? extends BasePhoneNumber> bNums, boolean createIfAbsent) {

        if (!bNums) {
            return IOCUtils.resultFactory.success([:])
        }
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
            tryCreateIfNone(p1, numberToPhoneRecords)
        }
        else { IOCUtils.resultFactory.success(numberToPhoneRecords) }
    }

    static Result<Map<PhoneNumber, HashSet<Long>>> tryFindEveryIdByNumbers(Long phoneId = null,
        Collection<Long> iprIds = null) {

        Map<PhoneNumber, HashSet<Long>> numToIds = [:].withDefault { new HashSet<Long>() }
        IndividualPhoneRecord.createCriteria()
            .list {
                // order of property projections dictates order in object array!!
                // IMPORTANT: must use createCriteria to specify this query. Using DetachedCriteria
                // only returns the id and does not also return the second projection property
                // example output: [[3, 1112223333], [6, 3006518300], [9, 1175729624], [9, 1994427797]]
                // the output above looks like a list of Object arrays, but it's actually a list of
                // Object arrays containing one item, which is another 2-member Object array
                projections {
                    property("id")
                    numbers { property("number") }
                }
                CriteriaUtils.inList(delegate, "id", iprIds, true)
                if (phoneId) {
                    eq("phone.id", phoneId)
                }
                CriteriaUtils.compose(delegate, PhoneRecords.forActive())
            }
            .each { Object[] itemWrapper ->
                // each item is a 1-item array, inner is a 2-item array
                Object[] items = itemWrapper[0] as Object[]
                // inner array has contact id as first element
                Long id = TypeConversionUtils.to(Long, items[0])
                // inner array has phone number as second element
                String num = items[1] as String

                PhoneNumber.tryCreate(num)
                    .then { PhoneNumber pNum -> numToIds[pNum] << id }
            }
        numToIds
    }

    static DetachedCriteria<IndividualPhoneRecord> buildForIds(Collection<Long> ids) {
        new DetachedCriteria<IndividualPhoneRecord>
            .build { CriteriaUtils.inList(delegate, "id", ids) }
    }

    // Helpers
    // -------

    protected static Result<Map<PhoneNumber, List<IndividualPhoneRecord>>> tryCreateIfNone(Phone p1,
        Map<PhoneNumber, List<IndividualPhoneRecord>> numberToPhoneRecords) {

        ResultGroup<IndividualPhoneRecord> resGroup = new ResultGroup<>()
        numberToPhoneRecords.each { PhoneNumber pNum, List<IndividualPhoneRecord> iprList ->
            if (iprList.isEmpty()) {
                resGroup << IndividualPhoneRecord.tryCreate(p1, [pNum])
                    .then { IndividualPhoneRecord ipr1 -> ipr1.mergeNumber(pNum, 0).curry(ipr1) }
                    .then { IndividualPhoneRecord ipr1 ->
                        iprList << ipr1
                        IOCUtils.resultFactory.success(ipr1)
                    }
            }
        }
        resGroup.toEmptyResult(false)
            .then { IOCUtils.resultFactory.success(numberToPhoneRecords) }
    }
}
