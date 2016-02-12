package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

@Domain(Location)
@TestMixin(HibernateTestMixin)
class LocationSpec extends Specification {

    void "test constraints and deletion"() {
    	when:
    	Location l = new Location(address:"testing address")

    	then:
    	l.validate() == false
    	l.errors.errorCount == 2

    	when: "lat out of bounds"
    	l.lat = -100G
    	l.lon = 0G

    	then:
    	l.validate() == false
    	l.errors.errorCount == 1

    	when: "lon out of bounds"
    	l.lat = 0G
    	l.lon = 200G

    	then:
    	l.validate() == false
    	l.errors.errorCount == 1

    	when: "valid bounds"
    	l.lat = 0G
    	l.lon = 0G

    	then:
    	l.save(flush:true)

    	when: "delete this location"
    	int baseline = Location.count()
    	l.delete(flush:true)

    	then:
    	Location.count() == baseline - 1
    }
}
