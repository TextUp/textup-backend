package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import org.textup.*

@GrailsTypeChecked
@Validateable
class Recipients implements Validateable, Dehydratable<Recipients.Dehydrated> {

    final Integer maxNum // non private for access in validator
    private final Collection<? extends PhoneRecord> all
    private final Phone phone
    final VoiceLanguage language

    static constraints = { // all nullable: false by default
        maxNum min: 1
        all minSize: 1, validator: { Collection<? extends PhoneRecord> val, Recipients obj ->
            if (val) {
                if (val.findAll { !it.toPermissions().canModify() }) {
                    return ["someNoPermissions"]
                }
                if (val.size() > obj.maxNum) {
                    return ["tooManyRecipients", obj.maxNum, val.size()]
                }
            }
        }
    }

    static class Dehydrated implements Rehydratable<Recipients> {
        private final Long phoneId
        private final Collection<Long> allIds
        private final Integer maxNum
        private final VoiceLanguage language

        @Override
        Result<Recipients> tryRehydrate() {
            Recipients r1 = new Recipients(phone: Phone.get(phoneId),
                all: AsyncUtils.getAllIds(PhoneRecord, allIds),
                maxNum: maxNum,
                language: language)
            DomainUtils.tryValidate(r1)
        }
    }

    static Result<Recipients> tryCreate(Phone p1, Collection<Long> ids,
        Collection<PhoneNumber> pNums, int maxNum) {

        IndividualPhoneRecords.tryFindEveryByNumbers(p1, pNums, true)
            .then { Map<PhoneNumber, List<IndividualPhoneRecord>> ipRecs ->
                Collection<? extends PhoneRecord> pRecs = CollectionUtils.mergeUnique(*ipRecs.values())
                pRecs += PhoneRecords.buildActiveForPhoneIds([p1.id])
                    .build(PhoneRecords.forIds(ids))
                    .list()
                Recipients r1 = new Recipients(phone: p1,
                    all: pRecs,
                    maxNum: maxNum,
                    language: p1.language)
                DomainUtils.tryValidate(r1, ResultStatus.CREATED)
            }
    }

    static Result<Recipients> tryCreate(Collection<? extends PhoneRecord> pRecs,
        VoiceLanguage language, int maxNum) {

        Recipients r1 = new Recipients(phone: pRecs?.getAt(0)?.phone,
            all: pRecs,
            maxNum: maxNum,
            language: language)
        DomainUtils.tryValidate(r1, ResultStatus.CREATED)
    }

    // Methods
    // -------

    @Override
    Recipients.Dehydrated dehydrate() {
        new Recipients.Dehydrated(phoneIds: phone.id, allIds: all*.id, maxNum: maxNum)
    }

    Result<PhoneWrapper> tryGetOne() {
        PhoneRecord pr1 = all?.getAt(0)
        pr1 ?
            IOCUtils.resultFactory.success(pr1.toWrapper()) :
            IOCUtils.resultFactory.failWithCodeAndStatus("", ResultStatus.UNPROCESSABLE_ENTITY) // TODO
    }

    Result<IndividualPhoneRecordWrapper> tryGetOneIndividual() {
        IndividualPhoneRecordWrapper w1
        if (all) {
            w1 = allIds*.toWrapper().find { it instanceof IndividualPhoneRecordWrapper }
        }
        w1 ?
            IOCUtils.resultFactory.success(w1) :
            IOCUtils.resultFactory.failWithCodeAndStatus("", ResultStatus.UNPROCESSABLE_ENTITY) // TODO
    }

    String buildFromName() { phone?.owner?.buildName() }

    // loops through unique records for individuals, groups, and group members
    void eachRecord(Closure action) {
        CollectionUtils.mergeUnique(*all*.buildRecords()).each(action)
    }

    // loops through owned and shared individuals, calling each passing the
    // `IndividualPhoneRecordWrapper` and all relevant individual and group records
    void eachIndividualWithRecords(Closure action) {
        Collection<GroupPhoneRecord> groups = all.findAll { it instanceof GroupPhoneRecord }
        Map<Long, Collection<GroupPhoneRecord>> idToGroups = PhoneRecordUtils.buildMemberIdToGroups(groups)
        all?.each { PhoneRecord pr1 ->
            PhoneRecordWrapper w1 = pr1.toWrapper()
            if (w1 instanceof IndividualPhoneRecordWrapper) {
                action.call(w1, CollectionUtils.mergeUnique(pr1.record, idToGroups[pr1.id]*.record))
            }
        }
    }
}
