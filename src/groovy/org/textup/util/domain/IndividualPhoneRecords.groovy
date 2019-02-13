package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

class IndividualPhoneRecords {

    @GrailsTypeChecked
    static Result<IndividualPhoneRecord> mustFindActiveForId(Long iprId) {
        IndividualPhoneRecord ipr1 = IndividualPhoneRecord.get(iprId)
        if (ipr1?.isActive()) {
            IOCUtils.resultFactory.success(ipr1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("individualPhoneRecords.notFound",
                ResultStatus.NOT_FOUND, [iprId])
        }
    }

    static Result<Map<PhoneNumber, List<IndividualPhoneRecord>>> tryFindOrCreateNumToObjByPhoneAndNumbers(Phone p1,
        Collection<? extends BasePhoneNumber> bNums, boolean createIfAbsent) {

        Map<? extends BasePhoneNumber, List<IndividualPhoneRecord>> numberToPhoneRecords = [:]
        if (!bNums) {
            return IOCUtils.resultFactory.success(numberToPhoneRecords)
        }
        // step 1: find all contact numbers that match the ones passed ine
        List<ContactNumber> cNums = ContactNumber.createCriteria().list {
            owner {
                eq("phone", p1)
                eq("isDeleted", false)
                ne("status", PhoneRecordStatus.BLOCKED)
            }
            CriteriaUtils.inList(delegate, "number", bNums*.number)
        } as List<ContactNumber>
        // step 2: group contacts by the passed-in phone numbers
        // ensure that even numbers without any IPRs have an empty list
        bNums.each { BasePhoneNumber bNum -> numberToPhoneRecords[PhoneNumber.copy(bNum)] = [] }
        cNums.each { ContactNumber cNum ->
            numberToPhoneRecords[PhoneNumber.copy(cNum)]?.add(cNum.owner)
        }
        // step 3: if allowed, create new contacts for any phone numbers without any contacts
        if (createIfAbsent) {
            tryCreateIfNone(p1, numberToPhoneRecords)
        }
        else { IOCUtils.resultFactory.success(numberToPhoneRecords) }
    }

    static Map<PhoneNumber, HashSet<Long>> findNumToIdByPhoneIdAndOptions(Long phoneId,
        Collection<Long> iprIds = null) {

        Map<PhoneNumber, HashSet<Long>> numToIds = [:].withDefault { new HashSet<Long>() }
        if (phoneId) {
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
                    eq("phone.id", phoneId)
                    ClosureUtils.compose(delegate, PhoneRecords.forActive())
                }
                .each { Object[] itemWrapper ->
                    // each item is a 1-item array, inner is a 2-item array
                    Object[] items = itemWrapper[0] as Object[]
                    // inner array has contact id as first element
                    Long id = TypeUtils.to(Long, items[0])
                    // inner array has phone number as second element
                    String num = items[1] as String

                    PhoneNumber.tryCreate(num)
                        .thenEnd { PhoneNumber pNum -> numToIds[pNum] << id }
                }
        }
        numToIds
    }

    static List<IndividualPhoneRecord> findEveryByIdsAndPhoneId(Collection<Long> ids, Long pId) {
        new DetachedCriteria(PhoneRecord)
            .build {
                CriteriaUtils.inList(delegate, "id", ids)
                eq("phone.id", pId)
            }
            .list()
    }

    static DetachedCriteria<IndividualPhoneRecord> buildForIds(Collection<Long> ids) {
        new DetachedCriteria(IndividualPhoneRecord)
            .build { CriteriaUtils.inList(delegate, "id", ids) }
    }

    // Helpers
    // -------

    @GrailsTypeChecked
    protected static Result<Map<PhoneNumber, List<IndividualPhoneRecord>>> tryCreateIfNone(Phone p1,
        Map<? extends BasePhoneNumber, List<IndividualPhoneRecord>> numberToPhoneRecords) {

        ResultGroup<IndividualPhoneRecord> resGroup = new ResultGroup<>()
        numberToPhoneRecords.each { BasePhoneNumber bNum, List<IndividualPhoneRecord> iprList ->
            if (iprList.isEmpty()) {
                resGroup << IndividualPhoneRecord.tryCreate(p1)
                    .then { IndividualPhoneRecord ipr1 -> ipr1.mergeNumber(bNum, 0).curry(ipr1) }
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
