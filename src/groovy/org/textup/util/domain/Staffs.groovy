package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class Staffs {

    // TODO hasPermissionsForStaff
    // [NOTE] have to make two calls because can't figure out how to return an association
    // property projection. Seems to work for Criteria but not DetachedCriteria
    static Result<Void> isAllowed(Long thisId) {
        AuthUtils.tryGetAuthId()
            .then { Long authId -> AuthUtils.isAllowed(thisId != null).curry(authId) }
            .then { Long authId ->
                // Can have permission for this staff if...
                AuthUtils.isAllowed(
                    // (1) You are this staff member
                    thisId == authId ||
                    // (2) You are on a same team as this staff member
                    Teams.hasTeamsInCommon(thisId, authId) ||
                    // (3) You are an admin at this staff member's organization
                    buildForAdminAtSameOrg(thisId, authId).count() > 0
                )
            }
    }

    static DetachedCriteria<Staff> buildForIdsAndStatuses(Collection<Long> ids,
        Collection<StaffStatus> statuses) {

        new DetachedCriteria(Staff)
            .build { CriteriaUtils.inList(delegate, "id", ids) }
            .build(forStatuses(statuses))
    }

    static DetachedCriteria<Staff> buildForOrgIdAndOptions(Long orgId, String query = null,
        Collection<StaffStatus> statuses = StaffStatus.ACTIVE_STATUSES) {

        new DetachedCriteria(Staff)
            .build { eq("org.id", orgId) }
            .build(forQuery(query))
            .build(forStatuses(statuses))
    }

    // Grails-managed join tables cannot be directly queried
    static HashSet<Staff> findEveryForSharingId(Long shareWithStaffId) {
        HashSet<Staff> shareCandidates = new HashSet<>()
        List<Teams> teams = Teams.buildForStaffIds([shareWithStaffId]).list()
        teams.each { Team t1 ->
            // add only staff members that have a phone!
            shareCandidates.addAll(t1.activeMembers.findAll { Staff s1 -> s1.phone })
        }
        // exclude the staff members we are finding share candidates for
        shareCandidates.findAll { Staff s1 -> s1.id != shareWithStaffId }
    }

    static Closure returnsOrgId() {
        return {
            projections {
                property("org.id")
            }
        }
    }

    // Helpers
    // -------

    protected static forQuery(String query) {
        if (!query) {
            return { }
        }
        return {
            or {
                String formattedQuery = StringUtils.toQuery(query)
                ilike("name", formattedQuery)
                ilike("email", formattedQuery)
                ilike("username", formattedQuery)

                String cleanedAsNumber = StringUtils.cleanPhoneNumber(query)
                if (cleanedAsNumber) {
                    ilike("personalPhoneAsString", StringUtils.toQuery(cleanedAsNumber))
                }
            }
        }
    }

    protected static forStatuses(Collection<StaffStatus> statuses) {
        return {
            CriteriaUtils.inList(delegate, "status", statuses)
        }
    }

    protected static DetachedCriteria<Staff> buildForAdminAtSameOrg(Long thisId, Long authId) {
        new DetachedCriteria(Staff).build {
            idEq(thisId)
            "in"("org", Organizations.buildActiveForAdminIds([authId]))
        }
    }
}
