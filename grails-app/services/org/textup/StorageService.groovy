package org.textup

import com.amazonaws.services.cloudfront.CloudFrontUrlSigner
import com.amazonaws.services.cloudfront.CloudFrontUrlSigner.Protocol
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectResult
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.textup.validator.UploadItem

@GrailsTypeChecked
@Transactional
class StorageService {

	AmazonS3Client s3Service
	GrailsApplication grailsApplication
	ResultFactory resultFactory

    // Auth link
    // ---------

	Result<URL> generateAuthLink(String objectKey) {
		DateTime expires = DateTime.now().plusHours(1)
		generateAuthLink(objectKey, expires)
	}
    // It is acceptable to call this method multiple times even for the same object
    // because presigned url is generated locally. Therefore, we don't have to worry
    // about the additional complexity of caching these results because we don't
    // have to worry about the additional performance penalty of network requests.
    Result<URL> generateAuthLink(String objectKey, DateTime expires) {
    	try {
            String root = grailsApplication.flatConfig["textup.media.cdn.root"],
                keyId = grailsApplication.flatConfig["textup.media.cdn.keyId"],
                privateKeyPath = grailsApplication.flatConfig["textup.media.cdn.privateKeyPath"]
            File keyFile = new File(privateKeyPath)
            Date expiresAt = expires.toDate()
            resultFactory.success(getSignedLink(Protocol.https, root, keyFile,
                objectKey, keyId, expiresAt))
        }
        catch (Throwable e) {
            log.error("StorageService.generateAuthLink: ${e.message}")
            return resultFactory.failWithThrowable(e)
        }
    }
    protected URL getSignedLink(Protocol protocol, String root, File keyFile,
        String objectKey, String keyId, Date expiresAt) {
        new URL(CloudFrontUrlSigner.getSignedURLWithCannedPolicy(protocol, root, keyFile,
            objectKey, keyId, expiresAt))
    }

    // Uploading
    // ---------

    Result<PutObjectResult> upload(String objectKey, UploadItem uItem) {
        if (uItem.validate()) {
            upload(objectKey, uItem.mimeType, compressIfImage(uItem))
        }
        else { resultFactory.failWithValidationErrors(uItem.errors) }
    }
    Result<PutObjectResult> upload(String objectKey, String mimeType, InputStream stream) {
        String bucketName = grailsApplication.flatConfig["textup.media.bucketName"]
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
    protected ByteArrayInputStream compressIfImage(UploadItem uItem) {
        Float quality = Helpers.to(Float, grailsApplication
            .flatConfig["textup.imageCompressionQualty"]) ?: 0.5f
        Helpers.tryCompressUploadItemStream(uItem, quality)
    }
}
