package org.textup.action

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import org.textup.validator.action.*
import spock.lang.*

// For some reason, in unit tests merging numbers with the same preference number doesn't add
// a new number to the hasMany relationship because of defects in mocking addTo* and removeFrom*

class MergeActionServiceIntegrationSpec extends Specification {

    MergeActionService mergeActionService

    void "test has actions"() {
        expect:
        mergeActionService.hasActions(doMergeActions: null) == false
        mergeActionService.hasActions(doMergeActions: "hi")
    }

    // We need to mock the finder for `IndividualPhoneRecord` because the integration
    // test is run WITHIN a transactiona and the `tryHandleActions` method is `Propagation.REQUIRES_NEW`
    void "test handing actions"() {
        given:
        String str1 = TestUtils.randString()

        IndividualPhoneRecord ipr1 = GroovyStub() { getPhone() >> GroovyMock(Phone) }
        MergeIndividualAction a1 = GroovyMock()
        MockedMethod mustFindActiveForId = MockedMethod.create(IndividualPhoneRecords, "mustFindActiveForId") {
            Result.createError([], ResultStatus.NOT_FOUND)
        }
        MockedMethod tryProcess = MockedMethod.create(ActionContainer, "tryProcess") {
            Result.createSuccess([a1])
        }
        MockedMethod tryMerge = MockedMethod.create(mergeActionService, "tryMerge")
        MockedMethod tryMergeWithInfo = MockedMethod.create(mergeActionService, "tryMergeWithInfo")

        when:
        Result res = mergeActionService.tryHandleActions(null, null)

        then:
        res.status == ResultStatus.NOT_FOUND
        tryProcess.notCalled
        tryMerge.notCalled
        tryMergeWithInfo.notCalled

        when:
        mustFindActiveForId = MockedMethod.create(mustFindActiveForId) {
            Result.createSuccess(ipr1)
        }
        res = mergeActionService.tryHandleActions(ipr1.id, [doMergeActions: str1])

        then:
        1 * a1.toString() >> MergeIndividualAction.DEFAULT
        tryProcess.callCount == 1
        tryProcess.latestArgs == [MergeIndividualAction, str1]
        tryMerge.callCount == 1
        tryMergeWithInfo.notCalled
        res.status == ResultStatus.NO_CONTENT

        when:
        res = mergeActionService.tryHandleActions(ipr1.id, [doMergeActions: str1])

        then:
        1 * a1.toString() >> MergeIndividualAction.RECONCILE
        tryProcess.callCount == 2
        tryProcess.latestArgs == [MergeIndividualAction, str1]
        tryMerge.callCount == 1
        tryMergeWithInfo.callCount == 1
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        tryProcess?.restore()
        tryMerge?.restore()
        tryMergeWithInfo?.restore()
    }

    void "test merging records"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord()
        IndividualPhoneRecord ipr3 = TestUtils.buildIndPhoneRecord()

        RecordItem rItem1 = TestUtils.buildRecordItem(ipr1.record)
        RecordItem rItem2 = TestUtils.buildRecordItem(ipr2.record)
        RecordItem rItem3 = TestUtils.buildRecordItem(ipr3.record)

        FutureMessage fMsg1 = TestUtils.buildFutureMessage(ipr1.record)
        FutureMessage fMsg2 = TestUtils.buildFutureMessage(ipr2.record)
        FutureMessage fMsg3 = TestUtils.buildFutureMessage(ipr3.record)

        when:
        Result res = mergeActionService.tryMergeRecords(ipr1, [ipr2, ipr3])

