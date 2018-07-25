package org.textup

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import org.restapidoc.annotation.*
import org.textup.type.*

@GrailsCompileStatic
@EqualsAndHashCode
@RestApiObject(
    name        = "MediaInfo",
    description = "Contains all media elements for a message or batch of messages")
class MediaInfo implements ReadOnlyMediaInfo {

    @RestApiObjectField(
        apiFieldName   = "elements",
        description    = "Media of various types contained within this message",
        allowedType    = "Set<MediaElement>",
        useForCreation = false)
    static hasMany = [elements: MediaElement]
    static constraints = { // all nullable:false by default
    }
    static mapping = {
        elements lazy: false, cascade: "save-update"
    }

    // Methods
    // -------

    void forEachBatch(Closure<?> doAction, Collection<MediaType> typesToRetrieve = []) {
        int maxFileCount = Constants.MAX_NUM_MEDIA_PER_MESSAGE
        long maxFileSize = Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES,
            currentBatchSize = 0
        List<MediaElement> batchSoFar = []
        getElements(typesToRetrieve).each { MediaElement element ->
            Long elementSize = element.sendVersion?.sizeInBytes ?: 0
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

    // We want to refer to the persistent set of elements when creating a duplicate for the revision.
    // It is OK if two media info objects point to the same media elements so we don't have to
    // create many duplicate media element objects
    MediaInfo duplicatePersistentState() {
        new MediaInfo(elements: this.getPersistentValue("elements"))
    }

    // Property access
    // ---------------

    List<MediaElement> getElements(Collection<MediaType> typesToFind = []) {
        Collection<MediaElement> elementCollection = typesToFind ?
            elements.findAll { MediaElement e1 -> e1.type in typesToFind } : elements
        new ArrayList<MediaElement>(elementCollection)
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
}
