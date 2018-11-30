package org.textup

import com.amazonaws.services.cloudfront.CloudFrontUrlSigner.Protocol
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import org.textup.type.*
import org.textup.util.TestUtils
import org.textup.validator.*
import spock.lang.Specification

@TestFor(StorageService)
@Domain([Record, RecordItem, RecordText, RecordCall, RecordItemReceipt,
    RecordNote, RecordNoteRevision, Location, Organization,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class StorageServiceSpec extends Specification {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

	void setup() {
		service.resultFactory = TestUtils.getResultFactory(grailsApplication)
        service.grailsApplication = grailsApplication
	}

    void "test uploading"() {
        given:
        service.s3Service = Mock(AmazonS3Client)
        UploadItem invalidItem = new UploadItem()
        assert invalidItem.validate() == false

        byte[] inputData1 = TestUtils.getPngSampleData()
        UploadItem validItem = new UploadItem(type: MediaType.IMAGE_PNG, data: inputData1)
        assert validItem.validate() == true

        when: "try to upload an invalid upload item"
        Result<PutObjectResult> res = service.upload(invalidItem)

        then: "validation errors"
        0 * service.s3Service._
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == invalidItem.errors.errorCount

        when: "try to upload a valid upload item"
        validItem.isPublic = false
        res = service.upload(validItem)

        then:
        1 * service.s3Service.putObject(*_) >> { PutObjectRequest request ->
            assert request.getCannedAcl() == null
        }
        res.status == ResultStatus.OK

        when: "valid public upload item"
        validItem.isPublic = true
        res = service.upload(validItem)

        then:
        1 * service.s3Service.putObject(*_) >> { PutObjectRequest request ->
            assert request.getCannedAcl() == CannedAccessControlList.PublicRead
        }
        res.status == ResultStatus.OK

        when: "try to upload by supplying each required piece of info"
        res = service.upload(validItem.key, validItem.type.mimeType, false,
            new ByteArrayInputStream(validItem.data))

        then:
        1 * service.s3Service.putObject(*_) >> { PutObjectRequest request ->
            assert request.getCannedAcl() == null
        }
        res.status == ResultStatus.OK

        when: "upload as a public item"
        res = service.upload(validItem.key, validItem.type.mimeType, true,
            new ByteArrayInputStream(validItem.data))

        then:
        1 * service.s3Service.putObject(*_) >> { PutObjectRequest request ->
            assert request.getCannedAcl() == CannedAccessControlList.PublicRead
        }
        res.status == ResultStatus.OK
    }

    void "test uploading batch of items asynchronously"() {
        given: "many upload items"
        service.s3Service = Mock(AmazonS3Client)
        List<UploadItem> uItems = []
        int numSuccesses = 5
        int numFailures = 3
        byte[] inputData1 = TestUtils.getPngSampleData()
        numSuccesses.times {
            uItems << new UploadItem(type: MediaType.IMAGE_PNG, data: inputData1)
        }
        numFailures.times { uItems << new UploadItem() }

        when: "empty list"
        ResultGroup<PutObjectResult> resGroup = service.uploadAsync(null)

        then:
        resGroup.isEmpty == true

        when: "with items"
        resGroup = service.uploadAsync(uItems)

        then:
        numSuccesses * service.s3Service.putObject(*_)
        resGroup.successes.size() == numSuccesses
        resGroup.failures.size() == numFailures
    }
}
