import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.pusher.rest.Pusher
import com.twilio.sdk.TwilioRestClient
import org.textup.*
import org.textup.rest.*
import org.textup.rest.marshallers.*
import org.textup.util.*

// Place your Spring DSL code here
beans = {

	// Spring AOP
	// ----------

	xmlns aop:"http://www.springframework.org/schema/aop"
	optimisticLockingRetryAspect(OptimisticLockingRetryAspect)
	aop.config("proxy-target-class":true)

	// src/groovy Helpers
	// ------------------

	def tConfig = application.config.textup
	def tRestConfig = tConfig.rest
	def apiConfig = tConfig.apiKeys
	def restConfig = application.config.grails.plugin.springsecurity.rest.token
	String v1Namespace = "v1"

	s3Service(AmazonS3Client,
		new BasicAWSCredentials(apiConfig.aws.accessKey, apiConfig.aws.secretKey))
	pusherService(Pusher, apiConfig.pusher.appId, apiConfig.pusher.apiKey, apiConfig.pusher.apiSecret) {
		encrypted = true
	}
	twilioService(TwilioRestClient, apiConfig.twilio.sid, apiConfig.twilio.authToken)
	resultFactory(ResultFactory) { bean ->
		bean.autowire = true
	}
	twimlBuilder(TwimlBuilder) { bean ->
		bean.autowire = true
	}
	restAuthenticationTokenJsonRenderer(CustomRestAuthenticationTokenJsonRenderer) {
		usernamePropertyName = restConfig.rendering.usernamePropertyName
		tokenPropertyName = restConfig.rendering.tokenPropertyName
		authoritiesPropertyName = restConfig.rendering.authoritiesPropertyName
		useBearerToken = restConfig.validation.useBearerToken
	}

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

	recordRenderer(ApiJsonRenderer, RecordItem) {
		label = tRestConfig.v1.record.singular
	}
	recordCollectionRenderer(ApiJsonCollectionRenderer, RecordItem) {
		label = tRestConfig.v1.record.plural
	}
	recordJsonMarshaller(RecordItemJsonMarshaller) {
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
}
