package org.textup

import grails.converters.JSON
import grails.compiler.GrailsTypeChecked
import javax.servlet.http.HttpSession
import org.joda.time.*
import org.joda.time.format.DateTimeFormat
import org.springframework.security.access.annotation.Secured
import org.textup.type.*

@GrailsTypeChecked
@Secured("ROLE_ADMIN")
class UsageController {

    final String SESSION_MONTH_KEY = "monthString"

    UsageService usageService

    def index() {
        DateTime dt = getTimeframe(session)
        List<UsageService.Organization> staffOrgs = usageService
                .getOverallPhoneActivity(dt, PhoneOwnershipType.INDIVIDUAL),
            teamOrgs = usageService.getOverallPhoneActivity(dt, PhoneOwnershipType.GROUP)
        [
            *: buildTimeframeParams(dt),
            *: buildContext(getNumPhones(staffOrgs), staffOrgs, getNumPhones(teamOrgs), teamOrgs),
            numActiveStaffPhones: getNumActivePhones(staffOrgs),
            numActiveTeamPhones: getNumActivePhones(teamOrgs),
            staffOrgs: staffOrgs,
            teamOrgs: teamOrgs
        ]
    }

    def show() {
        Long orgId = params.long("id")
        if (!orgId) {
            return redirect(action: "index")
        }
        DateTime dt = getTimeframe(session)
        List<UsageService.Staff> staffs = usageService.getStaffPhoneActivity(dt, orgId)
        List<UsageService.Team> teams = usageService.getTeamPhoneActivity(dt, orgId)
        [
            *: buildTimeframeParams(dt),
            *: buildContext(staffs.size() as BigDecimal, staffs, teams.size() as BigDecimal, teams),
            org: Organization.get(orgId),
            staffs: staffs,
            teams: teams
        ]
    }

    // Actions
    // -------

    def updateTimeframe() {
        session.setAttribute(SESSION_MONTH_KEY, params.timeframe)
        Long orgId = params.long("id")
        if (orgId) {
            redirect(action: "show", id: orgId)
        }
        else { redirect(action: "index") }
    }

    def ajaxGetActivity() {
        Integer currentMonthIndex = getMonthStringIndex(session)
        Map payload
        if (params.long("orgId")) {
            Long orgId = params.long("orgId")
            payload = [
                staffData: usageService.getActivityForOrg(PhoneOwnershipType.INDIVIDUAL, orgId),
                teamData: usageService.getActivityForOrg(PhoneOwnershipType.GROUP, orgId),
                currentMonthIndex: currentMonthIndex
            ]
        }
        else if (params.number) {
            payload = [
                numberData: usageService.getActivityForNumber(params.number as String),
                currentMonthIndex: currentMonthIndex
            ]
        }
        else {
            payload = [
                staffData: usageService.getActivity(PhoneOwnershipType.INDIVIDUAL),
                teamData: usageService.getActivity(PhoneOwnershipType.GROUP),
                currentMonthIndex: currentMonthIndex
            ]
        }
        render(payload as JSON)
    }

    // Helpers
    // -------

    protected DateTime getTimeframe(HttpSession session) {
        String monthString = session.getAttribute(SESSION_MONTH_KEY) as String
        DateTime dt = UsageUtils.monthStringToDateTime(monthString)
        dt ?: DateTime.now()
    }
    protected Map buildTimeframeParams(DateTime dt) {
        [
            monthString: UsageUtils.dateTimeToMonthString(dt),
            availableMonthStrings: UsageUtils.getAvailableMonthStrings()
        ]
    }
    protected Integer getMonthStringIndex(HttpSession session) {
        String currentMonthString = UsageUtils.dateTimeToMonthString(getTimeframe(session))
        UsageUtils.getAvailableMonthStrings().findIndexOf { String m1 -> m1 == currentMonthString }
    }

