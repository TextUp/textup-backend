package org.textup.override

import grails.converters.JSON
import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.rendering.AccessTokenJsonRenderer
import groovy.json.JsonSlurper
import groovy.util.logging.Log4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.util.Assert
import org.textup.*
import org.textup.util.*

@Log4j
class CustomTokenJsonRenderer implements AccessTokenJsonRenderer {

    Boolean useBearerToken
    String authoritiesPropertyName
    String tokenPropertyName
    String usernamePropertyName

    @Override
    String generateJson(AccessToken accessToken) {
        Assert.isInstanceOf(UserDetails, accessToken.principal, "A UserDetails implementation is required")
        // add basic information
        UserDetails userDetails = accessToken.principal as UserDetails
        Map result = [
            (usernamePropertyName) : userDetails.username,
            (authoritiesPropertyName) : userDetails.authorities?.collect { GrantedAuthority role -> role.authority },
            id: userDetails.id
        ]
        // add additional information about the staff
        Staff.withNewSession {
            Staff s1 = Staff.get(userDetails.id)
            result.staff = s1 ? DataFormatUtils.jsonToObject(s1) : null
        }
        // add token information
        if (useBearerToken) {
            result.token_type = "Bearer"
            result.access_token = accessToken.accessToken
            if (accessToken.expiration) {
                result.expires_in = accessToken.expiration
            }
            if (accessToken.refreshToken) {
                result.refresh_token = accessToken.refreshToken
            }
        }
        else {
            result["$tokenPropertyName"] = accessToken.accessToken
        }
        // build json string result
        new JSON(result)
    }
}
