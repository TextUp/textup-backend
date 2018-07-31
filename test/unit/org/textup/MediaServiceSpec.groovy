package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.test.runtime.FreshRuntime
import grails.validation.ValidationErrors
import java.util.concurrent.atomic.AtomicInteger
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.textup.rest.TwimlBuilder
import org.textup.type.*
import org.textup.util.CustomSpec
import org.textup.validator.*
import spock.lang.*
import static org.springframework.http.HttpStatus.*

// TODO

@TestFor(MediaService)
@Domain([MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class MediaServiceSpec extends Specification {

    // // Media actions
    // // -------------

    // void "test creating send version"() {
    //     when: "pass in content type and data"

    //     then: "create send version with appropriate width and file size"

    // }

    // void "test creating display versions"() {
    //     when: "pass in image that is larger than the `large` max size"

    //     then: "image is scaled down to max size for each version"

    //     when: "image size is already between `large` and `medium`"

    //     then: "large image is not scaled, medium and small are scaled"

    //     when: "image size is smaller than `small` max size"

    //     then: "none of the three returned versions are scaled"
    // }

    // void "test creating versions overall"() {
    //     when: "pass in data"

    //     then: "get back both send and display versions"
    // }

    // void "test adding media overall"() {
    //     when: "pass in data"

    //     then: "media info has a new media element added to it"

    // }

    // void "test handling media actions errors"() {
    //     when: "action container has errors"

    //     then:

    // }

    // void "test adding and removing via media action"() {
    //     when: "add via media action"

    //     then: "add function is called"

    //     when: "remove via media action"

    //     then:

    // }

    // // Receiving media
    // // ---------------

    // void "test extracting media id from url"() {
    //     expect:

    // }

    // void "test downloading + building versions for incoming media"() {
    //     given: "mocks for http request and response"

    //     when: "response has a error status"

    //     then:

    //     when: "response has a non-error status"

    //     then: "add function is called + media ids are extracted and collected"

    // }

    // void "test deleting media"() {
    //     given: "mocks for Twilio Media object"

    //     when: "given media ids to delete"

    //     then: "all provided media is are deleted"

    // }

    // // Sending media
    // // -------------

    // void "test sending media for text"() {
    //     when: "no media"

    //     then: "send without media"

    //     when: "with media"

    //     then: "send with media in batches"

    // }

    // void "test sending media for call"() {
    //     when: "no media"

    //     then: "only call"

    //     when: "with media"

    //     then: "send media via text message and also call"

    // }

    // void "test sending with media overall"() {
    //     when: "without call token"

    //     then: "send as text"

    //     when: "with call token"

    //     then: "send as call"

    //     when: "has some errors"

    //     then: "return error"

    // }
}
