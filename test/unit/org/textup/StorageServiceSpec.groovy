package org.textup

import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import grails.test.mixin.TestFor
import org.joda.time.DateTime
import spock.lang.Specification

@TestFor(StorageService)
class StorageServiceSpec extends Specification {

	String _bucketName
	String _key
	Date _expiration
	Map<String,String> _reqHeaders

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

	void setup() {
		service.resultFactory =
			grailsApplication.mainContext.getBean("resultFactory")
		service.s3Service = [generatePresignedUrl:{
			GeneratePresignedUrlRequest req ->
			_bucketName = req.bucketName
			_key = req.key
			_expiration = req.expiration
			_reqHeaders = req.customRequestHeaders
			new URL("http://www.example.com")
		}] as AmazonS3Client
	}

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
}
