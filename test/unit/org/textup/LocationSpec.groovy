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

@Domain(Location)
@TestMixin(HibernateTestMixin)
class LocationSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test constraints and deletion"() {
        given:
        String address = TestUtils.randString()
        BigDecimal validLat = 0G
        BigDecimal validLng = 88G

    	when:
        Result res = Location.tryCreate(null, null, null)

    	then:
    	res.status == ResultStatus.UNPROCESSABLE_ENTITY

    	when:
        res = Location.tryCreate(address, -888G, -888G)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = Location.tryCreate(address, validLat, validLng)

        then:
        res.status == ResultStatus.CREATED
        res.payload.address == address
        res.payload.lat == validLat
        res.payload.lng == validLng
    }

    void "test determining if actually dirty"() {
        when:
        Location loc1 = TestUtils.buildLocation()

        then:
        loc1.isDirty() == false
        loc1.isActuallyDirty() == false

        when: "change lat by a little bit"
        loc1.lat = loc1.lat + 0.000001

        then:
        loc1.isDirty() == true
        loc1.isActuallyDirty() == false

        when: "change lng by a little bit"
        loc1.lng = loc1.lng + 0.000001

        then:
        loc1.isDirty() == true
        loc1.isActuallyDirty() == false

        when: "change address"
        loc1.address = TestUtils.randString()

        then:
        loc1.isDirty() == true
        loc1.isActuallyDirty() == true
    }

    void "test duplicating persistent state"() {
        given: "an unsaved obj"
        Location loc1 = new Location(address: "hi", lat: 0G, lng: 0G)
        assert loc1.validate()

        when: "try create duplicate"
        Location dup = loc1.tryDuplicatePersistentState()

        then: "cannot do so because no persisted values to draw from"
        dup == null

        when: "save obj, then create duplicate"
        loc1.save(flush: true, failOnError:true)
        dup = loc1.tryDuplicatePersistentState()

        then: "persisted values are NOT null"
        dup instanceof Location
        null != dup.address
        dup.address == loc1.address
        null != dup.lat
        dup.lat == loc1.lat
        null != dup.lng
        dup.lng == loc1.lng

        when: "change some values on the Location"
        loc1.address = "something else"
        loc1.lat = 8G
        assert loc1.validate()
        dup = loc1.tryDuplicatePersistentState()

        then: "duplicate still uses persisted values"
        dup instanceof Location
        null != dup.address
        dup.address != loc1.address
        null != dup.lat
        dup.lat != loc1.lat
        null != dup.lng
        dup.lng == loc1.lng
    }
}
