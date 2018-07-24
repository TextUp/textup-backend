package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode
@GrailsCompileStatic
@Validateable
final class MediaElementVersion implements Serializable {

    private static final long serialVersionUID = 1L

    MediaVersion version
    String key
    Long sizeInBytes
    Integer widthInPixels

    static constraints = { // all nullable:false by default
        // inherent width in pixels for responsive media, currently only supported for images
        widthInPixels nullable:true
    }

    // Property access
    // ---------------

    // using the "w" unit for inherent width as called for in the `srcset` attribute as defined
    // in the responsive image specification
    String getInherentWidth() {
        Integer width1 = widthInPixels ?: version.?fallbackWidthInPixels
        width1 ? "${width1}w" : ""
    }

    URL getLink(StorageService storageService) {
        if (!storageService) {
            log.error("MediaElementVersion.getLink: storageService not provided")
            return
        }
        Result<URL> res = storageService
            .generateAuthLink(this.key)
            .logFail('MediaElementVersion.getLink')
        res.success ? res.payload : null
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

        private final MediaVersion version
        private final String key
        private final Long sizeInBytes
        private final Integer widthInPixels

        SerializationProxy(MediaElementVersion obj) {
            version  = obj.version
            key = obj.key
            sizeInBytes = obj.sizeInBytes
            widthInPixels = obj.widthInPixels
        }

        private Object readResolve() {
            new MediaElementVersion(version:version,
                key:key,
                sizeInBytes:sizeInBytes,
                widthInPixels:widthInPixels)
        }
    }
}
