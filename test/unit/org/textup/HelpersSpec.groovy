package org.textup

import spock.lang.Specification
import grails.test.mixin.support.GrailsUnitTestMixin

@TestMixin(GrailsUnitTestMixin)
class HelpersSpec extends Specification {

    void "test parsing from list"() {
    	given:
    	List<Integer> toFind1 = [1, 2, 3, 4, 5]
        List<Integer> all1 = [1, 3, 5, 6, 7]
        List<Integer> all2 = [1, 2, 3, 4, 5, 6, 7]

    	when:
    	ParsedResult<Integer, Integer> p = Helpers.parseFromList(toFind1, all1)

        then: 
        p.valid == [1, 3, 5]
        p.invalid == [6, 7]

        when: 
        p = Helpers.parseFromList(toFind1, all2)

        then: 
        p.valid == toFind1
        p.invalid == [6, 7]
    }
}
