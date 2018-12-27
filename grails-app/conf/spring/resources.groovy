import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.pusher.rest.Pusher
import org.textup.*
import org.textup.cache.*
import org.textup.media.*
import org.textup.rest.*
import org.textup.rest.marshaller.*
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
		cacheNameToMaxSize = [(Constants.CACHE_RECEIPTS): Constants.CACHE_RECEIPTS_MAX_SIZE]
	}
	receiptCache(RecordItemReceiptCache)

	// Marshallers
	// -----------

	contactableRenderer(ApiJsonRenderer, Contactable) {
		label = tRestConfig.v1.contact.singular
	}
	contactableCollectionRenderer(ApiJsonCollectionRenderer, Contactable) {
		label = tRestConfig.v1.contact.plural
	}
	contactableJsonMarshaller(ContactableJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	tagRenderer(ApiJsonRenderer, ContactTag) {
		label = tRestConfig.v1.tag.singular
	}
	tagCollectionRenderer(ApiJsonCollectionRenderer, ContactTag) {
		label = tRestConfig.v1.tag.plural
	}
	tagJsonMarshaller(ContactTagJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	organizationRenderer(ApiJsonRenderer, Organization) {
		label = tRestConfig.v1.organization.singular
	}
	organizationCollectionRenderer(ApiJsonCollectionRenderer, Organization) {
		label = tRestConfig.v1.organization.plural
	}
	organizationJsonMarshaller(OrganizationJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	locationRenderer(ApiJsonRenderer, ReadOnlyLocation) {
		label = tRestConfig.v1.location.singular
	}
	locationCollectionRenderer(ApiJsonCollectionRenderer, ReadOnlyLocation) {
		label = tRestConfig.v1.location.plural
	}
	locationJsonMarshaller(LocationJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	phoneRenderer(ApiJsonRenderer, Phone) {
		label = tRestConfig.v1.phone.singular
	}
	phoneCollectionRenderer(ApiJsonCollectionRenderer, Phone) {
		label = tRestConfig.v1.phone.plural
	}
	phoneJsonMarshaller(PhoneJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	recordRenderer(ApiJsonRenderer, ReadOnlyRecordItem) {
		label = tRestConfig.v1.record.singular
	}
	recordCollectionRenderer(ApiJsonCollectionRenderer, ReadOnlyRecordItem) {
		label = tRestConfig.v1.record.plural
	}
	recordJsonMarshaller(RecordItemJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	recordItemStatusRenderer(ApiJsonRenderer, RecordItemStatus) {
		label = tRestConfig.v1.recordItemStatus.singular
	}
	recordItemStatusCollectionRenderer(ApiJsonCollectionRenderer, RecordItemStatus) {
		label = tRestConfig.v1.recordItemStatus.plural
	}
	recordItemStatusJsonMarshaller(RecordItemStatusJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	recordItemRequestRenderer(ApiJsonRenderer, RecordItemRequest) {
		label = tRestConfig.v1.recordItemRequest.singular
	}
	recordItemRequestCollectionRenderer(ApiJsonCollectionRenderer, RecordItemRequest) {
		label = tRestConfig.v1.recordItemRequest.plural
	}
	recordItemRequestJsonMarshaller(RecordItemRequestJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	revisionRenderer(ApiJsonRenderer, ReadOnlyRecordNoteRevision) {
		label = tRestConfig.v1.revision.singular
	}
	revisionCollectionRenderer(ApiJsonCollectionRenderer, ReadOnlyRecordNoteRevision) {
		label = tRestConfig.v1.revision.plural
	}
	revisionJsonMarshaller(RecordNoteRevisionJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	mediaInfoRenderer(ApiJsonRenderer, ReadOnlyMediaInfo) {
		label = tRestConfig.v1.mediaInfo.singular
	}
	mediaInfoCollectionRenderer(ApiJsonCollectionRenderer, ReadOnlyMediaInfo) {
		label = tRestConfig.v1.mediaInfo.plural
	}
	mediaInfoJsonMarshaller(MediaInfoJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	mediaElementRenderer(ApiJsonRenderer, ReadOnlyMediaElement) {
		label = tRestConfig.v1.mediaElement.singular
	}
	mediaElementCollectionRenderer(ApiJsonCollectionRenderer, ReadOnlyMediaElement) {
		label = tRestConfig.v1.mediaElement.plural
	}
	mediaElementJsonMarshaller(MediaElementJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	futureMessageRenderer(ApiJsonRenderer, ReadOnlyFutureMessage) {
		label = tRestConfig.v1.futureMessage.singular
	}
	futureMessageCollectionRenderer(ApiJsonCollectionRenderer, ReadOnlyFutureMessage) {
		label = tRestConfig.v1.futureMessage.plural
	}
	futureMessageJsonMarshaller(FutureMessageJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	staffRenderer(ApiJsonRenderer, Staff) {
		label = tRestConfig.v1.staff.singular
	}
	staffCollectionRenderer(ApiJsonCollectionRenderer, Staff) {
		label = tRestConfig.v1.staff.plural
	}
	staffJsonMarshaller(StaffJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	scheduleRenderer(ApiJsonRenderer, Schedule) {
		label = tRestConfig.v1.schedule.singular
	}
	scheduleCollectionRenderer(ApiJsonCollectionRenderer, Schedule) {
		label = tRestConfig.v1.schedule.plural
	}
	scheduleJsonMarshaller(ScheduleJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	teamRenderer(ApiJsonRenderer, Team) {
		label = tRestConfig.v1.team.singular
	}
	teamCollectionRenderer(ApiJsonCollectionRenderer, Team) {
		label = tRestConfig.v1.team.plural
	}
	teamJsonMarshaller(TeamJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	sessionRenderer(ApiJsonRenderer, IncomingSession) {
		label = tRestConfig.v1.session.singular
	}
	sessionCollectionRenderer(ApiJsonCollectionRenderer, IncomingSession) {
		label = tRestConfig.v1.session.plural
	}
	sessionJsonMarshaller(SessionJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	announcementRenderer(ApiJsonRenderer, FeaturedAnnouncement) {
		label = tRestConfig.v1.announcement.singular
	}
	announcementCollectionRenderer(ApiJsonCollectionRenderer, FeaturedAnnouncement) {
		label = tRestConfig.v1.announcement.plural
	}
	announcementJsonMarshaller(AnnouncementJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	notificationRenderer(ApiJsonRenderer, Notification) {
		label = tRestConfig.v1.notification.singular
	}
	notificationCollectionRenderer(ApiJsonCollectionRenderer, Notification) {
		label = tRestConfig.v1.notification.plural
	}
	notificationJsonMarshaller(NotificationJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	notificationStatusRenderer(ApiJsonRenderer, NotificationStatus) {
		label = tRestConfig.v1.notificationStatus.singular
	}
	notificationStatusCollectionRenderer(ApiJsonCollectionRenderer, NotificationStatus) {
		label = tRestConfig.v1.notificationStatus.plural
	}
	notificationStatusJsonMarshaller(NotificationStatusJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	staffPolicyAvailabilityRenderer(ApiJsonRenderer, StaffPolicyAvailability) {
		label = tRestConfig.v1.staffPolicyAvailability.singular
	}
	staffPolicyAvailabilityCollectionRenderer(ApiJsonCollectionRenderer, StaffPolicyAvailability) {
		label = tRestConfig.v1.staffPolicyAvailability.plural
	}
	staffPolicyAvailabilityJsonMarshaller(StaffPolicyAvailabilityJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	availableNumberRenderer(ApiJsonRenderer, AvailablePhoneNumber) {
		label = tRestConfig.v1.availableNumber.singular
	}
	availableNumberCollectionRenderer(ApiJsonCollectionRenderer, AvailablePhoneNumber) {
		label = tRestConfig.v1.availableNumber.plural
	}
	availableNumberJsonMarshaller(AvailablePhoneNumberJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}

	mergeGroupRenderer(ApiJsonRenderer, MergeGroup) {
		label = tRestConfig.v1.mergeGroup.singular
	}
	mergeGroupCollectionRenderer(ApiJsonCollectionRenderer, MergeGroup) {
		label = tRestConfig.v1.mergeGroup.plural
	}
	mergeGroupJsonMarshaller(MergeGroupJsonMarshaller) {
		name = tRestConfig.defaultLabel
		namespace = v1Namespace
	}
}
