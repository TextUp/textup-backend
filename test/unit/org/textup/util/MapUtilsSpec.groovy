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
class MapUtilsSpec extends Specification {

    void "test building map of property to single or multiple objects"() {
        given:
        List obj1 = [1, 888]
        List obj2 = [2, 888]
        List obj3 = [3, 888]
        Collection objs = [obj1, obj1, obj2, obj3]

        when: "to single object"
        Map map1 = MapUtils.buildObjectMap(objs) { it[0] }

        then:
        map1.size() == 3
        map1.containsKey(obj1[0]) == true
        map1.containsKey(obj2[0]) == true
        map1.containsKey(obj3[0]) == true
        map1[obj1[0]] == obj1
        map1[obj2[0]] == obj2
        map1[obj3[0]] == obj3

        when: "to multiple unique objects"
        map1 = MapUtils.buildManyUniqueObjectsMap(objs) { it[0] }

        then:
        map1.size() == 3
        map1.containsKey(obj1[0]) == true
        map1.containsKey(obj2[0]) == true
        map1.containsKey(obj3[0]) == true
        map1[obj1[0]].size() == 1 // duplicates are screened out
        map1[obj2[0]].size() == 1
        map1[obj3[0]].size() == 1

        when: "to multiple non unique objects"
        map1 = MapUtils.buildManyNonUniqueObjectsMap(objs) { it[0] }

        then:
        map1.size() == 3
        map1.containsKey(obj1[0]) == true
        map1.containsKey(obj2[0]) == true
        map1.containsKey(obj3[0]) == true
        map1[obj1[0]].size() == 2
        map1[obj2[0]].size() == 1
        map1[obj3[0]].size() == 1
    }

    void "test finding highest value"() {
        given:
        Date dt1 = new Date()
        Date dt2 = new Date(dt1.time * 2)

        expect:
        MapUtils.findHighestValue(["okay":88, "value":1, "yas":2]).key == "okay"
        MapUtils.findHighestValue(["okay":dt2, "value":dt1, "yas":dt1]).key == "okay"
        MapUtils.findHighestValue(["okay":"c", "value":"b", "yas":"a"]).key == "okay"
    }
}
