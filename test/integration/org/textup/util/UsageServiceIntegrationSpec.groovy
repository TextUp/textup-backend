package org.textup.util

import java.math.RoundingMode
import java.text.DecimalFormat
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

// This needs to be an integration test because we need to run H2 with MySQL compatibility
// parameters for the raw SQL in this service to execute without errors

class UsageServiceIntegrationSpec extends Specification {

    UsageService usageService
    DecimalFormat decimalFormat

    Organization org1
    Organization org2
    Staff s1
    Staff s2
    Team t1
    Team t2
    Phone p1
    Phone p2
    Phone tp1
    Phone tp2
    IndividualPhoneRecord ipr1
    IndividualPhoneRecord ipr2
    IndividualPhoneRecord ipr3
    IndividualPhoneRecord ipr4

    def setup() {
        decimalFormat = new DecimalFormat("#.##")
        decimalFormat.setRoundingMode(RoundingMode.CEILING)

        org1 = TestUtils.buildOrg()
        org2 = TestUtils.buildOrg()

        s1 = TestUtils.buildStaff(org1)
        s2 = TestUtils.buildStaff(org2)

        t1 = TestUtils.buildTeam(org1)
        t1.addToMembers(s1)
        t2 = TestUtils.buildTeam(org2)
        t2.addToMembers(s2)

        p1 = TestUtils.buildActiveStaffPhone(s1)
        p2 = TestUtils.buildActiveStaffPhone(s2)

        tp1 = TestUtils.buildActiveTeamPhone(t1)
        tp2 = TestUtils.buildActiveTeamPhone(t2)

        ipr1 = TestUtils.buildIndPhoneRecord(p1)
        ipr2 = TestUtils.buildIndPhoneRecord(p2)
        ipr3 = TestUtils.buildIndPhoneRecord(tp1)
        ipr4 = TestUtils.buildIndPhoneRecord(tp1)

        RecordText rText1 = new RecordText(record: ipr1.record, contents: TestUtils.randString())
        RecordText rText2 = new RecordText(record: ipr2.record, contents: TestUtils.randString())
        RecordText rText3 = new RecordText(record: ipr3.record, contents: TestUtils.randString())
        RecordText rText4 = new RecordText(record: ipr4.record, contents: TestUtils.randString())
        [rText1, rText2, rText3, rText4].each {
            it.addReceipt(TestUtils.buildTempReceipt())
            it.save(flush: true, failOnError: true)
        }
    }

    void "test getting activity for a single month for all orgs"() {
        given:
        DateTime thisMonth = DateTime.now()
        DateTime priorMonth = DateTime.now().minusMonths(8)

        when: "a month with activity"
        List orgsForStaff = usageService.getOverallPhoneActivity(thisMonth, PhoneOwnershipType.INDIVIDUAL)
        List orgsForTeams = usageService.getOverallPhoneActivity(thisMonth, PhoneOwnershipType.GROUP)

        then:
        orgsForStaff.find { it.name == org1.name }
        orgsForStaff.find { it.name == org2.name }
        orgsForTeams.find { it.name == org1.name }
        orgsForTeams.find { it.name == org2.name }

        when: "a month with no activity"
        orgsForStaff = usageService.getOverallPhoneActivity(priorMonth, PhoneOwnershipType.INDIVIDUAL)
        orgsForTeams = usageService.getOverallPhoneActivity(priorMonth, PhoneOwnershipType.GROUP)

        then:
        orgsForStaff.isEmpty() == true
        orgsForTeams.isEmpty() == true

        expect:
        usageService.getOverallPhoneActivity(null, null) == []
    }

    void "test getting activity for a single month for all staff phones at a particular org"() {
        given:
        DateTime thisMonth = DateTime.now()
        DateTime priorMonth = DateTime.now().minusMonths(8)

        when: "nonexistent org"
        List staffActivity = usageService.getStaffPhoneActivity(thisMonth, -88L)

        then:
        staffActivity.isEmpty() == true

        when: "a month with activity"
        staffActivity = usageService.getStaffPhoneActivity(thisMonth, org1.id)

        then:
        staffActivity.size() > 0

        when: "a month with no activity"
        staffActivity = usageService.getStaffPhoneActivity(priorMonth, org1.id)

        then:
        staffActivity.isEmpty() == true

        expect:
        usageService.getStaffPhoneActivity(null, null) == []
    }

    void "test getting activity for a single month for all team phones at a particular org"() {
        given:
        DateTime thisMonth = DateTime.now()
        DateTime priorMonth = DateTime.now().minusMonths(8)

        when: "nonexistent org"
        List teamActivity = usageService.getTeamPhoneActivity(thisMonth, -88L)

        then:
        teamActivity.isEmpty() == true

        when: "a month with activity"
        teamActivity = usageService.getTeamPhoneActivity(thisMonth, org1.id)

        then:
        teamActivity.size() > 0

        when: "a month with no activity"
        teamActivity = usageService.getTeamPhoneActivity(priorMonth, org1.id)

        then:
        teamActivity.isEmpty() == true

        expect:
        usageService.getTeamPhoneActivity(null, null) == []
    }

