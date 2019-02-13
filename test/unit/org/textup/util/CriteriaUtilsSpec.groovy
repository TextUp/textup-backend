package org.textup.util

import grails.gorm.DetachedCriteria
import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain(Location)
@TestMixin(HibernateTestMixin)
class CriteriaUtilsSpec extends Specification {

    void "test in list criteria"() {
        given:
        Location loc1 = TestUtils.buildLocation()
        Location loc2 = TestUtils.buildLocation()
        Location loc3 = TestUtils.buildLocation()

        when: "is optional"
        Collection founds = new DetachedCriteria(Location).build {
            CriteriaUtils.inList(delegate, "id", null, true)
        }.list()

        then:
        founds.isEmpty() == false

        when: "not optional"
        founds = new DetachedCriteria(Location).build {
            CriteriaUtils.inList(delegate, "id", null, false)
        }.list()

        then:
        founds.isEmpty()

        when:
        founds = new DetachedCriteria(Location).build {
            CriteriaUtils.inList(delegate, "id", [loc1.id])
        }.list()

        then:
        founds == [loc1]
    }

    void "test adding projection to return id"() {
        given:
        Location loc1 = TestUtils.buildLocation()

        when:
        Collection founds = new DetachedCriteria(Location).build { eq("id", loc1.id) }
            .build(CriteriaUtils.returnsId())
            .list()

        then:
        founds == [loc1.id]
    }

    void "test adding criteria to exclude certain id"() {
        given:
        Location loc1 = TestUtils.buildLocation()
        int lBaseline = Location.count()

        when:
        int num = new DetachedCriteria(Location).build(CriteriaUtils.forNotIdIfPresent(null)).count()

        then:
        num == lBaseline

        when:
        num = new DetachedCriteria(Location).build(CriteriaUtils.forNotIdIfPresent(loc1.id)).count()

        then:
        num == lBaseline - 1
    }

    void "test calling count given `DetachedCriteria`"() {
        given:
        Location loc1 = TestUtils.buildLocation()
        int lBaseline = Location.count()

        when:
        def retVal = CriteriaUtils.countAction(null).call()

        then:
        retVal == 0

        when:
        retVal = CriteriaUtils.countAction(new DetachedCriteria(Location)).call()

        then:
        retVal == lBaseline
    }

    void "test updating all"() {
        given:
        String address = TestUtils.randString()
        Location loc1 = TestUtils.buildLocation()
        Location loc2 = TestUtils.buildLocation()

        when:
        def numUpdated = CriteriaUtils.updateAll(Location, [address: address]) { null }

        then:
        numUpdated == 0
        loc1.refresh().address != address
        loc2.refresh().address != address

        when:
        numUpdated = CriteriaUtils.updateAll(Location, [address: address]) { [loc1, loc2]*.id }

        then:
        numUpdated == 2
        loc1.refresh().address == address
        loc2.refresh().address == address
    }
}
