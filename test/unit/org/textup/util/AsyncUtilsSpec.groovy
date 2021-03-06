package org.textup.util

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.util.Holders
import java.util.concurrent.*
import org.apache.http.client.methods.*
import org.apache.http.HttpResponse
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.joda.time.DateTime
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([CustomAccountDetails, Location])
@TestMixin(HibernateTestMixin)
class AsyncUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    void "test getting all ids"() {
        given:
        Collection locs = []
        5.times { locs << TestUtils.buildLocation() }
        Location.withSession { it.flush() }

        when:
        Collection foundLocs = AsyncUtils.getAllIds(Location, locs*.id + [null, -88, null])

        then:
        foundLocs.size() == locs.size() // all found and null/not found ids excluded
        foundLocs.every { it instanceof Location }
        locs.every { it in foundLocs }
    }

    void "test generating a no-op Future object"() {
        when: "null payload"
        Future<?> fut1 = AsyncUtils.noOpFuture()

        then:
        fut1.cancel(true) == true
        fut1.get() == null
        fut1.get(1, TimeUnit.SECONDS) == null
        fut1.isCancelled() == false
        fut1.isDone() == true

        when: "specified payload"
        String msg = TestUtils.randString()
        Future<String> fut2 = AsyncUtils.noOpFuture(msg)

        then:
        fut2.cancel(true) == true
        fut2.get() == msg
        fut2.get(1, TimeUnit.SECONDS) == msg
        fut2.isCancelled() == false
        fut2.isDone() == true
    }

    void "test do asynchronously processing list in batches"() {
        given: "a list of items, an action to execute, and a batch size"
        int _numTimesCalled = 0
        int batchSize = 2
        List<Integer> before = [1, 2, 3, 4]
        Closure<Integer> action = { Integer beforeNum ->
            _numTimesCalled++
            beforeNum + 1
        }

        when: "calling async helper method"
        List<Integer> after = AsyncUtils.<Integer, Integer>doAsyncInBatches(before, batchSize, action)

        then: "all items in the list are processed"
        before.size() == _numTimesCalled
        after.eachWithIndex { Integer afterNum, int index ->
            assert before[index] + 1 == afterNum
        }
    }
}
