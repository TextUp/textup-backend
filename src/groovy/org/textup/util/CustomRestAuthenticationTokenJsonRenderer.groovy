package org.textup.util

import com.odobo.grails.plugin.springsecurity.rest.RestAuthenticationToken
import com.odobo.grails.plugin.springsecurity.rest.token.rendering.RestAuthenticationTokenJsonRenderer
import grails.converters.JSON
import groovy.json.JsonSlurper
import groovy.util.logging.Log4j
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.util.Assert
import org.textup.Staff

@Log4j
class CustomRestAuthenticationTokenJsonRenderer implements RestAuthenticationTokenJsonRenderer {

    @Autowired
    GrailsApplication grailsApplication

    Boolean useBearerToken
    String authoritiesPropertyName
    String tokenPropertyName
    String usernamePropertyName

    @Override
    String generateJson(RestAuthenticationToken restAuthenticationToken) {
        Assert.isInstanceOf(UserDetails, restAuthenticationToken.principal, "A UserDetails implementation is required")
        UserDetails userDetails = restAuthenticationToken.principal as UserDetails

        Map result = [
            (usernamePropertyName) : userDetails.username,
            (authoritiesPropertyName) : userDetails.authorities.collect {GrantedAuthority role -> role.authority },
            id: userDetails.id
        ]
        Staff.withNewSession {
            Staff staff = Staff.get(userDetails.id)
            if (staff) {
                Map data = [:]
                JSON.use('default') {
                    data += new JsonSlurper().parseText((staff as JSON).toString())
                }
                result.staff = data
            }
        }
        result["$tokenPropertyName"] = restAuthenticationToken.tokenValue
        if (useBearerToken) { result.token_type = 'Bearer' }
        def jsonResult = result as JSON
        log.debug "CUSTOM Generated JSON:\n${jsonResult.toString(true)}"
        return jsonResult.toString()
    }
}
