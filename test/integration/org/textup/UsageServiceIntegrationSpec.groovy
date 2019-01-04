package org.textup

import java.math.RoundingMode
import java.text.DecimalFormat
import org.joda.time.DateTime
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

// This needs to be an integration test because we need to run H2 with MySQL compatibility
// parameters for the raw SQL in this service to execute without errors

class UsageServiceIntegrationSpec extends CustomSpec {

    UsageService usageService
    DecimalFormat decimalFormat

    def setup() {
        setupIntegrationData()

        decimalFormat = new DecimalFormat("#.##");
        decimalFormat.setRoundingMode(RoundingMode.CEILING);
    }

    def cleanup() {
        cleanupIntegrationData()
    }

    void "test getting activity for a single month for all orgs"() {
        given:
        DateTime thisMonth = DateTime.now()
        DateTime priorMonth = DateTime.now().minusMonths(8)

        when: "a month with activity"
        List<UsageService.Organization> orgsForStaff = usageService
            .getOverallPhoneActivity(thisMonth, PhoneOwnershipType.INDIVIDUAL)
        List<UsageService.Organization> orgsForTeams = usageService
            .getOverallPhoneActivity(thisMonth, PhoneOwnershipType.GROUP)

        then:
        orgsForStaff.find { it.name == org.name }
        orgsForStaff.find { it.name == org2.name }
        orgsForTeams.find { it.name == org.name }
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
        List<UsageService.Staff> staffActivity = usageService.getStaffPhoneActivity(thisMonth, -88L)

        then:
        staffActivity.isEmpty() == true

        when: "a month with activity"
        staffActivity = usageService.getStaffPhoneActivity(thisMonth, org.id)

        then:
        staffActivity.size() > 0
        staffActivity.size() >= org.countPeople()

        when: "a month with no activity"
        staffActivity = usageService.getStaffPhoneActivity(priorMonth, org.id)

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
        List<UsageService.Staff> teamActivity = usageService.getTeamPhoneActivity(thisMonth, -88L)

        then:
        teamActivity.isEmpty() == true

        when: "a month with activity"
        teamActivity = usageService.getTeamPhoneActivity(thisMonth, org.id)

        then:
        teamActivity.size() > 0
        teamActivity.size() >= org.countTeams()

        when: "a month with no activity"
        teamActivity = usageService.getTeamPhoneActivity(priorMonth, org.id)

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
        List<UsageService.ActivityRecord> aList = usageService.getActivity(PhoneOwnershipType.INDIVIDUAL)

        then:
        aList.size() == 1
        aList[0].monthString == DateTimeUtils.DISPLAYED_MONTH_FORMAT.print(dt)

        when: "has prior data too"
        RecordCall rCall1 = p1.contacts[0].tryGetRecord().payload.storeOutgoingCall().payload
        rCall1.whenCreated = dt.minusMonths(numMonthsInPast)
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
        List<UsageService.ActivityRecord> aList1 = usageService
            .getActivityForOrg(PhoneOwnershipType.INDIVIDUAL, org.id)
        List<UsageService.ActivityRecord> aList2 = usageService
            .getActivityForOrg(PhoneOwnershipType.INDIVIDUAL, org2.id)

        then:
        aList1.size() == 1
        aList1[0].monthString == DateTimeUtils.DISPLAYED_MONTH_FORMAT.print(dt)
        aList2.size() == 1
        aList2[0].monthString == DateTimeUtils.DISPLAYED_MONTH_FORMAT.print(dt)

        when: "has prior data too"
        assert s1.org == org
        RecordCall rCall1 = s1.phone.contacts[0].tryGetRecord().payload.storeOutgoingCall().payload
        rCall1.whenCreated = dt.minusMonths(numMonthsInPast)
        rCall1.addReceipt(TestUtils.buildTempReceipt())
        rCall1.save(flush: true, failOnError: true)

        aList1 = usageService.getActivityForOrg(PhoneOwnershipType.INDIVIDUAL, org.id)
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
        List<UsageService.ActivityRecord> aList1 = usageService
            .getActivityForNumber(p1.numberAsString)
        List<UsageService.ActivityRecord> aList2 = usageService
            .getActivityForNumber(otherP1.numberAsString)

        then:
        aList1.size() == 1
        aList1[0].monthString == DateTimeUtils.DISPLAYED_MONTH_FORMAT.print(dt)
        aList2.size() == 1
        aList2[0].monthString == DateTimeUtils.DISPLAYED_MONTH_FORMAT.print(dt)

        when: "has prior data too"
        Contact newContact1 = p1.createContact([:], [TestUtils.randPhoneNumberString()]).payload
        RecordCall rCall1 = newContact1.record.storeOutgoingCall().payload
        rCall1.whenCreated = dt.minusMonths(numMonthsInPast)
        rCall1.addReceipt(TestUtils.buildTempReceipt())
        rCall1.save(flush: true, failOnError: true)

        aList1 = usageService.getActivityForNumber(p1.numberAsString)
        aList2 = usageService.getActivityForNumber(otherP1.numberAsString)

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
        Phone phone1 = new Phone(numberAsString:TestUtils.randPhoneNumberString())
        phone1.updateOwner(s1)
        phone1.save(flush: true, failOnError: true)
        Contact c1 = phone1.createContact([:], [TestUtils.randPhoneNumberString()]).payload
        c1.save(flush: true, failOnError: true)

        RecordText rText1 = c1.record.storeOutgoingText("outgoing").payload
        RecordText rText2 = c1.record.storeOutgoingText("incoming").payload
        RecordCall rCall1 = c1.record.storeOutgoingCall().payload
        RecordCall rCall2 = c1.record.storeOutgoingCall().payload
        rText2.outgoing = false
        rText2.numNotified = numNotified
        rCall2.outgoing = false
        rCall2.voicemailInSeconds = numVoicemailSeconds
        [rText1, rText2, rCall1, rCall2].each { RecordItem rItem ->
            rItem.addReceipt(TestUtils.buildTempReceipt())
            rItem.save(flush: true, failOnError: true)
        }

        when:
        List<UsageService.ActivityRecord> aList = usageService
            .getActivityForNumber(phone1.numberAsString)

        then:
        aList.size() == 1
        aList[0].numNotificationTexts == numNotified
        aList[0].numOutgoingTexts == 1
        aList[0].numOutgoingSegments == rText1.receipts[0].numBillable
        aList[0].numIncomingTexts == 1
        aList[0].numIncomingSegments == rText2.receipts[0].numBillable
        decimalFormat.format(aList[0].numVoicemailMinutes)  == decimalFormat.format(numVoicemailSeconds / 60)
        aList[0].numBillableVoicemailMinutes == Math.ceil(numVoicemailSeconds / 60)
        aList[0].numOutgoingCalls == 1
        decimalFormat.format(aList[0].numOutgoingMinutes) == decimalFormat.format(rCall1.receipts[0].numBillable / 60)
        aList[0].numOutgoingBillableMinutes == Math.ceil(rCall1.receipts[0].numBillable / 60)
        aList[0].numIncomingCalls == 1
        decimalFormat.format(aList[0].numIncomingMinutes) == decimalFormat.format(rCall2.receipts[0].numBillable / 60)
        aList[0].numIncomingBillableMinutes == Math.ceil(rCall2.receipts[0].numBillable / 60)
    }
}
