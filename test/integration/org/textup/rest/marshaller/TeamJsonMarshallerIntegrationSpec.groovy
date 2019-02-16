package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class TeamJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling"() {
        given:
        Team t1 = TestUtils.buildTeam()
        Phone tp1 = TestUtils.buildActiveTeamPhone(t1)

        when:
        Map json = TestUtils.objToJsonMap(t1)

        then:
        json.hexColor == t1.hexColor
        json.id == t1.id
        json.location instanceof Map
        json.location.id == t1.location.id
        json.name == t1.name
        json.numMembers == t1.activeMembers.size()
        json.org == t1.org.id
        json.phone instanceof Map
        json.phone.id == tp1.id
    }
}
