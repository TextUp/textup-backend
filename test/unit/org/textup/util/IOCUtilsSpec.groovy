package org.textup.util

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.test.runtime.DirtiesRuntime
import grails.util.Holders
import java.util.concurrent.*
import org.apache.http.client.methods.*
import org.apache.http.HttpResponse
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.joda.time.DateTime
import org.quartz.Scheduler
import org.springframework.context.*
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.textup.*
import org.textup.cache.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([CustomAccountDetails, Location])
@TestMixin(HibernateTestMixin)
class IOCUtilsSpec extends Specification {

    @DirtiesRuntime
    void "test getting beans from application context"() {
        given:
        ApplicationContext applicationContext = Mock()
        MockedMethod.create(Holders, "getApplicationContext") { applicationContext }

        when:
        IOCUtils.resultFactory

        then:
        1 * applicationContext.getBean(ResultFactory)

        when:
        IOCUtils.quartzScheduler

        then:
        1 * applicationContext.getBean(Scheduler)

        when:
        IOCUtils.linkGenerator

        then:
        1 * applicationContext.getBean(LinkGenerator)

        when:
        IOCUtils.messageSource

        then:
        1 * applicationContext.getBean(MessageSource)

        when:
        IOCUtils.security

        then:
        1 * applicationContext.getBean(SpringSecurityService)

        when:
        IOCUtils.authProvider

        then:
        1 * applicationContext.getBean(DaoAuthenticationProvider)

        when:
        IOCUtils.phoneCache

        then:
        1 * applicationContext.getBean(PhoneCache)
    }

    void "testing getting webhook link"() {
        given:
        IOCUtils.metaClass."static".getLinkGenerator = { ->
            [link: { Map m -> m.toString() }] as LinkGenerator
        }
        String handle = TestUtils.randString()

        when:
        String link = IOCUtils.getWebhookLink(handle: handle)

        then: "test the stringified map"
        link.contains(handle)
        link.contains("handle")
        link.contains("publicRecord")
        link.contains("save")
        link.contains("v1")
        link == IOCUtils.getHandleLink(handle)
    }

    void "test resolving message"() {
        given:
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }

        when: "from code"
        String code = TestUtils.randString()
        String msg = IOCUtils.getMessage(code, [1, 2, 3])

        then:
        msg == code

        when: "from resolvable object"
        Location emptyLoc1 = new Location()
        assert emptyLoc1.validate() == false
        msg = IOCUtils.getMessage(emptyLoc1.errors.allErrors[0])

        then:
        msg instanceof String
        msg != ""
    }
}
