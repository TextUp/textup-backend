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

    // Need to declare id for it to be considered in equality operator
    // see: https://stokito.wordpress.com/2014/12/19/equalsandhashcode-on-grails-domains/
    Long id

    DateTime whenCreated = JodaUtils.utcNow()
    MediaElementVersion sendVersion
    String uid = UUID.randomUUID().toString()

    static hasMany = [alternateVersions: MediaElementVersion]
    static mapping = {
        whenCreated type: PersistentDateTime
        sendVersion fetch: "join", cascade: "save-update"
        // [NOTE] one-to-many relationships should not have `fetch: "join"` because of GORM using
        // a left outer join to fetch the data runs into issues when a max is provided
        // see: https://stackoverflow.com/a/25426734
        alternateVersions cascade: "save-update"
    }
    static constraints = { // all nullable:false by default
        sendVersion nullable: true, cascadeValidation: true, validator: { MediaElementVersion send1 ->
            if (send1 && send1.sizeInBytes > ValidationUtils.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES) {
                return ["mediaElement.sendVersion.sizeTooBig"]
            }
        }
        alternateVersions nullable: true, cascadeValidation: true
    }

    static Result<MediaElement> tryCreate(Collection<UploadItem> alternates,
        UploadItem sVersion = null) {

        MediaElement e1 = new MediaElement(sendVersion: MediaElementVersion.createIfPresent(sVersion))
        alternates?.each { UploadItem uItem ->
            e1.addToAlternateVersions(MediaElementVersion.createIfPresent(uItem))
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
