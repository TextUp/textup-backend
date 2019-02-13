package org.textup.util

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class MarshallerUtilsSpec extends Specification {

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test resolving singular and plural"() {
        expect:
        MarshallerUtils.resolveCodeToSingular(null) == MarshallerUtils.FALLBACK_SINGULAR
        MarshallerUtils.resolveCodeToSingular("") == MarshallerUtils.FALLBACK_SINGULAR
        MarshallerUtils.resolveCodeToSingular("invalid") == MarshallerUtils.FALLBACK_SINGULAR
        MarshallerUtils.resolveCodeToSingular(MarshallerUtils.KEY_RECORD_ITEM_REQUEST) !=
            MarshallerUtils.FALLBACK_SINGULAR

        and:
        MarshallerUtils.resolveCodeToPlural(null) == MarshallerUtils.FALLBACK_PLURAL
        MarshallerUtils.resolveCodeToPlural("") == MarshallerUtils.FALLBACK_PLURAL
        MarshallerUtils.resolveCodeToPlural("invalid") == MarshallerUtils.FALLBACK_PLURAL
        MarshallerUtils.resolveCodeToPlural(MarshallerUtils.KEY_RECORD_ITEM_REQUEST) !=
            MarshallerUtils.FALLBACK_PLURAL
    }

    void "test building links"() {
        given:
        String resourceKey = TestUtils.randString()
        Long id = TestUtils.randIntegerUpTo(88, true)

        when:
        Map links = MarshallerUtils.buildLinks(resourceKey, id)

        then:
        links.self instanceof String
        links.self.contains(id.toString())
        links.self.contains(resourceKey)
        links.self.contains(RestUtils.ACTION_GET_SINGLE)
    }
}
