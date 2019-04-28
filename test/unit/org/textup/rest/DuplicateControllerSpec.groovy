package org.textup.rest

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
import spock.lang.*

// For some reason all controllers that have the `@Transactional` annotation must have
// `HibernateTestMixin` mixed for proper initialization of transaction manager
// see https://stackoverflow.com/a/25865276

@TestFor(DuplicateController)
@TestMixin(HibernateTestMixin)
class DuplicateControllerSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test index"() {
        given:
        Long pId = TestUtils.randIntegerUpTo(88)
        Long teamId = TestUtils.randIntegerUpTo(88)
        Long iprId = TestUtils.randIntegerUpTo(88)

        MergeGroup mGroup1 = GroovyMock()
        controller.duplicateService = GroovyMock(DuplicateService)
        MockedMethod tryGetPhoneId = MockedMethod.create(ControllerUtils, "tryGetPhoneId") {
            Result.createSuccess(pId)
        }
        MockedMethod respondWithClosures = MockedMethod.create(controller, "respondWithClosures")

        when:
        params.teamId = teamId
        params."ids[]" = [iprId]
        controller.index()

        then:
        tryGetPhoneId.latestArgs == [teamId]
        1 * controller.duplicateService.findDuplicates(pId, [iprId]) >> Result.createSuccess([mGroup1])
        respondWithClosures.latestArgs[0] instanceof Closure
        respondWithClosures.latestArgs[0].call() == 1
        respondWithClosures.latestArgs[1] instanceof Closure
        respondWithClosures.latestArgs[1].call() == [mGroup1]
        respondWithClosures.latestArgs[2] == TypeMap.create(params)
        respondWithClosures.latestArgs[3] == MarshallerUtils.KEY_MERGE_GROUP

        cleanup:
        tryGetPhoneId?.restore()
        respondWithClosures?.restore()
    }
}
