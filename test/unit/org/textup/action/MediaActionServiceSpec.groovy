package org.textup.action

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.textup.*
import org.textup.media.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import org.textup.validator.action.*
import spock.lang.*

@TestFor(MediaActionService)
class MediaActionServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test has actions"() {
        expect:
        service.hasActions(doMediaActions: "ok")
        service.hasActions(doMediaActions: null) == false
    }

    void "test handling actions"() {
        given:
        String str1 = TestUtils.randString()
        String uid = TestUtils.randString()
        MediaType mType = MediaType.values()[0]
        byte[] data = TestUtils.randString().bytes

        MediaInfo mInfo = GroovyMock()
        MediaAction a1 = GroovyMock()
        MediaAction a2 = GroovyMock()
        MediaAction uItem1 = GroovyMock()
        MockedMethod tryProcess = MockedMethod.create(ActionContainer, "tryProcess") {
            Result.createSuccess([a1, a2])
        }
        MockedMethod buildInitialData = MockedMethod.create(MediaPostProcessor, "buildInitialData") {
            Result.createError([], ResultStatus.UNPROCESSABLE_ENTITY)
        }

        when:
        Result res = service.tryHandleActions(mInfo, [doMediaActions: str1])

        then:
        1 * a1.toString() >> MediaAction.ADD
        tryProcess.callCount == 1
        tryProcess.latestArgs == [MediaAction, str1]
        buildInitialData.callCount == 1
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        buildInitialData = MockedMethod.create(buildInitialData) { Result.createSuccess(uItem1) }
        res = service.tryHandleActions(mInfo, [doMediaActions: str1])

        then:
        1 * a1.toString() >> MediaAction.ADD
        1 * a1.buildType() >> mType
        1 * a1.buildByteData() >> data
        1 * a2.toString() >> MediaAction.REMOVE
        1 * a2.uid >> uid
        1 * mInfo.removeMediaElement(uid)
        tryProcess.callCount == 2
        tryProcess.latestArgs == [MediaAction, str1]
        buildInitialData.callCount == 1
        buildInitialData.latestArgs == [mType, data]
        res.status == ResultStatus.OK
        res.payload == [uItem1]

        cleanup:
        tryProcess?.restore()
        buildInitialData?.restore()
    }
}
