package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class MediaElement implements ReadOnlyMediaElement, WithId, CanSave<MediaElement> {

    DateTime whenCreated = JodaUtils.now()
    MediaElementVersion sendVersion
    String uid = UUID.randomUUID().toString()

    static hasMany = [alternateVersions: MediaElementVersion]
    static mapping = {
        whenCreated type: PersistentDateTime
        sendVersion lazy: false, cascade: "save-update"
        alternateVersions lazy: false, cascade: "save-update"
    }
    static constraints = { // all nullable:false by default
        sendVersion nullable: true, cascadeValidation: true, validator: { MediaElementVersion send1 ->
            if (send1 && send1.sizeInBytes > ValidationUtils.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES) {
                return ["sizeTooBig"]
            }
        }
        alternateVersions nullable: true, cascadeValidation: true
    }

    static Result<MediaElement> tryCreate(UploadItem sVersion, Collection<UploadItem> alternates) {
        MediaElement e1 = new MediaElement(sendVersion: sVersion?.toMediaElementVersion())
        alternates?.each { UploadItem uItem ->
            e1.addToAlternateVersions(uItem.toMediaElementVersion())
        }
        DomainUtils.trySave(e1, ResultStatus.CREATED)
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
        if (sendVersion) {
            allVersions << sendVersion
        }
        alternateVersions?.each { MediaElementVersion vers1 -> allVersions << vers1 }
        allVersions
    }
}