    protected Map buildContext(BigDecimal numStaffPhones,
        Collection<? extends UsageService.HasActivity> staffList,
        BigDecimal numTeamPhones, Collection<? extends UsageService.HasActivity> teamList) {

        [
            staffTotalCost: getTotalCost(staffList),
            staffUsageCost: getUsageCost(staffList),
            staffCallCost: getCallCost(staffList),
            staffTextCost: getTextCost(staffList),
            numStaffPhones: numStaffPhones,
            numStaffTexts: getNumTexts(staffList),
            numStaffSegments: getNumSegments(staffList),
            numStaffCalls: getNumCalls(staffList),
            numStaffMinutes: getNumMinutes(staffList),
            numStaffBillableMinutes: getNumBillableMinutes(staffList),

            teamTotalCost: getTotalCost(teamList),
            teamUsageCost: getUsageCost(teamList),
            teamCallCost: getCallCost(teamList),
            teamTextCost: getTextCost(teamList),
            numTeamPhones: numTeamPhones,
            numTeamTexts: getNumTexts(teamList),
            numTeamSegments: getNumSegments(teamList),
            numTeamCalls: getNumCalls(teamList),
            numTeamMinutes: getNumMinutes(teamList),
            numTeamBillableMinutes: getNumBillableMinutes(teamList),

            currentTime: UsageUtils.dateTimeToTimestamp(DateTime.now())
        ]
    }
    protected BigDecimal getTotalCost(Collection<? extends UsageService.HasActivity> aList) {
        UsageUtils.sumProperty(aList) { UsageService.HasActivity a1 ->
            a1.totalCost
        }
    }
    protected BigDecimal getUsageCost(Collection<? extends UsageService.HasActivity> aList) {
        UsageUtils.sumProperty(aList) { UsageService.HasActivity a1 ->
            a1.activity.cost
        }
    }
    protected BigDecimal getCallCost(Collection<? extends UsageService.HasActivity> aList) {
        UsageUtils.sumProperty(aList) { UsageService.HasActivity a1 ->
            a1.activity.callCost
        }
    }
    protected BigDecimal getTextCost(Collection<? extends UsageService.HasActivity> aList) {
        UsageUtils.sumProperty(aList) { UsageService.HasActivity a1 ->
            a1.activity.textCost
        }
    }
    protected BigDecimal getNumTexts(Collection<? extends UsageService.HasActivity> aList) {
        UsageUtils.sumProperty(aList) { UsageService.HasActivity a1 ->
            a1.activity.numTexts
        }
    }
    protected BigDecimal getNumSegments(Collection<? extends UsageService.HasActivity> aList) {
        UsageUtils.sumProperty(aList) { UsageService.HasActivity a1 ->
            a1.activity.numSegments
        }
    }
    protected BigDecimal getNumCalls(Collection<? extends UsageService.HasActivity> aList) {
        UsageUtils.sumProperty(aList) { UsageService.HasActivity a1 ->
            a1.activity.numCalls
        }
    }
    protected BigDecimal getNumMinutes(Collection<? extends UsageService.HasActivity> aList) {
        UsageUtils.sumProperty(aList) { UsageService.HasActivity a1 ->
            a1.activity.numMinutes
        }
    }
    protected BigDecimal getNumBillableMinutes(Collection<? extends UsageService.HasActivity> aList) {
        UsageUtils.sumProperty(aList) { UsageService.HasActivity a1 ->
            a1.activity.numBillableMinutes
        }
    }

    protected BigDecimal getNumPhones(Collection<UsageService.Organization> orgs) {
        UsageUtils.sumProperty(orgs) { UsageService.Organization o1 ->
            o1.totalNumPhones as BigDecimal
        }
    }
    // numActivePhones is really only meaningful in an organizational context
    protected BigDecimal getNumActivePhones(Collection<UsageService.Organization> aList) {
        UsageUtils.sumProperty(aList) { UsageService.Organization a1 ->
            a1.activity.numActivePhones as BigDecimal
        }
    }
}
