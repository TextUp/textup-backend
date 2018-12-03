package org.textup.util

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import grails.test.runtime.*
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class LinkUtilsSpec extends Specification {

    void "test generating public links"() {
        given:
        String bucketName = grailsApplication.flatConfig["textup.media.bucketName"]
        String ident = TestUtils.randString()

        when:
        URL link = LinkUtils.unsignedLink(ident)

        then:
        link instanceof URL
        link.toString().contains(bucketName)
        link.toString().contains(ident)

        expect:
        LinkUtils.unsignedLink(null) == null
    }

    @DirtiesRuntime
    void "test generating authenticated links"() {
        given:
        String ident = TestUtils.randString()
        String signedUrl = "https://www.example.com"
        MockedMethod getSignedLink = TestUtils.mock(LinkUtils, "getSignedLink") {
            new URL(signedUrl)
        }

        when:
        URL link = LinkUtils.signedLink(ident)

        then:
        link instanceof URL
        link.toString() == signedUrl
        getSignedLink.callCount == 1
        getSignedLink.callArguments.every { it[3] == ident }

        expect:
        LinkUtils.signedLink(null) == null
    }
}
