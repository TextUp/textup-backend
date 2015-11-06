package org.textup.util

import org.textup.Staff
import com.odobo.grails.plugin.springsecurity.rest.token.rendering.RestAuthenticationTokenJsonRenderer
import com.odobo.grails.plugin.springsecurity.rest.RestAuthenticationToken
import groovy.util.logging.Log4j
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.util.Assert
import grails.converters.JSON

@Log4j
class CustomRestAuthenticationTokenJsonRenderer implements RestAuthenticationTokenJsonRenderer {
    String usernamePropertyName
    String tokenPropertyName
    String authoritiesPropertyName
    Boolean useBearerToken

    @Override
    String generateJson(RestAuthenticationToken restAuthenticationToken) {
        Assert.isInstanceOf(UserDetails, restAuthenticationToken.principal, "A UserDetails implementation is required")
        UserDetails userDetails = restAuthenticationToken.principal as UserDetails

        def result = [
            (usernamePropertyName) : userDetails.username,
            (authoritiesPropertyName) : userDetails.authorities.collect {GrantedAuthority role -> role.authority },
            id : userDetails.id
        ]
        Staff.withNewSession {
            Staff staff = Staff.get(userDetails.id)
            if (staff) {
                result.name = staff.name
                result.email = staff.email
                result.status = staff.status
            }
        }
        result["$tokenPropertyName"] = restAuthenticationToken.tokenValue
        if (useBearerToken) { result.token_type = 'Bearer' }
        def jsonResult = result as JSON
        log.debug "CUSTOM Generated JSON:\n${jsonResult.toString(true)}"
        return jsonResult.toString()
    }
}
