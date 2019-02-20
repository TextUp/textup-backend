package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.annotation.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class OrganizationService implements ManagesDomain.Updater<Organization> {

    LocationService locationService

    Result<Organization> tryFindOrCreate(TypeMap orgInfo) {
        Organization org1 = Organization.get(orgInfo.long("id"))
        if (org1) {
            IOCUtils.resultFactory.success(org1)
        }
        else {
            locationService.tryCreate(orgInfo.typeMapNoNull("location"))
                .then { Location loc1 -> Organization.tryCreate(orgInfo.string("name"), loc1) }
        }
    }

    @RollbackOnResultFailure
    Result<Organization> tryUpdate(Long orgId, TypeMap body) {
        Organizations.mustFindForId(orgId)
            .then { Organization org1 -> trySetFields(org1, body) }
            .then { Organization org1 ->
                locationService.tryUpdate(org1.location, body.typeMapNoNull("location")).curry(org1)
            }
            .then { Organization org1 -> DomainUtils.trySave(org1) }
    }

    // Helpers
    // -------

    protected Result<Organization> trySetFields(Organization org1, TypeMap body) {
        org1.with {
            if (body.name) name = body.name
            if (body.int("timeout") != null) timeout = body.int("timeout")
            if (body.awayMessageSuffix != null) awayMessageSuffix = body.awayMessageSuffix
        }
        DomainUtils.trySave(org1)
    }
}
