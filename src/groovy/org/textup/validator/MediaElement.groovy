package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.textup.Constants
import org.textup.Helpers
import org.textup.type.MediaType
import org.textup.type.MediaVersion

@GrailsCompileStatic
@EqualsAndHashCode
@ToString
@Validateable
final class MediaElement implements Serializable {

    private static final long serialVersionUID = 1L

    String uid = UUID.randomUUID().toString()
    MediaType type
    EnumMap<MediaVersion, MediaElementVersion> versions = new EnumMap<>(MediaVersion)

    static constraints = { // all nullable:false by default
        versions validator: { EnumMap<MediaVersion, MediaElementVersion> vMap ->
            // must have a "send" version
            if (vMap.containsKey(MediaVersion.SEND)) {
                // check "send" version doesn't exceed max MMS size for a message
                MediaElementVersion sendVersion = vMap[MediaVersion.SEND]
                if (sendVersion.sizeInBytes > Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES) {
                    return ["sendVersionTooLarge"]
                }
                // version map key and the version property of MediaElementVersion must agree
                for (Map.Entry<MediaVersion, MediaElementVersion> entry in vMap) {
                    if (entry.key != entry.value.version) {
                        return ["versionKeysMismatch"]
                    }
                }
            }
            else { return ["missingSendVersion"] }
        }
    }

    // TODO: eager fetch association


    // Events
    // ------

    def beforeValidate() {
        List<String> msgs = []
        this.versions.each { MediaElementVersion v1 ->
            if (!v1.validate()) {
                msgs += Helpers.resultFactory.failWithValidationErrors(v1.errors).errorMessages
            }
        }
        if (msgs) {
            this.errors.rejectValue("versions", "mediaElement.versions.invalid",
                [msgs] as Object[], "Invalid element")
        }
    }

    // Events
    // ------

    MediaElementVersion addVersion(UploadItem uItem) {
        // TODO
    }

    // Property access
    // ---------------

    long getSendVersionSizeInBytes() {
        vMap[MediaVersion.SEND]?.sizeInBytes
    }
    Map<MediaVersion, MediaElementVersion> getVersionsForDisplay() {
        versions.findAll { MediaVersion k, MediaElementVersion v -> k != MediaVersion.SEND } ?:
            [(MediaVersion.LARGE):versions[MediaVersion.SEND]]
    }

    // Serialization via proxy
    // -----------------------
    // From Effective Java 2ed Item 78

    private Object writeReplace() {
       new SerializationProxy(this);
    }
    private void readObject(ObjectInputStream stream) throws InvalidObjectException {
       throw new InvalidObjectException("Proxy required");
    }
    private static class SerializationProxy implements Serializable {

        private static final long serialVersionUID = 1L

        private final String uid
        private final MediaType type
        private final EnumMap<MediaVersion, MediaElementVersion> versions

        SerializationProxy(MediaElement obj) {
            uid = obj.uid
            type  = obj.type
            versions = obj.versions
        }

        private Object readResolve() throws ObjectStreamException {
            new MediaElement(type:type, versions:versions)
        }
    }
}
