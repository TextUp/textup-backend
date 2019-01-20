package org.textup.util

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.*
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.textup.validator.UploadItem
import org.textup.util.*

@GrailsTypeChecked
@Transactional
class StorageService {

    AmazonS3Client s3Service
    GrailsApplication grailsApplication

    ResultGroup<?> uploadAsync(Collection<UploadItem> uItems) {
        ResultGroup<PutObjectResult> resGroup = new ResultGroup<>()
        if (uItems) {
            resGroup << AsyncUtils.<UploadItem>doAsyncInBatches(uItems,
                AsyncUtils.UPLOAD_BATCH_SIZE) { UploadItem uItem -> upload(uItem) }
        }
        resGroup
    }

    Result<?> upload(UploadItem uItem) {
        DomainUtils.tryValidate(uItem).then {
            new ByteArrayInputStream(uItem.data).withStream { InputStream bStream ->
                upload(uItem.key, uItem.type?.mimeType, uItem.isPublic, bStream)
            }
        }
    }

    Result<?> upload(String identifier, String mimeType, boolean isPublic, InputStream stream) {
        String bucketName = grailsApplication.flatConfig["textup.media.bucketName"]
        try {
            ObjectMetadata metadata = new ObjectMetadata()
            metadata.with {
                setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION)
                setContentType(mimeType)
            }
            PutObjectRequest request = new PutObjectRequest(bucketName, identifier, stream, metadata)
            // Only set an object-level ACL if the object is public.
            // Don't specify an object-level ACL here for private items because when we adjust
            // the bucket-level ACL its scope of effect will be overridden by these object-level ACLs
            if (isPublic) {
                request.withCannedAcl(CannedAccessControlList.PublicRead)
            }
            IOCUtils.resultFactory.success(s3Service.putObject(request))
        }
        catch (Throwable e) {
            IOCUtils.resultFactory.failWithThrowable(e, "upload")
        }
    }
}
