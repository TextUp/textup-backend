package org.textup.util

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import grails.test.runtime.*
import org.apache.commons.validator.routines.UrlValidator
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class LinkUtilsSpec extends Specification {

    @Shared
    UrlValidator urlValidator = new UrlValidator()

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
        MockedMethod getSignedLink = MockedMethod.create(LinkUtils, "getSignedLink") {
            new URL(signedUrl)
        }

        when:
        URL link = LinkUtils.signedLink(ident)

        then:
        link instanceof URL
        link.toString() == signedUrl
        getSignedLink.callCount == 1
        getSignedLink.allArgs.every { it[3] == ident }

        expect:
        LinkUtils.signedLink(null) == null
    }

    void "test getting configuration links"() {
        given:
        String tok1 = TestUtils.randString()
        String tok2 = TestUtils.randString()

        expect:
        urlValidator.isValid(LinkUtils.adminDashboard())
        urlValidator.isValid(LinkUtils.setupAccount())
        urlValidator.isValid(LinkUtils.superDashboard())

        LinkUtils.passwordReset(tok1).contains(tok1)
        urlValidator.isValid(LinkUtils.passwordReset(tok1))

        LinkUtils.notification(tok2).contains(tok2)
        urlValidator.isValid(LinkUtils.notification(tok2))
    }
}
