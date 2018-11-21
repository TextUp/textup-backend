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
            *: buildContext(staffOrgs, teamOrgs),
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
            *: buildContext(staffs, teams),
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

    protected Map buildContext(Collection<? extends UsageService.HasActivity> staffList,
        Collection<? extends UsageService.HasActivity> teamList) {

        [
            numStaffPhones: staffList.size(),
            numStaffSegments: getNumSegments(staffList),
            numStaffMinutes: getNumMinutes(staffList),
            numTeamPhones: teamList.size(),
            numTeamSegments: getNumSegments(teamList),
            numTeamMinutes: getNumMinutes(teamList),
            currentTime: UsageUtils.dateTimeToTimestamp(DateTime.now())
        ]
    }
    protected BigDecimal getNumSegments(Collection<? extends UsageService.HasActivity> activityOwners) {
        BigDecimal numSegments = 0
        activityOwners?.each { UsageService.HasActivity a1 ->
            if (a1.activity.numBillableSegments) {
                numSegments += a1.activity.numBillableSegments
            }
        }
        numSegments
    }
    protected BigDecimal getNumMinutes(Collection<? extends UsageService.HasActivity> activityOwners) {
        BigDecimal numMinutes = 0
        activityOwners?.each { UsageService.HasActivity a1 ->
            if (a1.activity.numBillableMinutes) {
                numMinutes += a1.activity.numBillableMinutes
            }
        }
        numMinutes
    }
}
