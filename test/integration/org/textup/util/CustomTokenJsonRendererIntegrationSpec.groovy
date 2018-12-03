package org.textup.util

import grails.plugin.springsecurity.rest.token.AccessToken
import grails.util.Holders
import groovy.json.*
import java.util.concurrent.*
import org.apache.http.client.methods.*
import org.joda.time.DateTime
import org.springframework.security.core.userdetails.*
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Specification

class CustomTokenJsonRendererIntegrationSpec extends Specification {

    CustomTokenJsonRenderer accessTokenJsonRenderer

    void "test generating json for access token"() {
        given:
        Organization org1 = new Organization(name: TestUtils.randString())
        org1.location = TestUtils.buildLocation()
        org1.save(flush:true, failOnError:true)

        Staff s1 = new Staff(username: TestUtils.randString(), password: "password",
            name: "Name", org: org1, personalPhoneAsString: TestUtils.randPhoneNumber(),
            email: "ok@ok.com", lockCode: Constants.DEFAULT_LOCK_CODE)
        s1.save(flush:true, failOnError:true)

        AccessToken accessToken = Mock()
        UserDetails userDetails = new User(TestUtils.randString(), TestUtils.randString(), [])
        userDetails.metaClass.id = s1.id

        String tokenString = TestUtils.randString()
        String refreshString = TestUtils.randString()
        Integer expiresIn = 888

        when:
        String jsonString = accessTokenJsonRenderer.generateJson(accessToken)
        Map jsonObj = DataFormatUtils.jsonToObject(jsonString)

        then:
        (1.._) * accessToken.principal >> userDetails
        (1.._) * accessToken.expiration >> expiresIn
        (1.._) * accessToken.refreshToken >> refreshString
        (1.._) * accessToken.accessToken >> tokenString

        jsonObj.staff instanceof Map
        jsonObj.access_token == tokenString
        jsonObj.refresh_token == refreshString
        jsonObj.expires_in == expiresIn
    }
}
