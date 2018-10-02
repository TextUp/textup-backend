package org.textup.type

import spock.lang.*

// TODO move these tests over to ImagePostProcessor

class MediaVersionSpec extends Specification {

    void "test iterating through versions in appropriate order"() {
        when: "start iterating from send version"
        List<MediaVersion> versionsIterated = iterateHelper(MediaVersion.SEND)

        then: "no next"
        [MediaVersion.SEND] == versionsIterated

        when: "start iterating from large version"
        versionsIterated = iterateHelper(MediaVersion.LARGE)

        then: "iterate through large, medium, and small"
        [MediaVersion.LARGE, MediaVersion.MEDIUM, MediaVersion.SMALL] == versionsIterated

        when: "start iterating from medium version"
        versionsIterated = iterateHelper(MediaVersion.MEDIUM)

        then: "iterate through medium and small"
        [MediaVersion.MEDIUM, MediaVersion.SMALL] == versionsIterated
    }

    protected List<MediaVersion> iterateHelper(MediaVersion startEnum) {
        List<MediaVersion> versionsIterated = []
        MediaVersion currEnum = startEnum
        while (currEnum.next) {
            versionsIterated << currEnum
            currEnum = currEnum.next
        }
        versionsIterated << currEnum
        versionsIterated
    }
}
