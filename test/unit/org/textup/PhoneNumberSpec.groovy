package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Domain(PhoneNumber)
@TestMixin(HibernateTestMixin)
@Unroll
class PhoneNumberSpec extends Specification {

    void "test phone number validation for #phoneNum"() {
    	when: "we have a phone number"
    	PhoneNumber pNum = new PhoneNumber(number:phoneNum)

    	then: 
    	pNum.validate() == isValid

    	where: 
		phoneNum          | isValid 
		"+2223334444"     | true 
		"+12223334444"    | true 
		"12223334444"     | true 
		"2223334444"      | true 
		"222333sdf4444"   | true 
		"222 33 4444"     | false
		"222 3asdf3 4444" | false
    }

    void "test phone number formatting"() {
    	when: "we have a valid phone number"
    	PhoneNumber pNum = new PhoneNumber(number:"+12223334444")

    	then:
    	pNum.number == "2223334444"
    	pNum.e164PhoneNumber == "+12223334444"
    	pNum.prettyPhoneNumber == "222 333 4444"
    }
}
