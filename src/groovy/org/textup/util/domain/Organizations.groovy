package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

class Organizations {

    @GrailsTypeChecked
    static Result<Long> isAllowed(Long thisId) {
        AuthUtils.tryGetAuthId()
            .then { Long authId -> Organizations.tryIfAdmin(thisId, authId) }
            .then { IOCUtils.resultFactory.success(thisId) }
    }

    @GrailsTypeChecked
    static Result<Void> tryIfAdmin(Long orgId, Long staffId) {
        Staffs.mustFindForId(staffId)
            .then { Staff s1 -> AuthUtils.isAllowed(isAdminAt(orgId, s1)) }
    }

    static boolean isAdminAt(Long orgId, Staff s1) {
        s1?.status == StaffStatus.ADMIN && s1?.org?.id == orgId
    }

    @GrailsTypeChecked
    static Result<Organization> mustFindForId(Long orgId) {
        Organization org1 = orgId ? Organization.get(orgId) : null
        if (org1) {
            IOCUtils.resultFactory.success(org1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("organizations.notFound",
                ResultStatus.NOT_FOUND, [orgId])
        }
    }

    static DetachedCriteria<Organization> buildActiveForAdminIds(Collection<Long> adminIds) {
        new DetachedCriteria(Organization)
            .build {
                "in"("id", Staffs.buildForIdsAndStatuses(adminIds, [StaffStatus.ADMIN])
                    .build(Staffs.returnsOrgId()))
            }
            .build(forStatuses(OrgStatus.ACTIVE_STATUSES))
    }

    static DetachedCriteria<Organization> buildForOptions(String query = null,
        Collection<OrgStatus> orgStatuses = OrgStatus.ACTIVE_STATUSES) {

        new DetachedCriteria(Organization)
            .build(forQuery())
            .build(forStatuses(orgStatuses))
    }

    static DetachedCriteria<Organization> buildForNameAndLatLng(String name, BigDecimal lat,
        BigDecimal lng) {

        new DetachedCriteria(Organization)
            .build {
                eq("name", name)
                location {
                    eq("lat", lat)
                    eq("lng", lng)
                }
            }
    }

    // Helpers
    // -------

    protected static Closure forQuery(String query) {
        return {
            if (query) {
                String formattedQuery = StringUtils.toQuery(query)
                or {
                    ilike("name", formattedQuery)
                    location {
                        ilike("address", formattedQuery)
                    }
                }
            }
        }
    }

    protected static Closure forStatuses(Collection<OrgStatus> orgStatuses) {
        return {
            CriteriaUtils.inList(delegate, "status", orgStatuses)
        }
    }
}
