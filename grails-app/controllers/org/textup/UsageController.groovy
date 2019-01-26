package org.textup

import grails.converters.JSON
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import javax.servlet.http.HttpSession
import org.joda.time.*
import org.joda.time.format.DateTimeFormat
import org.springframework.security.access.annotation.Secured
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@GrailsTypeChecked
@Secured("ROLE_ADMIN")
@Transactional
class UsageController {

    static final String SESSION_MONTH_KEY = "monthString"

    UsageService usageService

    def index() {
        DateTime dt = getTimeframe(session)
        List<ActivityEntity.Organization> staffOrgs = usageService
                .getOverallPhoneActivity(dt, PhoneOwnershipType.INDIVIDUAL),
            teamOrgs = usageService.getOverallPhoneActivity(dt, PhoneOwnershipType.GROUP)
        [
            *: buildTimeframeParams(dt),
            staffUsageAndCosts: buildUsageAndCosts(staffOrgs),
            teamUsageAndCosts: buildUsageAndCosts(teamOrgs),
            staffPhoneCounts: buildPhoneCounts(staffOrgs),
            teamPhoneCounts: buildPhoneCounts(teamOrgs),
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
        List<ActivityEntity.Staff> staffs = usageService.getStaffPhoneActivity(dt, orgId)
        List<ActivityEntity.Team> teams = usageService.getTeamPhoneActivity(dt, orgId)
        [
            *: buildTimeframeParams(dt),
            staffUsageAndCosts: buildUsageAndCosts(staffs),
            teamUsageAndCosts: buildUsageAndCosts(teams),
            staffPhoneCounts: buildPhoneCounts(staffs.size(), null),
            teamPhoneCounts: buildPhoneCounts(teams.size(), null),
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
        Integer currentMonthIndex = UsageUtils.getAvailableMonthStringIndex(getTimeframe(session))
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
            availableMonthStrings: UsageUtils.getAvailableMonthStrings(),
            currentTime: UsageUtils.dateTimeToTimestamp(DateTime.now())
        ]
    }

    protected Map buildUsageAndCosts(Collection<? extends ActivityEntity.HasActivity> aList) {
        [
            totalCost: aList.sum { ActivityEntity.HasActivity a1 -> a1.totalCost },
            usageCost: aList.sum { ActivityEntity.HasActivity a1 -> a1.activity.cost },
            textCost: aList.sum { ActivityEntity.HasActivity a1 -> a1.activity.textCost },
            callCost: aList.sum { ActivityEntity.HasActivity a1 -> a1.activity.callCost },
            numTexts: aList.sum { ActivityEntity.HasActivity a1 -> a1.activity.numTexts },
            numSegments: aList.sum { ActivityEntity.HasActivity a1 -> a1.activity.numSegments },
            numCalls: aList.sum { ActivityEntity.HasActivity a1 -> a1.activity.numCalls },
            numMinutes: aList.sum { ActivityEntity.HasActivity a1 -> a1.activity.numMinutes },
            numBillableMinutes: aList
                .sum { ActivityEntity.HasActivity a1 -> a1.activity.numBillableMinutes }
        ]
    }

    // numActivePhones is really only meaningful in an organizational context
    protected Map buildPhoneCounts(Collection<ActivityEntity.Organization> orgs) {
        [
            numPhones: orgs.sum { ActivityEntity.Organization o1 -> o1.totalNumPhones },
            numActivePhones: orgs.sum { ActivityEntity.Organization o1 -> o1.activity.numActivePhones }
        ]
    }

    protected Map buildPhoneCounts(Number numPhones, Number numActivePhones) {
        [
            numPhones: numPhones,
            numActivePhones: numActivePhones
        ]
    }
}
