package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class Organizations {

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
            .build(forStatuses())
    }

    // Helpers
    // -------

    protected static Closure forQuery(String query) {
        return {
            if (query) {
                String formattedQuery = StringUtils.toQuery(query)
                or {
                    ilike("name", formattedQuery)
                    ilike("location.address", formattedQuery)
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
