package org.textup

import grails.compiler.GrailsCompileStatic
import grails.transaction.Transactional
import grails.validation.ValidationErrors
import static org.springframework.http.HttpStatus.*

@GrailsCompileStatic
@Transactional
class OrganizationService {

	ResultFactory resultFactory

    Result<Organization> update(Long orgId, Map body) {
    	Organization org = Organization.get(orgId)
    	if (!org) {
    		return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "organizationService.update.notFound", [orgId])
    	}
        if (body.name) { org.name = body.name }
        if (Helpers.toInteger(body.timeout) != null) {
            org.timeout = Helpers.toInteger(body.timeout)
        }
    	if (body.location instanceof Map) {
    		Map l = body.location as Map
    		org.location.with {
    			if (l.address) address = l.address
                if (l.lat) lat = Helpers.toBigDecimal(l.lat)
                if (l.lon) lon = Helpers.toBigDecimal(l.lon)
    		}
            if (!org.location.save()) {
                return resultFactory.failWithValidationErrors(org.location.errors)
            }
    	}
    	if (org.save()) {
            resultFactory.success(org)
        }
    	else { resultFactory.failWithValidationErrors(org.errors) }
    }
}
