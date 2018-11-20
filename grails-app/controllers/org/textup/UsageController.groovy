package org.textup

import grails.compiler.GrailsTypeChecked
import javax.servlet.http.HttpSession
import org.joda.time.*
import org.joda.time.format.DateTimeFormat
import org.springframework.security.access.annotation.Secured
import org.textup.type.*

@GrailsTypeChecked
@Secured("ROLE_ADMIN")
class UsageController {

    final String SESSION_TIMEFRAME = "timeframe"
    final String CURRENT_TIME_FORMAT = "MMM dd, y h:mm a"
    final String DISPLAYED_MONTH_FORMAT = "MMM yyyy"

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
        session.setAttribute(SESSION_TIMEFRAME, params.timeframe)
        Long orgId = params.long("id")
        if (orgId) {
            redirect(action: "show", id: orgId)
        }
        else { redirect(action: "index") }
    }

    // Helpers
    // -------

    protected DateTime getTimeframe(HttpSession session) {
        Date storedTimeframe = session.getAttribute(SESSION_TIMEFRAME) as Date
        new DateTime(storedTimeframe ?: new Date())
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
            currentTime: DateTimeFormat.forPattern(CURRENT_TIME_FORMAT).print(DateTime.now())
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
    protected Map buildTimeframeParams(DateTime dt) {
        [
            timeframe: dt.toDate(),
            monthString: DateTimeFormat.forPattern(DISPLAYED_MONTH_FORMAT).print(dt),
            allowedYears: (UsageUtils.earliestAvailableYear())..(DateTime.now().year)
        ]
    }
}
