package org.textup

import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectResult
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.textup.validator.UploadItem
import static org.springframework.http.HttpStatus.*

@GrailsTypeChecked
@Transactional
class StorageService {

	AmazonS3Client s3Service
	GrailsApplication grailsApplication
	ResultFactory resultFactory

    // Auth link
    // ---------

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
    // It is acceptable to call this method multiple times even for the same object
    // because presigned url is generated locally. Therefore, we don't have to worry
    // about the additional complexity of caching these results because we don't
    // have to worry about the additional performance penalty of network requests.
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

    // Uploading
    // ---------

    Result<PutObjectResult> upload(String objectKey, UploadItem uItem) {
        if (uItem.validate()) {
            upload(objectKey, uItem.mimeType, uItem.stream)
        }
        else { resultFactory.failWithValidationErrors(uItem.errors) }
    }
    Result<PutObjectResult> upload(String objectKey, String mimeType, InputStream stream) {
        String bucketName = grailsApplication.flatConfig["textup.storageBucketName"]
        try {
            ObjectMetadata metadata = new ObjectMetadata()
            metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION)
            metadata.setContentType(mimeType)
            resultFactory.success(s3Service.putObject(bucketName, objectKey, stream, metadata))
        }
        catch (e) {
            resultFactory.failWithThrowable(e)
        }
    }
}
