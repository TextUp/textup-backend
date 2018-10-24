package org.textup

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.*
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.textup.validator.UploadItem

@GrailsTypeChecked
@Transactional
class StorageService {

    AmazonS3Client s3Service
    GrailsApplication grailsApplication
    ResultFactory resultFactory

    ResultGroup<PutObjectResult> uploadAsync(Collection<UploadItem> uItems) {
        if (!uItems) {
            return new ResultGroup<PutObjectResult>()
        }
        List<Result<PutObjectResult>> resList = Helpers.<UploadItem>doAsyncInBatches(uItems,
            this.&upload, Constants.CONCURRENT_UPLOAD_BATCH_SIZE)
        new ResultGroup<PutObjectResult>(resList)
    }
    Result<PutObjectResult> upload(UploadItem uItem) {
        if (uItem.validate()) {
            new ByteArrayInputStream(uItem.data).withStream { InputStream bStream ->
                upload(uItem.key, uItem.type?.mimeType, uItem.isPublic, bStream)
            }
        }
        else { resultFactory.failWithValidationErrors(uItem.errors) }
    }
    Result<PutObjectResult> upload(String identifier, String mimeType, boolean isPublic,
        InputStream stream) {

        String bucketName = grailsApplication.flatConfig["textup.media.bucketName"]
        try {
            ObjectMetadata metadata = new ObjectMetadata()
            metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION)
            metadata.setContentType(mimeType)
            PutObjectRequest request = new PutObjectRequest(bucketName, identifier, stream, metadata)
            // Only set an object-level ACL if the object is public.
            // Don't specify an object-level ACL here for private items because when we adjust
            // the bucket-level ACL its scope of effect will be overridden by these object-level ACLs
            if (isPublic) { request.withCannedAcl(CannedAccessControlList.PublicRead) }
            resultFactory.success(s3Service.putObject(request))
        }
        catch (Throwable e) {
            log.error("StorageService.upload: could not upload: ${e.message}")
            resultFactory.failWithThrowable(e)
        }
    }
}