        then:
        res.status == ResultStatus.NO_CONTENT
        rItem1.refresh().record == ipr1.record
        rItem2.refresh().record == ipr1.record
        rItem3.refresh().record == ipr1.record
        fMsg1.refresh().record == ipr1.record
        fMsg2.refresh().record == ipr1.record
        fMsg3.refresh().record == ipr1.record
    }

    void "test merging sharing"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord()
        IndividualPhoneRecord ipr3 = TestUtils.buildIndPhoneRecord()

        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(ipr2)
        PhoneRecord spr2 = TestUtils.buildSharedPhoneRecord(ipr3)

        when:
        Result res = mergeActionService.tryMergeSharing(ipr1, [ipr2, ipr3])

        then:
        res.status == ResultStatus.NO_CONTENT
        spr1.refresh().record == ipr1.record
        spr1.refresh().shareSource == ipr1
        spr2.refresh().record == ipr1.record
        spr2.refresh().shareSource == ipr1
    }

    void "test merging groups"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord()
        IndividualPhoneRecord ipr3 = TestUtils.buildIndPhoneRecord()

        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()
        gpr1.members.addToPhoneRecords(ipr1)
        gpr1.members.addToPhoneRecords(ipr2)
        GroupPhoneRecord gpr2 = TestUtils.buildGroupPhoneRecord()
        gpr2.members.addToPhoneRecords(ipr2)
        gpr2.members.addToPhoneRecords(ipr3)
        GroupPhoneRecord gpr3 = TestUtils.buildGroupPhoneRecord()
        gpr3.members.addToPhoneRecords(ipr3)

        PhoneRecord.withSession { it.flush() }

        when:
        Result res = mergeActionService.tryMergeGroups(ipr1, [ipr2, ipr3])

        then:
        res.status == ResultStatus.NO_CONTENT
        ipr1 in gpr1.members.phoneRecords
        ipr2 in gpr1.members.phoneRecords

        ipr1 in gpr2.members.phoneRecords
        ipr2 in gpr2.members.phoneRecords
        ipr3 in gpr2.members.phoneRecords

        ipr1 in gpr3.members.phoneRecords
        ipr3 in gpr3.members.phoneRecords
    }


    void "test merging numbers"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord()
        IndividualPhoneRecord ipr3 = TestUtils.buildIndPhoneRecord()

        // TODO
        // // For some reason, in unit tests merging numbers with the same preference number doesn't add
        // // a new number to the hasMany relationship. Therefore, here we ensure that the numbers have
        // // different preference and we test number merging again in an integration test
        // ipr2.numbers.each { it.preference += 10 }
        // ipr3.numbers.each { it.preference += 100 }

        when:
        Result res = mergeActionService.tryMergeNumbers(ipr1, [ipr2, ipr3])

        then:
        res.status == ResultStatus.NO_CONTENT
        ipr2.numbers.every { it.number in ipr1.numbers*.number }
        ipr3.numbers.every { it.number in ipr1.numbers*.number }
    }

    void "test merging fields"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        ipr1.status = PhoneRecordStatus.ARCHIVED
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord()
        ipr2.status = PhoneRecordStatus.UNREAD
        IndividualPhoneRecord ipr3 = TestUtils.buildIndPhoneRecord()
        ipr3.status = PhoneRecordStatus.BLOCKED

        when:
        Result res = mergeActionService.tryMergeFields(ipr1, [ipr2, ipr3])

        then:
        ipr1.status == PhoneRecordStatus.ACTIVE
        ipr1.isDeleted == false
        ipr2.isDeleted
        ipr3.isDeleted
    }

    void "test merging overall"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord()

        MockedMethod tryMergeFields = MockedMethod.create(mergeActionService, "tryMergeFields") { Result.void() }
        MockedMethod tryMergeNumbers = MockedMethod.create(mergeActionService, "tryMergeNumbers") { Result.void() }
        MockedMethod tryMergeGroups = MockedMethod.create(mergeActionService, "tryMergeGroups") { Result.void() }
        MockedMethod tryMergeSharing = MockedMethod.create(mergeActionService, "tryMergeSharing") { Result.void() }
        MockedMethod tryMergeRecords = MockedMethod.create(mergeActionService, "tryMergeRecords") { Result.void() }

        when:
        Result res = mergeActionService.tryMerge(ipr1, [ipr2])

        then:
        tryMergeFields.latestArgs == [ipr1, [ipr2]]
        tryMergeNumbers.latestArgs == [ipr1, [ipr2]]
        tryMergeGroups.latestArgs == [ipr1, [ipr2]]
        tryMergeSharing.latestArgs == [ipr1, [ipr2]]
        tryMergeRecords.latestArgs == [ipr1, [ipr2]]
        res.status == ResultStatus.OK
        res.payload == ipr1

        cleanup:
        tryMergeFields?.restore()
        tryMergeNumbers?.restore()
        tryMergeGroups?.restore()
        tryMergeSharing?.restore()
        tryMergeRecords?.restore()
    }

    void "test merging overall with info"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord()

        String name = TestUtils.randString()
        String note = TestUtils.randString()
        MergeIndividualAction a1 = GroovyMock()

        MockedMethod tryMerge = MockedMethod.create(mergeActionService, "tryMerge") { Result.createSuccess(ipr1) }

        when:
        Result res = mergeActionService.tryMergeWithInfo(ipr1, [ipr2], a1)

        then:
        1 * a1.buildName() >> name
        1 * a1.buildNote() >> note
        tryMerge.latestArgs == [ipr1, [ipr2]]
        res.payload == ipr1
        ipr1.name == name
        ipr1.note == note

        cleanup:
        tryMerge?.restore()
    }
}
