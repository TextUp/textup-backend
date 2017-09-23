package org.textup.validator

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import org.textup.type.ScheduleStatus
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@TestMixin(GrailsUnitTestMixin)
@Unroll
class ScheduleChangeSpec extends Specification {

	void "test constraints"() {
		when: "we have a blank object"
		ScheduleChange sc = new ScheduleChange()

		then: "invalid"
		sc.validate() == false
		sc.errors.errorCount == 2

		when: "we fill out the type and when"
		sc = new ScheduleChange(type:ScheduleStatus.AVAILABLE,
			when:DateTime.now())

		then: "valid"
		sc.validate() == true
	}

	void "test timezones"() {
		given: "a valid ScheduleChange"
		ScheduleChange sc = new ScheduleChange(type:ScheduleStatus.AVAILABLE,
			when:DateTime.now())
		assert sc.validate()

		when: "we set the timezone"
		sc.timezone = "America/Los_Angeles"

		then: "we get times in that timezone"
		sc.when.zone.getID() == "America/Los_Angeles"
	}
}
