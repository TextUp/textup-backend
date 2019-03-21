package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

class Staffs {

    @GrailsTypeChecked
    static Result<Long> isAllowed(Long thisId) {
        AuthUtils.isAllowed(thisId != null)
            .then { AuthUtils.tryGetAuthId() }
            .then { Long authId ->
                // Can have permission for this staff if...
                AuthUtils.isAllowed(
                    // (1) You are this staff member
                    thisId == authId ||
                    // (2) You are an admin at this staff member's organization
                    buildForAdminAtSameOrg(thisId, authId).count() > 0
                )
            }
            .then { IOCUtils.resultFactory.success(thisId) }
    }

    @GrailsTypeChecked
    static Result<Staff> mustFindForId(Long staffId) {
        Staff s1 = staffId ? Staff.get(staffId) : null
        if (s1) {
            IOCUtils.resultFactory.success(s1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("staffs.notFoundForId",
                ResultStatus.NOT_FOUND, [staffId])
        }
    }

    @GrailsTypeChecked
    static Result<Staff> mustFindForUsername(String un) {
        Staff s1 = Staff.findByUsername(StringUtils.cleanUsername(un))
        if (s1) {
            IOCUtils.resultFactory.success(s1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("staffs.notFoundForUsername",
                ResultStatus.NOT_FOUND, [un])
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
    @GrailsTypeChecked
    static Collection<Staff> findEveryForSharingId(Long shareWithStaffId) {
        HashSet<Staff> shareCandidates = new HashSet<>()
        if (shareWithStaffId) {
            List<Team> teams = Teams.buildActiveForStaffIds([shareWithStaffId]).list()
            teams.each { Team t1 ->
                // add only staff members that have a phone!
                Collection<Staff> staffWitPhones = t1.activeMembers.findAll { Staff s1 ->
                    !!IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
                }
                shareCandidates.addAll(staffWitPhones)
            }
        }
        // exclude the staff members we are finding share candidates for
        shareCandidates.findAll { Staff s1 -> s1.id != shareWithStaffId }
    }

    @GrailsTypeChecked
    static Collection<Staff> findEveryForRecordIds(Collection<Long> recIds) {
        if (recIds) {
            List<Phone> phones = PhoneRecords.buildActiveForRecordIds(recIds)
                .build(PhoneRecords.returnsPhone())
                .list() as List<Phone>
            CollectionUtils.mergeUnique(phones*.owner*.buildAllStaff())
        }
        else { [] }
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

    protected static Closure forQuery(String query) {
        String formattedQuery = StringUtils.toQuery(query)
        String possibleNum = StringUtils.cleanPhoneNumber(query)
        String numberQuery = StringUtils.toQuery(possibleNum)
        return {
            if (formattedQuery) {
                or {
                    ilike("name", formattedQuery)
                    ilike("email", formattedQuery)
                    ilike("username", formattedQuery)
                    if (possibleNum) {
                        ilike("personalNumberAsString", numberQuery)
                    }
                }
            }
        }
    }

    protected static Closure forStatuses(Collection<StaffStatus> statuses) {
        return {
            CriteriaUtils.inList(delegate, "status", statuses)
        }
    }

    protected static DetachedCriteria<Staff> buildForAdminAtSameOrg(Long thisId, Long authId) {
        new DetachedCriteria(Staff).build {
            idEq(thisId)
            "in"("org.id", Organizations
                .buildActiveForAdminIds([authId])
                .build(CriteriaUtils.returnsId()))
        }
    }
}
