package org.textup.util

import com.amazonaws.services.cloudfront.CloudFrontUrlSigner.Protocol
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.*
import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestFor(StorageService)
@TestMixin(HibernateTestMixin)
class StorageServiceSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test uploading"() {
        given:
        UploadItem invalidItem = new UploadItem()
        UploadItem validItem = TestUtils.buildUploadItem(MediaType.IMAGE_PNG)

        service.s3Service = GroovyMock(AmazonS3Client)

        when: "try to upload an invalid upload item"
        Result res = service.upload(invalidItem)

        then: "validation errors"
        0 * service.s3Service._
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == invalidItem.errors.errorCount

        when: "try to upload a valid upload item"
        validItem.isPublic = false
        res = service.upload(validItem)

        then:
        1 * service.s3Service.putObject(*_) >> { PutObjectRequest request ->
            assert request.getCannedAcl() == null
        }
        res.status == ResultStatus.OK

        when: "valid public upload item"
        validItem.isPublic = true
        res = service.upload(validItem)

        then:
        1 * service.s3Service.putObject(*_) >> { PutObjectRequest request ->
            assert request.getCannedAcl() == CannedAccessControlList.PublicRead
        }
        res.status == ResultStatus.OK

        when: "try to upload by supplying each required piece of info"
        res = service.upload(validItem.key, validItem.type.mimeType, false,
            new ByteArrayInputStream(validItem.data))

        then:
        1 * service.s3Service.putObject(*_) >> { PutObjectRequest request ->
            assert request.getCannedAcl() == null
        }
        res.status == ResultStatus.OK

        when: "upload as a public item"
        res = service.upload(validItem.key, validItem.type.mimeType, true,
            new ByteArrayInputStream(validItem.data))

        then:
        1 * service.s3Service.putObject(*_) >> { PutObjectRequest request ->
            assert request.getCannedAcl() == CannedAccessControlList.PublicRead
        }
        res.status == ResultStatus.OK
    }

    void "test uploading batch of items asynchronously"() {
        given: "many upload items"
        List<UploadItem> uItems = []
        int numSuccesses = 5
        numSuccesses.times { uItems << TestUtils.buildUploadItem(MediaType.IMAGE_PNG) }
        int numFailures = 3
        numFailures.times { uItems << new UploadItem() }

        service.s3Service = GroovyMock(AmazonS3Client)

        when: "empty list"
        ResultGroup resGroup = service.uploadAsync(null)

        then:
        resGroup.isEmpty == true

        when: "with items"
        resGroup = service.uploadAsync(uItems)

        then:
        numSuccesses * service.s3Service.putObject(*_)
        resGroup.successes.size() == numSuccesses
        resGroup.failures.size() == numFailures
    }
}
