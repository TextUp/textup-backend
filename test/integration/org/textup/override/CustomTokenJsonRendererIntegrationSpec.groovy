package org.textup.override

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
        String un1 = TestUtils.randString()
        String pwd1 = TestUtils.randString()
        String tokenString = TestUtils.randString()
        String refreshString = TestUtils.randString()
        Integer expiresIn = TestUtils.randIntegerUpTo(88, true)

        Staff s1 = TestUtils.buildStaff()
        UserDetails userDetails = new User(un1, pwd1, [])
        userDetails.metaClass.id = s1.id

        AccessToken accessToken = GroovyMock()

        when:
        String jsonString = accessTokenJsonRenderer.generateJson(accessToken)
        Map jsonObj = DataFormatUtils.jsonToObject(jsonString)

        then:
        (1.._) * accessToken.principal >> userDetails
        (1.._) * accessToken.expiration >> expiresIn
        (1.._) * accessToken.refreshToken >> refreshString
        (1.._) * accessToken.accessToken >> tokenString

        jsonObj[accessTokenJsonRenderer.usernamePropertyName] == un1
        jsonObj[accessTokenJsonRenderer.authoritiesPropertyName] == []
        jsonObj.staff instanceof Map
        jsonObj.staff.id == s1.id
        jsonObj.access_token == tokenString
        jsonObj.refresh_token == refreshString
        jsonObj.expires_in == expiresIn
    }
}
