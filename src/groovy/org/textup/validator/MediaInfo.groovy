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
final class MediaInfo implements Serializable {

    private static final long serialVersionUID = 1L

    // See Effective Java 2ed Items 78 and 43
    // Note: Groovy doesn't support Java's array initialization syntax
    private static final MediaElement[] EMPTY_ELEMENTS_ARRAY = []

    List<MediaElement> elements = []

    static constraints = { // all nullable:false by default
    }

    // TODO: eager fetch association

    // Events
    // ------

    def beforeValidate() {
        List<String> msgs = []
        this.elements.each { MediaElement e1 ->
            if (!e1.validate()) {
                msgs += Helpers.resultFactory.failWithValidationErrors(e1.errors).errorMessages
            }
        }
        if (msgs) {
            this.errors.rejectValue("elements", "mediaInfo.elements.invalid",
                [msgs] as Object[], "Invalid media")
        }
    }

    // Methods
    // -------

    void forEachBatch(Closure<Void> doAction, Collection<MediaType> typesToRetrieve = []) {
        int maxFileCount = Constants.MAX_NUM_MEDIA_PER_MESSAGE
        long maxFileSize = Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES,
            currentBatchSize = 0
        List<MediaElement> batchSoFar = []
        getElements(typesToRetrieve).forEach { MediaElement element ->
            Long elementSize = element.sendVersionInBytes
            // if adding the current element would exceed either the file size or file number
            // thresholds, then execute this batch first and then clear to start new batch
            if (currentBatchSize + elementSize > maxFileSize ||
                batchSoFar.size() + 1 > maxFileCount) {
                doAction(batchSoFar)
                batchSoFar.clear()
                currentBatchSize = 0
            }
            batchSoFar << element
            currentBatchSize += elementSize
        }
        // call doAction on any batch left to execute
        if (batchSoFar) {
            doAction(batchSoFar)
        }
    }

    // Property access
    // ---------------

    List<MediaElement> getElements(Collection<MediaType> typesToRetrieve = []) {
        typesToRetrieve ? elements.findAll { MediaEement e1 -> e1.type in mediaTypes } : elements
    }

    MediaElement removeElement(String uid) {
        MediaElement e1 = elements.find { MediaElement e1 -> e1.uid == uid }
        if (e1) {
            elements.remove(e1)
        }
        e1
    }

    boolean isEmpty() {
        elements.isEmpty()
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

        private final MediaElement[] elements

        SerializationProxy(MediaInfo obj) {
            elements  = obj.elements.toArray(EMPTY_ELEMENTS_ARRAY)
        }

        private Object readResolve() throws ObjectStreamException {
            new MediaInfo(elements:Arrays.toList(elements))
        }
    }
}
