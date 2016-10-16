package org.textup

import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime

@GrailsTypeChecked
@Transactional
class StorageService {

	AmazonS3Client s3Service
	GrailsApplication grailsApplication
	ResultFactory resultFactory

	Result<URL> generateAuthLink(String objectKey, HttpMethod verb,
    	Map params=[:]) {
		DateTime expires = DateTime.now().plusHours(1)
		generateAuthLink(objectKey, verb, expires, params)
	}
	Result<URL> generateAuthLink(String objectKey,
    	HttpMethod verb, DateTime expires, Map params) {
		String bucket = grailsApplication.flatConfig["textup.storageBucketName"]
		generateAuthLink(bucket, objectKey, verb, expires, params)
	}
    Result<URL> generateAuthLink(String bucket, String objectKey,
    	HttpMethod verb, DateTime expires, Map params) {
    	try {
            GeneratePresignedUrlRequest req =
                new GeneratePresignedUrlRequest(bucket, objectKey)
            req.with {
                method = verb
                expiration = expires.toDate()
            }
            params.each { Object key, Object val ->
                String header = Helpers.toString(key),
                    value = Helpers.toString(val)
            	if (header == 'Content-Type') {
            		req.contentType = val
        		}
        		else { req.putCustomRequestHeader(header, value) }
            }
            resultFactory.success(s3Service.generatePresignedUrl(req))
        }
        catch (Throwable e) {
            log.error("StorageService.generateAuthLink: ${e.message}")
            return resultFactory.failWithThrowable(e)
        }
    }
}
