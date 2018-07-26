package org.textup

import grails.compiler.GrailsCompileStatic
import grails.transaction.Transactional
import org.textup.util.RollbackOnResultFailure

@GrailsCompileStatic
@Transactional
class OrganizationService {

	ResultFactory resultFactory

    @RollbackOnResultFailure
    Result<Organization> update(Long orgId, Map body) {
    	Organization org = Organization.get(orgId)
    	if (!org) {
    		return resultFactory.failWithCodeAndStatus("organizationService.update.notFound",
                ResultStatus.NOT_FOUND, [orgId])
    	}
        if (body.name) { org.name = body.name }
        if (Helpers.to(Integer, body.timeout) != null) {
            org.timeout = Helpers.to(Integer, body.timeout)
        }
    	if (body.location instanceof Map) {
    		Map l = body.location as Map
    		org.location.with {
    			if (l.address) address = l.address
                if (l.lat) lat = Helpers.to(BigDecimal, l.lat)
                if (l.lon) lon = Helpers.to(BigDecimal, l.lon)
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