    void "test getting activity over all months with data overall"() {
        given:
        DateTime dt = DateTime.now()
        int numMonthsInPast = 8

        when: "only data this month"
        List aList = usageService.getActivity(PhoneOwnershipType.INDIVIDUAL)

        then:
        aList.size() == 1
        aList[0].monthString == JodaUtils.DISPLAYED_MONTH_FORMAT.print(dt)

        when: "has prior data too"
        RecordCall rCall1 = new RecordCall(record: ipr1.record, whenCreated: dt.minusMonths(numMonthsInPast))
        rCall1.addReceipt(TestUtils.buildTempReceipt())
        rCall1.save(flush: true, failOnError: true)

        aList = usageService.getActivity(PhoneOwnershipType.INDIVIDUAL)

        then:
        aList.size() == numMonthsInPast + 1
    }

    void "test getting activity over all months with data for a particular org"() {
        given:
        DateTime dt = DateTime.now()
        int numMonthsInPast = 8

        when: "only data this month"
        List aList1 = usageService.getActivityForOrg(PhoneOwnershipType.INDIVIDUAL, org1.id)
        List aList2 = usageService.getActivityForOrg(PhoneOwnershipType.INDIVIDUAL, org2.id)

        then:
        aList1.size() == 1
        aList1[0].monthString == JodaUtils.DISPLAYED_MONTH_FORMAT.print(dt)
        aList2.size() == 1
        aList2[0].monthString == JodaUtils.DISPLAYED_MONTH_FORMAT.print(dt)

        when: "has prior data too"
        RecordCall rCall1 = new RecordCall(record: ipr1.record, whenCreated: dt.minusMonths(numMonthsInPast))
        rCall1.addReceipt(TestUtils.buildTempReceipt())
        rCall1.save(flush: true, failOnError: true)

        aList1 = usageService.getActivityForOrg(PhoneOwnershipType.INDIVIDUAL, org1.id)
        aList2 = usageService.getActivityForOrg(PhoneOwnershipType.INDIVIDUAL, org2.id)

        then:
        aList1.size() == numMonthsInPast + 1
        aList1[0].numCalls == 1
        aList2.size() == numMonthsInPast + 1
        aList2[0].numCalls == 0
    }

    void "test getting activity over all months with data for a particular TextUp phone number"() {
        given:
        DateTime dt = DateTime.now()
        int numMonthsInPast = 8

        when: "only data this month"
        List aList1 = usageService.getActivityForPhoneId(p1.id)
        List aList2 = usageService.getActivityForPhoneId(p2.id)

        then:
        aList1.size() == 1
        aList1[0].monthString == JodaUtils.DISPLAYED_MONTH_FORMAT.print(dt)
        aList2.size() == 1
        aList2[0].monthString == JodaUtils.DISPLAYED_MONTH_FORMAT.print(dt)

        when: "has prior data too"
        RecordCall rCall1 = new RecordCall(record: ipr1.record, whenCreated: dt.minusMonths(numMonthsInPast))
        rCall1.addReceipt(TestUtils.buildTempReceipt())
        rCall1.save(flush: true, failOnError: true)

        aList1 = usageService.getActivityForPhoneId(p1.id)
        aList2 = usageService.getActivityForPhoneId(p2.id)

        then:
        aList1.size() == numMonthsInPast + 1
        aList1[0].numCalls == 1
        aList2.size() == numMonthsInPast + 1
        aList2[0].numCalls == 0
    }

    void "test summing usage + calculating costs"() {
        given:
        int numNotified = 88
        int numVoicemailSeconds = 23

        Phone thisPhone = TestUtils.buildActiveStaffPhone(s1)
        IndividualPhoneRecord thisIpr = TestUtils.buildIndPhoneRecord(thisPhone)
        RecordText thisText1 = new RecordText(record: thisIpr.record, outgoing: true, contents: "outgoing")
        RecordText thisText2 = new RecordText(record: thisIpr.record, outgoing: false, contents: "incoming", numNotified: numNotified)
        RecordCall thisCall1 = new RecordCall(record: thisIpr.record, outgoing: true)
        RecordCall thisCall2 = new RecordCall(record: thisIpr.record, outgoing: false, voicemailInSeconds: numVoicemailSeconds)
        [thisText1, thisText2, thisCall1, thisCall2].each { RecordItem rItem ->
            rItem.addReceipt(TestUtils.buildTempReceipt())
            rItem.save(flush: true, failOnError: true)
        }

        when:
        List aList = usageService.getActivityForPhoneId(thisPhone.id)

        then:
        aList.size() == 1
        aList.every { it instanceof ActivityRecord }
        aList[0].numNotificationTexts == numNotified
        aList[0].numOutgoingTexts == 1
        aList[0].numOutgoingSegments == thisText1.receipts[0].numBillable
        aList[0].numIncomingTexts == 1
        aList[0].numIncomingSegments == thisText2.receipts[0].numBillable
        decimalFormat.format(aList[0].numVoicemailMinutes)  == decimalFormat.format(numVoicemailSeconds / 60)
        aList[0].numBillableVoicemailMinutes == Math.ceil(numVoicemailSeconds / 60)
        aList[0].numOutgoingCalls == 1
        decimalFormat.format(aList[0].numOutgoingMinutes) == decimalFormat.format(thisCall1.receipts[0].numBillable / 60)
        aList[0].numOutgoingBillableMinutes == Math.ceil(thisCall1.receipts[0].numBillable / 60)
        aList[0].numIncomingCalls == 1
        decimalFormat.format(aList[0].numIncomingMinutes) == decimalFormat.format(thisCall2.receipts[0].numBillable / 60)
        aList[0].numIncomingBillableMinutes == Math.ceil(thisCall2.receipts[0].numBillable / 60)
    }
}
