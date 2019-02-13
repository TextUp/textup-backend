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
class AvailablePhoneNumberSpec extends Specification {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

	def setup() {
		TestUtils.standardMockSetup()
	}

	void "test creating existing"() {
		given:
		String num = TestUtils.randPhoneNumber()
		String sid = TestUtils.randString()

		when:
		Result res = AvailablePhoneNumber.tryCreateExisting(null, null)

		then:
		res.status == ResultStatus.UNPROCESSABLE_ENTITY
		res.errorMessages.size() > 0

		when:
		res = AvailablePhoneNumber.tryCreateExisting(num, sid)

		then:
		res.status == ResultStatus.CREATED
		res.payload instanceof AvailablePhoneNumber
		res.payload.infoType == AvailablePhoneNumber.TYPE_EXISTING
		res.payload.info == sid
		res.payload.number == PhoneNumber.create(num).number
	}

	void "test creating new"() {
		given:
		String num = TestUtils.randPhoneNumber()
		String country = TestUtils.randString()
		String region = TestUtils.randString()

		when:
		Result res = AvailablePhoneNumber.tryCreateNew(null, null, null)

		then:
		res.status == ResultStatus.UNPROCESSABLE_ENTITY
		res.errorMessages.size() > 0

		when:
		res = AvailablePhoneNumber.tryCreateNew(num, country, null)

		then:
		res.status == ResultStatus.CREATED
		res.payload instanceof AvailablePhoneNumber
		res.payload.infoType == AvailablePhoneNumber.TYPE_NEW
		res.payload.info == country
		res.payload.number == PhoneNumber.create(num).number

		when:
		res = AvailablePhoneNumber.tryCreateNew(num, country, region)

		then:
		res.status == ResultStatus.CREATED
		res.payload instanceof AvailablePhoneNumber
		res.payload.infoType == AvailablePhoneNumber.TYPE_NEW
		res.payload.info == "${region}, ${country}"
		res.payload.number == PhoneNumber.create(num).number
	}

	void "test equality + hash code"() {
        given:
        String num = TestUtils.randPhoneNumber()
		String sid = TestUtils.randString()
		String country = TestUtils.randString()
		String region = TestUtils.randString()
		AvailablePhoneNumber aNum1 = AvailablePhoneNumber.tryCreateExisting(num, sid).payload
		AvailablePhoneNumber aNum2 = AvailablePhoneNumber.tryCreateExisting(num, sid).payload
		AvailablePhoneNumber aNum3 = AvailablePhoneNumber.tryCreateNew(num, country, region).payload
		AvailablePhoneNumber aNum4 = AvailablePhoneNumber.tryCreateNew(num, country, region).payload

        expect:
        [aNum1, aNum2, aNum3, aNum4].every { it == it && it.hashCode() == it.hashCode() }

        aNum1 == aNum2
        aNum1.hashCode() == aNum2.hashCode()

        aNum3 == aNum4
        aNum3.hashCode() == aNum4.hashCode()

        aNum1 != aNum3
        aNum1.hashCode() != aNum3.hashCode()
        aNum1 != aNum4
        aNum1.hashCode() != aNum4.hashCode()

        aNum2 != aNum3
        aNum2.hashCode() != aNum3.hashCode()
        aNum2 != aNum4
        aNum2.hashCode() != aNum4.hashCode()
    }
}
