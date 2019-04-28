package org.textup.validator

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
@Unroll
class ScheduleChangeSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

	void "test constraints"() {
		given:
		DateTime dt = DateTime.now()
		String tz = "America/Los_Angeles"

		when: "we have a blank object"
		Result res = ScheduleChange.tryCreate(null, null, null)

		then: "invalid"
		res.status == ResultStatus.UNPROCESSABLE_ENTITY

		when: "we fill out the type and when"
		res = ScheduleChange.tryCreate(ScheduleStatus.AVAILABLE, dt, null)

		then: "valid"
		res.status == ResultStatus.CREATED
		res.payload instanceof ScheduleChange
		res.payload.type == ScheduleStatus.AVAILABLE
		res.payload.when == dt
		res.payload.timezone == null

		when:
		res = ScheduleChange.tryCreate(ScheduleStatus.UNAVAILABLE, dt, tz)

		then:
		res.status == ResultStatus.CREATED
		res.payload instanceof ScheduleChange
		res.payload.type == ScheduleStatus.UNAVAILABLE
		res.payload.when == dt
		res.payload.when.zone.getID() == tz
		res.payload.timezone == tz
	}
}
