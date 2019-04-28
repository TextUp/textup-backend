package org.textup.util

import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import groovy.transform.TypeCheckingMode
import org.textup.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class MarshallerUtils {

    static final String MARSHALLER_DEFAULT = "default"
    static final String FALLBACK_SINGULAR = "resource"
    static final String FALLBACK_PLURAL = "resources"
    static final String PARAM_META = "meta"
    static final String PARAM_LINKS = "link"

    static final String KEY_ANNOUNCEMENT = "announcement"
    static final String KEY_CONTACT = "contact"
    static final String KEY_FUTURE_MESSAGE = "futureMessage"
    static final String KEY_LOCATION = "location"
    static final String KEY_MEDIA_ELEMENT = "mediaElement"
    static final String KEY_MEDIA_ELEMENT_VERSION = "mediaElementVersion"
    static final String KEY_MEDIA_INFO = "mediaInfo"
    static final String KEY_MERGE_GROUP = "mergeGroup"
    static final String KEY_MERGE_GROUP_ITEM = "mergeGroupItem"
    static final String KEY_NOTIFICATION = "notification"
    static final String KEY_NOTIFICATION_DETAIL = "notificationDetail"
    static final String KEY_ORGANIZATION = "organization"
    static final String KEY_OWNER_POLICY = "ownerPolicy"
    static final String KEY_PHONE = "phone"
    static final String KEY_PHONE_NUMBER = "phoneNumber"
    static final String KEY_RECORD_ITEM = "recordItem"
    static final String KEY_RECORD_ITEM_REQUEST = "recordItemRequest"
    static final String KEY_REVISION = "revision"
    static final String KEY_SCHEDULE = "schedule"
    static final String KEY_SESSION = "session"
    static final String KEY_STAFF = "staff"
    static final String KEY_TAG = "tag"
    static final String KEY_TEAM = "team"

    static String resolveCodeToSingular(String key) {
        String code = "textup.rest.marshallers.${key}.singular"
        Holders.flatConfig[code] ?: MarshallerUtils.FALLBACK_SINGULAR
    }

    static String resolveCodeToPlural(String key) {
        String code = "textup.rest.marshallers.${key}.plural"
        Holders.flatConfig[code] ?: MarshallerUtils.FALLBACK_PLURAL
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static void setupJsonMarshaller(Object delegate, String key, Class clazz,
        Class<? extends JsonNamedMarshaller> marshallerClazz) {

        ClosureUtils.compose(delegate) {
            "${key}Renderer"(ApiJsonRenderer, clazz) {
                label = MarshallerUtils.resolveCodeToSingular(key)
            }
            "${key}CollectionRenderer"(ApiJsonCollectionRenderer, clazz) {
                label = MarshallerUtils.resolveCodeToPlural(key)
            }
            "${key}JsonMarshaller"(marshallerClazz)
        }
    }

    static Map<String, String> buildLinks(String resourceKey, Long id) {
        [
            self: IOCUtils.linkGenerator.link(id: id,
                resource: resourceKey,
                action: RestUtils.ACTION_GET_SINGLE,
                absolute: false)
        ]
    }
}
