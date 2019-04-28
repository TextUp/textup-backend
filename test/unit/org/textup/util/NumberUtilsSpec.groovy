package org.textup.util

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import grails.util.Holders
import java.util.concurrent.*
import org.apache.http.client.methods.*
import org.apache.http.HttpResponse
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.joda.time.DateTime
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class NumberUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test rounding BigDecimal"() {
        expect:
        NumberUtils.round(88.881G, -3) == 0G
        NumberUtils.round(88.881G, -1) == 90G
        NumberUtils.round(88.881G, -2) == 100G
        NumberUtils.round(null, 0) == null
        NumberUtils.round(88.881G, 0) == 89G
        NumberUtils.round(88.881G, 1) == 88.9G
        NumberUtils.round(88.881G, 2) == 88.88G
        NumberUtils.round(88.881G, 3) == 88.881G
        NumberUtils.round(88.881G, 4) == 88.881G
    }

    void "test comparing two BigDecimals at a given precision level"() {
        expect:
        NumberUtils.nearlyEqual(88.881G, 88.879G, 2) == true
        NumberUtils.nearlyEqual(88.881G, 88.871G, 2) == false
        NumberUtils.nearlyEqual(88.881G, 88.871G, 1) == true
        NumberUtils.nearlyEqual(88.881G, 88.871G, 0) == true
        NumberUtils.nearlyEqual(88.881G, 88.871G, -1) == true
        NumberUtils.nearlyEqual(88.881G, -88.871G, -1) == false
    }
}
