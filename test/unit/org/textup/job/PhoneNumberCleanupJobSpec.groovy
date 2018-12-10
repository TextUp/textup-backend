package org.textup.job

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.apache.commons.logging.Log
import org.textup.*
import org.textup.type.*
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class PhoneNumberCleanupJobSpec extends Specification {

    void "test calling service to delete jobs"() {
        given:
        PhoneNumberCleanupJob job = new PhoneNumberCleanupJob()
        job.numberService = GroovyMock(NumberService)
        job.log = Mock(Log)

        when: "success"
        job.execute()

        then:
        1 * job.numberService.cleanupInternalNumberPool() >>
            new Result(payload: Tuple.create([], []))
        1 * job.log.info(*_)

        when: "an error happens"
        job.execute()

        then: "logger not called"
        1 * job.numberService.cleanupInternalNumberPool() >>
            new Result(status: ResultStatus.BAD_REQUEST)
        0 * job.log._
    }
}
