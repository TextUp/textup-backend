package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.restapidoc.annotation.*
import org.textup.type.*

@GrailsTypeChecked
@EqualsAndHashCode
@RestApiObject(
    name        = "MediaInfo",
    description = "Contains all media elements for a message or batch of messages")
class MediaInfo implements ReadOnlyMediaInfo {

    private Set<MediaElement> _originalMediaElements = Collections.emptySet()

    static transients = ['_originalMediaElements']
    @RestApiObjectField(
        apiFieldName   = "elements",
        description    = "Media of various types contained within this message",
        allowedType    = "Set<MediaElement>",
        useForCreation = false)
    static hasMany = [mediaElements: MediaElement]
    static constraints = { // all nullable:false by default
        mediaElements cascadeValidation: true
    }
    static mapping = {
        mediaElements lazy: false, cascade: "save-update"
    }

    // Events
    // ------

    void onLoad() { tryUpdateOriginalMediaElements() }
    void afterInsert() { tryUpdateOriginalMediaElements() }
    void afterUpdate() { tryUpdateOriginalMediaElements() }
    protected void tryUpdateOriginalMediaElements() {
        if (mediaElements) {
            _originalMediaElements = new HashSet<MediaElement>(mediaElements)
        }
        else { _originalMediaElements = Collections.emptySet() }
    }

    // Methods
    // -------

    // We want to refer to the persistent set of elements when creating a duplicate for the revision.
    // It is OK if two media info objects point to the same media elements so we don't have to
    // create many duplicate media element objects
    MediaInfo tryDuplicatePersistentState() {
        if (_originalMediaElements) {
            new MediaInfo(mediaElements: new HashSet<MediaElement>(_originalMediaElements))
        }
        else {
            log.debug("MediaInfo.tryDuplicatePersistentState: no elements")
            null
        }
    }

    void forEachBatch(Closure<?> doAction, Collection<MediaType> typesToRetrieve = []) {
        int maxFileCount = Constants.MAX_NUM_MEDIA_PER_MESSAGE
        long maxFileSize = Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES,
            currentBatchSize = 0
        List<MediaElement> batchSoFar = []
        getMediaElementsByType(typesToRetrieve).each { MediaElement e1 ->
            Long elementSize = e1.sendVersion?.sizeInBytes ?: 0
            // if adding the current element would exceed either the file size or file number
            // thresholds, then execute this batch first and then clear to start new batch
            if (currentBatchSize + elementSize > maxFileSize ||
                batchSoFar.size() + 1 > maxFileCount) {
                doAction(batchSoFar)
                batchSoFar.clear()
                currentBatchSize = 0
            }
            batchSoFar << e1
            currentBatchSize += elementSize
        }
        // call doAction on any batch left to execute
        if (batchSoFar) {
            doAction(batchSoFar)
        }
    }

    // Property access
    // ---------------

    // Need to manually override the isDirty check because it only works when we add the first
    // element and change the value from `null` to a Collection. For all subsequent modifications,
    // the default `isDirty()` method will not check the state of the initialized Collection
    boolean isDirty() {
        (mediaElements ?: Collections.emptySet()) != _originalMediaElements
    }

    List<MediaElement> getMediaElementsByType(Collection<MediaType> typesToFind = []) {
        Collection<MediaElement> elementCollection = typesToFind ?
            mediaElements?.findAll { MediaElement e1 -> e1.type in typesToFind } : mediaElements
        elementCollection ? new ArrayList<MediaElement>(elementCollection) : []
    }

    MediaElement removeMediaElement(String uid) {
        MediaElement e1 = mediaElements.find { MediaElement e1 -> e1.uid == uid }
        if (e1) {
            removeFromMediaElements(e1)
        }
        e1
    }

    boolean isEmpty() {
        mediaElements ? mediaElements.isEmpty() : true
    }
}
