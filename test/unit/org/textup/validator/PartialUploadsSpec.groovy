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
class PartialUploadsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation"() {
        given:
        UploadItem uItem1 = TestUtils.buildUploadItem()
        UploadItem uItem2 = TestUtils.buildUploadItem()

        when:
        Result res = PartialUploads.tryCreate(null)

        then:
        res.status == ResultStatus.CREATED
        res.payload.elements.isEmpty()
        res.payload.uploads.isEmpty()

        when:
        res = PartialUploads.tryCreate([uItem2, uItem1])

        then:
        res.status == ResultStatus.CREATED
        res.payload.uploads.size() == 2
        uItem1 in res.payload.uploads
        uItem2 in res.payload.uploads
        res.payload.elements.size() == 2
        res.payload.elements.find { it.alternateVersions.find { it.versionId == uItem1.key } }
        res.payload.elements.find { it.alternateVersions.find { it.versionId == uItem2.key } }
    }

    void "test looping through each upload"() {
        given:
        UploadItem uItem1 = TestUtils.buildUploadItem()
        UploadItem uItem2 = TestUtils.buildUploadItem()

        List args = []
        Closure doAction = { arg1, arg2 -> args << [arg1, arg2] }

        when:
        PartialUploads pu1 = PartialUploads.tryCreate([uItem2, uItem1]).payload
        pu1.eachUpload(doAction)

        then:
        args.size() == 2
        args.find { it[0] == uItem1 && it[1].alternateVersions.find { v -> v.versionId == it[0].key } }
        args.find { it[0] == uItem2 && it[1].alternateVersions.find { v -> v.versionId == it[0].key } }
    }
}
