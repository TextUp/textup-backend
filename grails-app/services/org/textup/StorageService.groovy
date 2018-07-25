package org.textup

import com.amazonaws.services.cloudfront.CloudFrontUrlSigner
import com.amazonaws.services.cloudfront.CloudFrontUrlSigner.Protocol
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectResult
import grails.async.Promises
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.textup.type.MediaType
import org.textup.validator.UploadItem

@GrailsTypeChecked
@Transactional
class StorageService {

    AmazonS3Client s3Service
    GrailsApplication grailsApplication
    ResultFactory resultFactory
    MediaService mediaService

    // Auth link
    // ---------

	Result<URL> generateAuthLink(String identifier) {
		DateTime expires = DateTime.now().plusHours(1)
		generateAuthLink(identifier, expires)
	}
    // It is acceptable to call this method multiple times even for the same object
    // because presigned url is generated locally. Therefore, we don't have to worry
    // about the additional complexity of caching these results because we don't
    // have to worry about the additional performance penalty of network requests.
    Result<URL> generateAuthLink(String identifier, DateTime expires) {
    	try {
            String root = grailsApplication.flatConfig["textup.media.cdn.root"],
                keyId = grailsApplication.flatConfig["textup.media.cdn.keyId"],
                privateKeyPath = grailsApplication.flatConfig["textup.media.cdn.privateKeyPath"]
            File keyFile = new File(privateKeyPath)
            Date expiresAt = expires.toDate()
            resultFactory.success(getSignedLink(Protocol.https, root, keyFile,
                identifier, keyId, expiresAt))
        }
        catch (Throwable e) {
            log.error("StorageService.generateAuthLink: ${e.message}")
            e.printStackTrace()
            resultFactory.failWithThrowable(e, false)
        }
    }
    protected URL getSignedLink(Protocol protocol, String root, File keyFile,
        String identifier, String keyId, Date expiresAt) {
        new URL(CloudFrontUrlSigner.getSignedURLWithCannedPolicy(protocol, root, keyFile,
            identifier, keyId, expiresAt))
    }

    // Uploading
    // ---------

    ResultGroup<PutObjectResult> uploadAsync(List<UploadItem> uItems) {
        if (!uItems) {
            return new ResultGroup<PutObjectResult>()
        }
        Helpers
            .<UploadItem, ResultGroup<?>>doAsyncInBatches(itemsToUpload, { List<UploadItem> batch ->
                Promises.task {
                    new ResultGroup<PutObjectResult>(batch.collect(storageService.&upload))
                }
            }, Constants.CONCURRENT_UPLOAD_BATCH_SIZE)
            .inject(new ResultGroup<PutObjectResult>(),
                { ResultGroup<PutObjectResult> res, ResultGroup<PutObjectResult> i -> res.merge(i) })
    }
    Result<PutObjectResult> upload(UploadItem uItem) {
        if (uItem.validate()) {
            new ByteArrayInputStream(uItem.data).withCloseable { ByteArrayInputStream bStream ->
                upload(uItem.key, uItem.mimeType, bStream)
            }
        }
        else { resultFactory.failWithValidationErrors(uItem.errors, false) }
    }
    Result<PutObjectResult> upload(String identifier, String mimeType, InputStream stream) {
        String bucketName = grailsApplication.flatConfig["textup.media.bucketName"]
        try {
            ObjectMetadata metadata = new ObjectMetadata()
            metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION)
            metadata.setContentType(mimeType)
            resultFactory.success(s3Service.putObject(bucketName, identifier, stream, metadata))
        }
        catch (Throwable e) {
            resultFactory.failWithThrowable(e, false)
        }
    }
}
