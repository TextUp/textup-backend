package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.*
import org.restapidoc.annotation.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.UploadItem

@GrailsTypeChecked
@EqualsAndHashCode
@RestApiObject(
    name        = "MediaElement",
    description = "A media element contained within a media info object contains various versions optimized for sending or display")
class MediaElement implements ReadOnlyMediaElement, WithId {

    @RestApiObjectField(
        description    = "unique id for this media element, used for deletion",
        allowedType    = "String",
        useForCreation = false)
    String uid = UUID.randomUUID().toString()

    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
    MediaElementVersion sendVersion

    @RestApiObjectFields(params=[
         @RestApiObjectField(
            apiFieldName   = "versions",
            description    = "various versions for display",
            allowedType    = "MediaElementVersion",
            useForCreation = false)
    ])
    static hasMany = [alternateVersions: MediaElementVersion]
    static constraints = { // all nullable:false by default
        sendVersion nullable: true, cascadeValidation: true, validator: { MediaElementVersion send1 ->
            if (send1 && send1.sizeInBytes > Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES) {
                return ["sizeTooBig"]
            }
        }
        alternateVersions nullable: true, cascadeValidation: true
    }
    static mapping = {
        whenCreated type: PersistentDateTime
        sendVersion lazy: false, cascade: "save-update"
        alternateVersions lazy: false, cascade: "save-update"
    }

    // Methods
    // -------

    boolean hasType(Collection<MediaType> typesToCheckFor) {
        HashSet<MediaType> allTypes = getAllTypes()
        typesToCheckFor?.any { MediaType t1 -> allTypes.contains(t1) }
    }

    // Properties
    // ----------

    HashSet<MediaType> getAllTypes() {
        HashSet<MediaType> allTypes = new HashSet<>()
        getAllVersions().collect { MediaElementVersion v1 -> allTypes << v1.type }
        allTypes
    }

    List<MediaElementVersion> getAllVersions() {
        List<MediaElementVersion> allVersions = []
        if (sendVersion) { allVersions << sendVersion }
        alternateVersions?.each { MediaElementVersion vers1 -> allVersions << vers1 }
        allVersions
    }
}
