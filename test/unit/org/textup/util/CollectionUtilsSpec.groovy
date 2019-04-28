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
class CollectionUtilsSpec extends Specification {

    void "test take right"() {
        given: "a list"
        List data = [0, 1, 2, 3, 4]

        when: "list is null"
        List taken = CollectionUtils.takeRight(null, 2)

        then:
        taken == []

        when: "index is too small"
        taken = CollectionUtils.takeRight(data, -2)

        then:
        taken == []

        when: "index is too big"
        taken = CollectionUtils.takeRight(data, data.size() + 4)

        then:
        taken == []

        when: "take nothing"
        taken = CollectionUtils.takeRight(data, 0)

        then:
        taken == []

        when: "take all"
        taken = CollectionUtils.takeRight(data, data.size())

        then:
        taken == [0, 1, 2, 3, 4]

        when: "index is item in the middle of the list"
        taken = CollectionUtils.takeRight(data, data.size() - 1)

        then:
        taken == [1, 2, 3, 4]

        when: "index is item in the middle of the list"
        taken = CollectionUtils.takeRight(data, 3)

        then:
        taken == [2, 3, 4]
    }

    void "test in list ignoring case"() {
        expect:
        CollectionUtils.inListIgnoreCase("toBeFound", ["hello", "yes"]) == false
        CollectionUtils.inListIgnoreCase("toBeFound", ["tobefound"]) == true
        CollectionUtils.inListIgnoreCase("toBeFound", []) == false
        CollectionUtils.inListIgnoreCase(null, ["hello", "yes"]) == false
        CollectionUtils.inListIgnoreCase("toBeFound", null) == false
    }

    void "test join with different last"() {
        expect:
        CollectionUtils.joinWithDifferentLast([], ", ", " and ") == ""
        CollectionUtils.joinWithDifferentLast([1], ", ", " and ") == "1"
        CollectionUtils.joinWithDifferentLast([1, 2], ", ", " and ") == "1 and 2"
        CollectionUtils.joinWithDifferentLast([1, 2, 3], ", ", " and ") == "1, 2 and 3"
    }

    void "test ensure list has no null elements"() {
        expect:
        CollectionUtils.ensureNoNull(null) == null
        CollectionUtils.ensureNoNull([1, 2, 3, 4]) == [1, 2, 3, 4]
        CollectionUtils.ensureNoNull([null, 1, 2, 3, null, 4]) == [1, 2, 3, 4]
    }

    void "test symmetric difference"() {
        given:
        Collection obj1 = [1, 2, 3]
        Collection obj2 = [1, 2, 3, 4]
        Collection obj3 = []

        expect:
        CollectionUtils.difference(null, null) == []
        CollectionUtils.difference(obj1, null) == obj1
        CollectionUtils.difference(obj1, null).is(obj1) == false
        CollectionUtils.difference(null, obj2) == obj2
        CollectionUtils.difference(null, obj2).is(obj2) == false
        CollectionUtils.difference(obj1, obj2) == [4]
        CollectionUtils.difference(obj1, obj3) == [1, 2, 3]
    }

    void "test shallow copy no null"() {
        given:
        Collection obj1 = [1, 2, 3]
        Collection obj2 = [1, 2, null, 4]
        Collection obj3 = []

        expect:
        CollectionUtils.shallowCopyNoNull(null) == []
        CollectionUtils.shallowCopyNoNull(obj3) == []
        CollectionUtils.shallowCopyNoNull(obj1) == obj1
        CollectionUtils.shallowCopyNoNull(obj1).is(obj1) == false
        CollectionUtils.shallowCopyNoNull(obj2).is(obj2) == false
    }

    void "test merging collections uniquely"() {
        given:
        String msg1 = TestUtils.randString()
        String msg2 = TestUtils.randString()
        String msg3 = TestUtils.randString()
        String msg4 = TestUtils.randString()
        String msg5 = TestUtils.randString()

        when:
        List merged = CollectionUtils.mergeUnique(null)

        then:
        merged == []

        when:
        merged = CollectionUtils.mergeUnique([[msg1, msg2, msg3]])

        then:
        merged == [msg1, msg2, msg3]

        when:
        merged = CollectionUtils.mergeUnique([[msg1, msg2, msg3], null, [msg4, msg3], [msg1, msg4, msg5]])

        then:
        merged == [msg1, msg2, msg3, msg4, msg5]
    }
}
