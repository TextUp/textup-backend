package org.textup

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.textup.util.*
import spock.lang.*

@Domain([Organization, Location])
@TestMixin(HibernateTestMixin)
class ResultGroupSpec extends Specification {

    void "test adding items and composition"() {
        when: "group is empty"
        ResultGroup<Location> resGroup = new ResultGroup<>()
        Result<Location> succ1 = Result.createSuccess(TestUtils.buildLocation(), ResultStatus.CREATED)
        Result<Location> succ2 = Result.createSuccess(TestUtils.buildLocation(), ResultStatus.CREATED)
        Result<Location> succ3 = Result.createSuccess(TestUtils.buildLocation(), ResultStatus.OK)
        Result<Location> fail1 = Result.createError(["could not create!"], ResultStatus.UNPROCESSABLE_ENTITY)
        Result<Location> fail2 = Result.createError(["could not create!"], ResultStatus.BAD_REQUEST)
        Result<Location> fail3 = Result.createError(["could not create!"], ResultStatus.UNPROCESSABLE_ENTITY)

        then:
        resGroup.isEmpty == true
        resGroup.anySuccesses == false
        resGroup.anyFailures == false
        resGroup.successStatus == null
        resGroup.failureStatus == null
        resGroup.successes.isEmpty() == true
        resGroup.failures.isEmpty() == true
        resGroup.payload.isEmpty() == true

        when:
        resGroup << [succ1, fail1]
        resGroup.add([succ2, fail2])
        resGroup << new ResultGroup([succ3, fail3])

        then:
        resGroup.isEmpty == false
        resGroup.anySuccesses == true
        resGroup.anyFailures == true
        resGroup.successStatus == ResultStatus.CREATED
        resGroup.failureStatus == ResultStatus.UNPROCESSABLE_ENTITY
        resGroup.successes.size() == 3
        resGroup.failures.size() == 3
        resGroup.payload.size() == 3
    }
}
