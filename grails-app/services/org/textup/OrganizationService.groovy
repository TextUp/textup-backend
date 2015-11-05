package org.textup

import grails.transaction.Transactional
import grails.validation.ValidationErrors
import static org.springframework.http.HttpStatus.*

@Transactional
class OrganizationService {

	def resultFactory

    Result<Organization> update(Long orgId, Map body) {
    	Organization org = Organization.get(orgId)
    	if (!org) { 
    		return resultFactory.failWithMessageAndStatus(NOT_FOUND, 
                "organizationService.update.notFound", [orgId])
    	}
    	org.name = body.name ?: org.name
    	if (body.location) {
    		def l = body.location
    		org.location.with {
    			if (l.address) address = l.address
                if (l.lat) lat = l.lat
                if (l.lon) lon = l.lon
    		}
            if (!org.location.save()) {
                return resultFactory.failWithValidationErrors(org.location.errors)
            }
    	}
    	if (org.save()) { resultFactory.success(org) }
    	else { resultFactory.failWithValidationErrors(org.errors) }
    }
}
