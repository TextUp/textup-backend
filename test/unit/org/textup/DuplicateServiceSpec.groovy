package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestFor(DuplicateService)
class DuplicateServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test finding duplicates"() {
        given:
        Long pId = TestUtils.randIntegerUpTo(88)
        Long iprId = TestUtils.randIntegerUpTo(88)
        Map numToIds = [(TestUtils.randPhoneNumber()): [TestUtils.randIntegerUpTo(88)]]
        MergeGroup mGroup1 = GroovyMock()
        MockedMethod findNumToIdByPhoneIdAndOptions = MockedMethod.create(IndividualPhoneRecords, "findNumToIdByPhoneIdAndOptions") {
            numToIds
        }
        MockedMethod tryBuildMergeGroups = MockedMethod.create(DuplicateUtils, "tryBuildMergeGroups") {
            Result.createSuccess(mGroup1).toGroup()
        }

        when:
        Result res = service.findDuplicates(pId, [iprId])

        then:
        findNumToIdByPhoneIdAndOptions.latestArgs == [pId, [iprId]]
        tryBuildMergeGroups.latestArgs == [numToIds]
        res.status == ResultStatus.OK
        res.payload == [mGroup1]

        cleanup:
        findNumToIdByPhoneIdAndOptions?.restore()
        tryBuildMergeGroups?.restore()
    }
}
