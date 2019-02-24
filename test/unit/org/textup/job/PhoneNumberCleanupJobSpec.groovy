package org.textup.job

import grails.test.mixin.*
import grails.test.mixin.support.*
import org.apache.commons.logging.Log
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class PhoneNumberCleanupJobSpec extends Specification {

    void "test calling service to delete jobs"() {
        given:
        PhoneNumberCleanupJob job = new PhoneNumberCleanupJob()
        job.numberService = GroovyMock(NumberService)
        job.log = GroovyMock(Log)

        when: "success"
        job.execute()

        then:
        1 * job.numberService.cleanupInternalNumberPool() >>
            Result.createSuccess(Tuple.create([], []))
        1 * job.log.info(*_)

        when: "an error happens"
        job.execute()

        then: "logger not called"
        1 * job.numberService.cleanupInternalNumberPool() >>
            Result.createError([], ResultStatus.BAD_REQUEST)
        0 * job.log._
    }
}
