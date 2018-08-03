package org.textup

import com.amazonaws.services.cloudfront.CloudFrontUrlSigner.Protocol
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectResult
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import java.nio.charset.StandardCharsets
import javax.servlet.http.HttpServletRequest
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.type.*
import org.textup.util.TestHelpers
import org.textup.validator.*
import spock.lang.Specification

@TestFor(StorageService)
@Domain([Record, RecordItem, RecordText, RecordCall, RecordItemReceipt,
    RecordNote, RecordNoteRevision, Location, Organization,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class StorageServiceSpec extends Specification {

	String _objectKey
	Date _expiration
    String _signedUrl
    String _eTag = UUID.randomUUID().toString()

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

	void setup() {
		service.resultFactory = grailsApplication.mainContext.getBean("resultFactory")
		service.s3Service = [
            putObject: { String bucket, String key, InputStream stream, ObjectMetadata meta ->
                [getETag: { -> _eTag }] as PutObjectResult
            }
        ] as AmazonS3Client
        service.grailsApplication = [
            getFlatConfig: { ->
                [
                    "textup.media.bucketName": "testing-bucket",
                    "textup.media.cdn.root": "testing.textup.org",
                    "textup.media.cdn.keyId": "testingKey",
                    "textup.media.cdn.privateKeyPath": "/test-key.der"
                ]
            }
        ] as GrailsApplication
        service.metaClass.getSignedLink = { Protocol protocol, String root, File keyFile,
            String objectKey, String keyId, Date expiresAt ->
            _objectKey = objectKey
            _expiration = expiresAt
            new URL(_signedUrl)
        }
	}

    // Authenticated links
    // -------------------

    void "test generating authenticated links"() {
    	when:
        _objectKey = null
        _expiration = null
        _signedUrl = "https://www.example.com"
    	String key = "mykey"
    	DateTime expires = DateTime.now().plusDays(1)
    	Result<URL> res = service.generateAuthLink(key, expires)

    	then:
    	res.success == true
        res.status == ResultStatus.OK
    	res.payload instanceof URL
    	_signedUrl == res.payload.toString()
    	_objectKey == key
    	expires.isEqual(_expiration.time)
    }

    // Uploading
    // ---------

    void "test uploading"() {
        given:
        UploadItem invalidItem = new UploadItem()
        assert invalidItem.validate() == false

        byte[] inputData1 = TestHelpers.getPngSampleData()
        UploadItem validItem = new UploadItem(mediaVersion: MediaVersion.SEND,
            mimeType: Constants.MIME_TYPE_PNG,
            data: inputData1)
        assert validItem.validate() == true

        when: "try to upload an invalid upload item"
        Result<PutObjectResult> res = service.upload(invalidItem)

        then: "validation errors"
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == invalidItem.errors.errorCount

        when: "try to upload a valid upload item"
        res = service.upload(validItem)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof PutObjectResult
        res.payload.getETag() == _eTag

        when: "try to upload by supplying each required piece of info"
        res = service.upload(validItem.key, validItem.mimeType,
            new ByteArrayInputStream(validItem.data))

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof PutObjectResult
        res.payload.getETag() == _eTag
    }

    void "test uploading batch of items asynchronously"() {
        given: "many upload items"
        List<UploadItem> uItems = []
        int numSuccesses = 5
        int numFailures = 3
        byte[] inputData1 = TestHelpers.getPngSampleData()
        numSuccesses.times {
            uItems << new UploadItem(mediaVersion: MediaVersion.SEND,
                mimeType: Constants.MIME_TYPE_PNG,
                data: inputData1)
        }
        numFailures.times { uItems << new UploadItem() }

        when: "empty list"
        ResultGroup<PutObjectResult> resGroup = service.uploadAsync(null)

        then:
        resGroup.isEmpty == true

        when: "with items"
        resGroup = service.uploadAsync(uItems)

        then:
        resGroup.isEmpty == false
        resGroup.successes.size() == numSuccesses
        resGroup.successes.every { it.payload instanceof PutObjectResult }
        resGroup.failures.size() == numFailures
    }
}
