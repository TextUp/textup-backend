package org.textup

import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
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

	String _bucketName
	String _key
	Date _expiration
	Map<String,String> _reqHeaders
    boolean _didUpdateRequest = false
    String _eTag = UUID.randomUUID().toString()

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

	void setup() {
		service.resultFactory =
			grailsApplication.mainContext.getBean("resultFactory")
		service.s3Service = [
            generatePresignedUrl: { GeneratePresignedUrlRequest req ->
    			_bucketName = req.bucketName
    			_key = req.key
    			_expiration = req.expiration
    			_reqHeaders = req.customRequestHeaders
    			new URL("http://www.example.com")
    		},
            putObject: { String bucket, String key, InputStream stream,
                ObjectMetadata meta ->
                [getETag: { -> _eTag }] as PutObjectResult
            }
        ] as AmazonS3Client
        service.grailsApplication = [
            getFlatConfig: { ->
                ["textup.storageBucketName":"testing-bucket"]
            }
        ] as GrailsApplication
	}

    // Authenticated links
    // -------------------

    void "test generating authenticated links"() {
    	when:
    	String bucket = "mybucket"
    	String key = "mykey"
    	DateTime expires = DateTime.now().plusDays(1)
    	Map params = ["Content-Length": 800, "Content-Type":"image/png"]
    	Result<URL> res = service.generateAuthLink(bucket, key,
    		HttpMethod.GET, expires, params)

    	then:
    	res.success == true
    	res.payload instanceof URL
    	_bucketName == bucket
    	_key == key
    	expires.isEqual(_expiration.time)
    	_reqHeaders.each { String k, String v ->
    		assert params[k] != null
    	}
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
        Result<PutObjectResult> res = service.upload(itemKey, invalidItem)

        then: "validation errors"
        res.success == false
        res.type == ResultType.VALIDATION
        res.payload.errorCount == invalidItem.errors.errorCount

        when: "try to upload a valid upload item"
        res = service.upload(itemKey, validItem)

        then:
        res.success == true
        res.payload instanceof PutObjectResult
        res.payload.getETag() == _eTag

        when: "try to upload by supplying each required piece of info"
        res = service.upload(itemKey, contentType,
            new ByteArrayInputStream(Base64.decodeBase64(data)))

        then:
        res.success == true
        res.payload instanceof PutObjectResult
        res.payload.getETag() == _eTag
    }
}
