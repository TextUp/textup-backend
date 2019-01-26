package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([CustomAccountDetails, MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class MediaInfoSpec extends Specification {

    void "test constraints and isEmpty"() {
        when: "empty obj"
        MediaInfo mInfo = new MediaInfo()

        then: "is empty and is valid"
        mInfo.validate() == true
        mInfo.isEmpty() == true
    }

    void "test modifying and retrieving elements"() {
        given: "empty obj"
        MediaInfo mInfo = new MediaInfo()

        when: "adding elements, with some that are duplicates"
        MediaElement e1 = TestUtils.buildMediaElement(Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES / 2)
        MediaElement e2 = TestUtils.buildMediaElement(Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES / 2)
        mInfo.addToMediaElements(e1)
        mInfo.addToMediaElements(e1)
        mInfo.addToMediaElements(e1)
        mInfo.addToMediaElements(e2)
        assert mInfo.validate()

        then: "can retrieve to see additions + hasMany set has enforced uniqueness among elements"
        mInfo.mediaElements.size() == 2
        mInfo.getMediaElementsByType(MediaType.IMAGE_TYPES).size() == 2

        and: "pass in a falsy value assumes all media elements"
        mInfo.getMediaElementsByType().size() == 2
        mInfo.getMediaElementsByType(null).size() == 2
        mInfo.getMediaElementsByType([]).size() == 2

        when: "remove elements by uid"
        assert mInfo.removeMediaElement("nonexistent") == null
        assert mInfo.removeMediaElement(e1.uid) instanceof MediaElement

        then: "can retrieve to see removals"
        mInfo.mediaElements.size() == 1
        mInfo.getMediaElementsByType(MediaType.IMAGE_TYPES).size() == 1
    }

    void "test iterating over elements in batches by file size"() {
        given: "valid obj with elements"
        MediaElement e1 = TestUtils.buildMediaElement(Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES / 2)
        MediaElement e2 = TestUtils.buildMediaElement(Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES / 2)
        MediaElement e3 = TestUtils.buildMediaElement(Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES * 0.75)
        MediaElement e4 = TestUtils.buildMediaElement(Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES * 0.75)
        MediaInfo mInfo = new MediaInfo()
        [e1, e2, e3, e4].each(mInfo.&addToMediaElements)
        assert mInfo.validate()

        when: "looping over batches"
        int numBatches = 0
        mInfo.forEachBatch({ numBatches++ }, MediaType.IMAGE_TYPES)

        then: "iteration to happen in batches according to max file size and number of files"
        numBatches == 3
    }

    void "test iterating over elements in batches by number of files"() {
        given: "valid obj with elements"
        MediaInfo mInfo = new MediaInfo()
        45.times { mInfo.addToMediaElements(TestUtils.buildMediaElement(5)) }
        assert mInfo.validate()

        when: "looping over batches"
        int numBatches = 0
        mInfo.forEachBatch({ numBatches++ }, MediaType.IMAGE_TYPES)

        then: "iteration to happen in batches according to max file size and number of files"
        numBatches == 5 // up to 10 items per batch
    }

    void "test iterating over batches where some media elements are missing send versions"() {
        given:
        MediaElement e1 = TestUtils.buildMediaElement()
        MediaElement e2 = TestUtils.buildMediaElement()
        MediaElement e3 = TestUtils.buildMediaElement()
        MediaElement e4 = TestUtils.buildMediaElement()
        e2.sendVersion = null
        e3.sendVersion = null
        MediaInfo mInfo = new MediaInfo()
        [e1, e2, e3, e4].each { mInfo.addToMediaElements(it) }
        assert mInfo.validate()
        List<MediaElement> batch

        when:
        mInfo.forEachBatch { batch = it }

        then: "media elements without send versions are ignored"
        batch instanceof Collection
        batch.size() == 2
        batch.each { it == e1 || it == e4 }
    }

    void "test getting most recent media element by type"() {
        given:
        MediaInfo mInfo = new MediaInfo()
        MediaElement mostRecent
        DateTime dt = DateTime.now()
        10.times {
            mostRecent = TestUtils.buildMediaElement()
            mostRecent.sendVersion.type = MediaType.AUDIO_WEBM_OPUS
            mostRecent.whenCreated = dt.plusMinutes(it)
            mInfo.addToMediaElements(mostRecent)
        }
        assert mInfo.validate()

        expect: "pass in a falsy value assumes all media elements"
        mInfo.getMostRecentByType() == mostRecent
        mInfo.getMostRecentByType(null) == mostRecent
        mInfo.getMostRecentByType([]) == mostRecent

        and:
        mInfo.getMostRecentByType([MediaType.IMAGE_JPEG]) == null
        mInfo.getMostRecentByType([MediaType.AUDIO_WEBM_OPUS]) == mostRecent
    }

    void "test cascading validation"() {
        given: "empty obj"
        MediaInfo mInfo = new MediaInfo()

        when: "valid obj with all valid elements"
        MediaElement e1 = TestUtils.buildMediaElement(Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES / 2)
        MediaElement e2 = TestUtils.buildMediaElement(Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES / 2)
        MediaElement e3 = TestUtils.buildMediaElement(Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES * 0.75)
        MediaElement e4 = TestUtils.buildMediaElement(Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES * 0.75)
        [e1, e2, e3, e4].each(mInfo.&addToMediaElements)

        then: "parent is valid"
        mInfo.validate() == true

        when: "add one invalid version"
        e1.sendVersion.type = null

        then: "validating parent reveals invalid version"
        mInfo.validate() == false
        mInfo.errors.getFieldErrorCount("mediaElements.0.sendVersion.type") == 1

        when: "invalid version is fixed"
        e1.sendVersion.type = MediaType.IMAGE_PNG

        then: "parent validates"
        mInfo.validate() == true
    }

    void "test duplicate persistent state"() {
        given: "valid obj with elements but NOT PERSISTED"
        MediaInfo mInfo = new MediaInfo()
        assert mInfo.isDirty() == false

        when: "try to duplicate"
        MediaInfo dupInfo = mInfo.tryDuplicatePersistentState()

        then: "returns null because not persisted yet"
        dupInfo == null

        when: "persist then try to duplicate"
        assert mInfo.save(flush: true, failOnError: true)
        dupInfo = mInfo.tryDuplicatePersistentState()

        then: "returns null because no elements"
        dupInfo == null

        when: "add elements and then persist"
        MediaElement e1 = TestUtils.buildMediaElement(Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES / 2)
        MediaElement e2 = TestUtils.buildMediaElement(Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES / 2)
        MediaElement e3 = TestUtils.buildMediaElement(Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES * 0.75)
        MediaElement e4 = TestUtils.buildMediaElement(Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES * 0.75)
        List<MediaElement> originalElements = [e1, e2, e3, e4]
        originalElements.each(mInfo.&addToMediaElements)
        assert mInfo.isDirty() == true
        assert mInfo.save(flush: true, failOnError: true)
        assert mInfo.isDirty() == false
        dupInfo = mInfo.tryDuplicatePersistentState()

        then: "finally able to create a duplicate even though not dirty because dirty checking is separate"
        dupInfo instanceof MediaInfo
        dupInfo.mediaElements.size() == 4
        originalElements.every { it in dupInfo.mediaElements }
        mInfo.isDirty() == false
        mInfo.mediaElements == dupInfo.mediaElements // same because not actually dirty

        when: "we make some changes by adding + removing from elements"
        MediaElement e5 = TestUtils.buildMediaElement(Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES * 0.75)
        mInfo.addToMediaElements(e5)
        mInfo.removeFromMediaElements(e3)
        mInfo.removeFromMediaElements(e4)
        assert mInfo.isDirty() == true
        dupInfo = mInfo.tryDuplicatePersistentState()

        then: "duplicate persistent state creates a copy that ignores the unsaved changes"
        mInfo.mediaElements.size() == 3
        dupInfo instanceof MediaInfo
        dupInfo.mediaElements.size() == 4
        originalElements.every { it in dupInfo.mediaElements }
        mInfo.mediaElements != dupInfo.mediaElements

        when: "we then save the changes"
        assert mInfo.save(flush: true, failOnError: true)
        assert mInfo.isDirty() == false
        dupInfo = mInfo.tryDuplicatePersistentState()
        assert mInfo.isDirty() == false

        then: "the duplicated copy also reflects the now-persisted changes"
        mInfo.mediaElements.size() == 3
        dupInfo instanceof MediaInfo
        dupInfo.mediaElements.size() == 3
        mInfo.mediaElements == dupInfo.mediaElements
    }
}
