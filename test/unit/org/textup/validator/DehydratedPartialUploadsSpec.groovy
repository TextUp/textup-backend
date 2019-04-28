package org.textup.validator

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

@Domain([CustomAccountDetails, MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class DehydratedPartialUploadsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation + rehydration"() {
        given:
        UploadItem uItem1 = TestUtils.buildUploadItem()
        UploadItem uItem2 = TestUtils.buildUploadItem()
        PartialUploads pu1 = PartialUploads.tryCreate([uItem2, uItem1]).payload

        when:
        Result res = DehydratedPartialUploads.tryCreate(null)

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR

        when:
        res = DehydratedPartialUploads.tryCreate(pu1)

        then:
        res.status == ResultStatus.CREATED
        res.payload instanceof DehydratedPartialUploads
        res.payload.validate()

        when:
        res = res.payload.tryRehydrate()

        then:
        res.status == ResultStatus.CREATED
        res.payload == pu1
    }
}
