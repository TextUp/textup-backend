package org.textup

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.cloudfront.CloudFrontUrlSigner.Protocol
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
import org.textup.types.ResultType
import org.textup.validator.UploadItem
import spock.lang.Specification
import static org.springframework.http.HttpStatus.*

@TestFor(StorageService)
@Domain([Record, RecordItem, RecordText, RecordCall, RecordItemReceipt,
    RecordNote, RecordNoteRevision, Location])
@TestMixin(HibernateTestMixin)
class StorageServiceSpec extends Specification {

	String _objectKey
	Date _expiration
    String _signedUrl
    String _eTag = UUID.randomUUID().toString()
    boolean _triedToCompress = false

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

	void setup() {
		service.resultFactory =
			grailsApplication.mainContext.getBean("resultFactory")
		service.s3Service = [
            putObject: { String bucket, String key, InputStream stream,
                ObjectMetadata meta ->
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
        service.metaClass.compressIfImage = { UploadItem uItem ->
            _triedToCompress = true
            uItem.stream
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
    	res.payload instanceof URL
    	_signedUrl == res.payload.toString()
    	_objectKey == key
    	expires.isEqual(_expiration.time)
    }

    // Uploading
    // ---------

    void "test uploading"() {
        given:
        String contentType = "image/png"
        String data = Base64.encodeBase64String("hello".getBytes(StandardCharsets.UTF_8))
        String checksum = DigestUtils.md5Hex(data)
        UploadItem invalidItem = new UploadItem(),
            validItem = new UploadItem(mimeType: contentType, data: data, checksum: checksum)
        assert invalidItem.validate() == false
        assert validItem.validate() == true

        when: "try to upload an invalid upload item"
        String itemKey = UUID.randomUUID().toString()
        _triedToCompress = false
        Result<PutObjectResult> res = service.upload(itemKey, invalidItem)

        then: "validation errors"
        res.success == false
        res.type == ResultType.VALIDATION
        res.payload.errorCount == invalidItem.errors.errorCount
        _triedToCompress == false // returned before call to compress happened

        when: "try to upload a valid upload item"
        _triedToCompress = false
        res = service.upload(itemKey, validItem)

        then:
        res.success == true
        res.payload instanceof PutObjectResult
        res.payload.getETag() == _eTag
        _triedToCompress == true

        when: "try to upload by supplying each required piece of info"
        _triedToCompress = false
        res = service.upload(itemKey, contentType,
            new ByteArrayInputStream(Base64.decodeBase64(data)))

        then:
        res.success == true
        res.payload instanceof PutObjectResult
        res.payload.getETag() == _eTag
        _triedToCompress == false // passing each required piece of info bypasses compression
    }
}
