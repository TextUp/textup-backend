package org.textup.util

import grails.converters.JSON
import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.rendering.AccessTokenJsonRenderer
import groovy.json.JsonSlurper
import groovy.util.logging.Log4j
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.util.Assert
import org.textup.Staff

@Log4j
class CustomTokenJsonRenderer implements AccessTokenJsonRenderer {

    @Autowired
    GrailsApplication grailsApplication

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
            (authoritiesPropertyName) : userDetails.authorities.collect {GrantedAuthority role -> role.authority },
            id: userDetails.id
        ]
        // add additional information about the staff
        Staff.withNewSession {
            Staff staff = Staff.get(userDetails.id)
            if (staff) {
                JSON.use(grailsApplication.flatConfig["textup.rest.defaultLabel"]) {
                    result.staff = new JsonSlurper().parseText((staff as JSON).toString())
                }
            }
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
        def jsonResult = result as JSON
        log.debug "CUSTOM Generated JSON:\n${jsonResult.toString(true)}"
        return jsonResult.toString()
    }
}
