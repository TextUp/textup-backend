package org.textup.job

import grails.gorm.DetachedCriteria
import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.joda.time.*
import org.quartz.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class DigestNotificationJobSpec extends Specification {

    void "test executing"() {
        given:
        DigestNotificationJob job = new DigestNotificationJob()

        JobExecutionContext context = GroovyMock()
        DetachedCriteria crit1 = GroovyMock()
        RecordItem rItem1 = GroovyMock()
        NotificationGroup notifGroup1 = GroovyMock()
        job.notificationService = GroovyMock(NotificationService)
        MockedMethod buildForIncomingMessagesAfter = MockedMethod.create(RecordItems, "buildForIncomingMessagesAfter") {
            crit1
        }
        MockedMethod tryBuildNotificationGroup = MockedMethod.create(NotificationUtils, "tryBuildNotificationGroup") {
            Result.createSuccess(notifGroup1)
        }
        ByteArrayOutputStream stdErr = TestUtils.captureAllStreamsReturnStdErr()

        when:
        job.execute(context)

        then:
        1 * context.mergedJobDataMap >> new JobDataMap()
        buildForIncomingMessagesAfter.notCalled
        tryBuildNotificationGroup.notCalled
        stdErr.toString().contains("could not find stored frequency")

        when:
        stdErr.reset()
        job.execute(context)

        then:
        1 * context.mergedJobDataMap >>
            new JobDataMap((DigestNotificationJob.FREQ_KEY): NotificationFrequency.HALF_HOUR)
        buildForIncomingMessagesAfter.latestArgs[0] instanceof DateTime
        buildForIncomingMessagesAfter.latestArgs[0]
            .isAfter(NotificationFrequency.HALF_HOUR.buildDateTimeInPast().minusMinutes(1))
        buildForIncomingMessagesAfter.latestArgs[0]
            .isBefore(NotificationFrequency.HALF_HOUR.buildDateTimeInPast().plusMinutes(1))
        1 * crit1.list() >> [rItem1]
        tryBuildNotificationGroup.latestArgs == [[rItem1]]
        1 * job.notificationService.send(NotificationFrequency.HALF_HOUR, notifGroup1) >> Result.void()
        stdErr.size() == 0

        cleanup:
        buildForIncomingMessagesAfter?.restore()
        tryBuildNotificationGroup?.restore()
        TestUtils.restoreAllStreams()
    }
}
