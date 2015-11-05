import org.textup.*
import org.textup.util.*
import org.textup.rest.*
import org.textup.rest.marshallers.*

// Place your Spring DSL code here
beans = {
	def tRestConfig = application.config.textup.rest
	def restConfig = application.config.grails.plugin.springsecurity.rest.token
	String v1Namespace = "v1"

	resultFactory(ResultFactory) { bean ->
		bean.autowire = true 
	}
	customRestAuthenticationTokenJsonRenderer(CustomRestAuthenticationTokenJsonRenderer) { 
		usernamePropertyName = restConfig.rendering.usernamePropertyName
		tokenPropertyName = restConfig.rendering.authoritiesPropertyName 
		authoritiesPropertyName = restConfig.rendering.tokenPropertyName 
		useBearerToken = restConfig.validation.useBearerToken 
	}

	/////////////////
	// Marshallers //
	/////////////////

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
}
