import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.pusher.rest.Pusher
import org.textup.*
import org.textup.annotation.*
import org.textup.cache.*
import org.textup.media.*
import org.textup.override.*
import org.textup.rest.*
import org.textup.rest.marshaller.*
import org.textup.structure.*
import org.textup.util.*
import org.textup.validator.*

// Place your Spring DSL code here
beans = {

	// Spring AOP
	// ----------

	xmlns aop:"http://www.springframework.org/schema/aop"
	optimisticLockingRetryAspect(OptimisticLockingRetryAspect)
	rollbackOnResultFailureAspect(RollbackOnResultFailureAspect)
	aop.config("proxy-target-class":true)

	// src/groovy Helpers
	// ------------------

	def tConfig = application.config.textup
	def audioConfig = tConfig.media.audio
	def tRestConfig = tConfig.rest
	def apiConfig = tConfig.apiKeys
	def restConfig = application.config.grails.plugin.springsecurity.rest.token
	String v1Namespace = "v1"

	s3Service(AmazonS3Client,
		new BasicAWSCredentials(apiConfig.aws.accessKey, apiConfig.aws.secretKey))
	audioUtils(AudioUtils, audioConfig.executableDirectory, audioConfig.executableName,
		tConfig.tempDirectory)
	pusherService(Pusher, apiConfig.pusher.appId, apiConfig.pusher.apiKey, apiConfig.pusher.apiSecret) {
		encrypted = true
	}
	resultFactory(ResultFactory) { bean ->
		bean.autowire = true
	}
	accessTokenJsonRenderer(CustomTokenJsonRenderer) {
		usernamePropertyName = restConfig.rendering.usernamePropertyName
		tokenPropertyName = restConfig.rendering.tokenPropertyName
		authoritiesPropertyName = restConfig.rendering.authoritiesPropertyName
		useBearerToken = restConfig.validation.useBearerToken
	}
	// Override cache manager to support constraining the in-memory cache size
	grailsCacheManager(ConstrainedSizeCacheManager) {
		cacheNameToMaxSize = [
			(Constants.CACHE_RECEIPTS): Constants.CACHE_RECEIPTS_MAX_SIZE,
			(Constants.CACHE_PHONES): Constants.CACHE_PHONES_MAX_SIZE
		]
	}
	receiptCache(RecordItemReceiptCache)

	// Marshallers
	// -----------

	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_ANNOUNCEMENT, FeaturedAnnouncement, AnnouncementJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_CONTACT, PhoneRecordWrapper, PhoneRecordWrapperJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_FUTURE_MESSAGE, ReadOnlyFutureMessage, FutureMessageJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_LOCATION, ReadOnlyLocation, LocationJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_MEDIA_ELEMENT, ReadOnlyMediaElement, MediaElementJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_MEDIA_ELEMENT_VERSION, ReadOnlyMediaElementVersion, MediaElementVersionJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_MEDIA_INFO, ReadOnlyMediaInfo, MediaInfoJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_MERGE_GROUP, MergeGroup, MergeGroupJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_MERGE_GROUP_ITEM, MergeGroupItem, MergeGroupItemJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_NOTIFICATION, Notification, NotificationJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_NOTIFICATION_DETAIL, NotificationDetail, NotificationDetailJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_ORGANIZATION, Organization, OrganizationJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_OWNER_POLICY, OwnerPolicy, OwnerPolicyJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_PHONE, Phone, PhoneJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_PHONE_NUMBER, BasePhoneNumber, BasePhoneNumberJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_RECORD_ITEM, ReadOnlyRecordItem, RecordItemJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_RECORD_ITEM_REQUEST, RecordItemRequest, RecordItemRequestJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_REVISION, ReadOnlyRecordNoteRevision, RecordNoteRevisionJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_SCHEDULE, Schedule, ScheduleJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_SESSION, IncomingSession, SessionJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_STAFF, Staff, StaffJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_TAG, GroupPhoneRecord, GroupPhoneRecordJsonMarshaller)
	MarshallerUtils.setupJsonMarshaller(delegate, MarshallerUtils.KEY_TEAM, Team, TeamJsonMarshaller)
}
