import com.twilio.Twilio
import grails.util.GrailsUtil
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.textup.*
import org.textup.rest.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

class BootStrap {

	def marshallerInitializerService
	def grailsApplication

    def init = { servletContext ->
    	// marshaller and renderer customization from
    	// see http://groovyc.net/non-trivial-restful-apis-in-grails-part-1/
    	marshallerInitializerService.initialize()
    	// set appropriate security providers so we can call HTTPS urls (such as in MediaService)
    	// add BouncyCastle as the preferred security provider and remove SunEC because SunEC
    	// is not present in the OpenJDKs in our Ubuntu-based production environments
    	Security.insertProviderAt(new BouncyCastleProvider(), 1)
    	Security.removeProvider("SunEC")
    	// builder pattern in Twilio needs to be initialized
    	def twilioConfig = grailsApplication.config.textup.apiKeys.twilio
    	Twilio.init(twilioConfig.sid, twilioConfig.authToken)

    	if (GrailsUtil.environment in ["development", "production"] && Organization.count() == 0) {
            Role adminRole = Roles.tryGetAdminRole()
                .logFail("bootstrap: creating role 1")
                .payload
            Role userRole = Roles.tryGetUserRole()
                .logFail("bootstrap: creating role 2")
                .payload

            Staff.withSession { it.flush() }

            Organization org1 = Organization.tryCreate("Shelter Rhode Island", TestUtils.buildLocation())
                .logFail("bootstrap: creating organization 1")
                .payload
            org1.status = OrgStatus.PENDING
            Organization org2 = Organization.tryCreate("Rhode Island House", TestUtils.buildLocation())
                .logFail("bootstrap: creating organization 2")
                .payload
            org2.status = OrgStatus.APPROVED

            Staff.withSession { it.flush() }

            Staff s0 = Staff.tryCreate(userRole, org1, "Mallory Pending1", "demo-pending", "password", "connect@textup.org")
                .logFail("bootstrap: creating staff 1")
                .payload
            s0.status = StaffStatus.ADMIN
            Staff s1 = Staff.tryCreate(adminRole, org2, "Super", "super", "password", "connect@textup.org")
                .logFail("bootstrap: creating staff 2")
                .payload
            s1.status = StaffStatus.ADMIN
            Staff s2 = Staff.tryCreate(userRole, org2, "Eric Bai", "demo-eric", "password", "eric@textup.org")
                .logFail("bootstrap: creating staff 3")
                .payload
            s2.status = StaffStatus.ADMIN
            s2.personalNumberAsString = TestUtils.randPhoneNumberString()
            Staff s3 = Staff.tryCreate(userRole, org2, "Michelle Petersen", "demo-michelle", "password", "michelle@textup.org")
                .logFail("bootstrap: creating staff 4")
                .payload
            s3.status = StaffStatus.ADMIN
            s3.personalNumberAsString = TestUtils.randPhoneNumberString()
            Staff s4 = Staff.tryCreate(userRole, org2, "Johnny Staff3", "demo-johnny", "password", "connect@textup.org")
                .logFail("bootstrap: creating staff 5")
                .payload
            s4.status = StaffStatus.PENDING
            s4.personalNumberAsString = TestUtils.randPhoneNumberString()

            Staff.withSession { it.flush() }

            Team t1 = Team.tryCreate(org2, "Rapid Rehousing", TestUtils.buildLocation())
                .logFail("bootstrap: creating team 1")
                .payload
            t1.addToMembers(s2)
            t1.addToMembers(s3)
            Team t2 = Team.tryCreate(org2, "Housing First", TestUtils.buildLocation())
                .logFail("bootstrap: creating team 2")
                .payload
            t2.addToMembers(s1)
            t2.addToMembers(s2)

            Staff.withSession { it.flush() }

            Phone p1 = Phone.tryCreate(s1.id, PhoneOwnershipType.INDIVIDUAL)
                .logFail("bootstrap: creating phone 1")
                .payload
            p1.tryActivate(TestUtils.randPhoneNumber(), TestUtils.randString())
                .logFail("bootstrap: activating phone 1")
            Phone p2 = Phone.tryCreate(s2.id, PhoneOwnershipType.INDIVIDUAL)
                .logFail("bootstrap: creating phone 2")
                .payload
            p2.tryActivate(TestUtils.randPhoneNumber(), TestUtils.randString())
                .logFail("bootstrap: activating phone 2")
            Phone p3 = Phone.tryCreate(s3.id, PhoneOwnershipType.INDIVIDUAL)
                .logFail("bootstrap: creating phone 3")
                .payload
            p3.tryActivate(TestUtils.randPhoneNumber(), TestUtils.randString())
                .logFail("bootstrap: activating phone 3")
            Phone tp1 = Phone.tryCreate(t1.id, PhoneOwnershipType.GROUP)
                .logFail("bootstrap: creating phone 4")
                .payload
            tp1.tryActivate(TestUtils.randPhoneNumber(), TestUtils.randString())
                .logFail("bootstrap: activating phone 4")

            Staff.withSession { it.flush() }

            OwnerPolicy own1 = OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(p1.owner, s1.id)
                .logFail("bootstrap: creating owner policy 1")
                .payload
            own1.schedule.updateWithIntervalStrings(wednesday: ["0100:2300"])
            OwnerPolicy own2 = OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(p2.owner, s2.id)
                .logFail("bootstrap: creating owner policy 2")
                .payload
            own2.schedule.updateWithIntervalStrings(friday: ["0100:2300"])
            OwnerPolicy own3 = OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(p3.owner, s3.id)
                .logFail("bootstrap: creating owner policy 3")
                .payload
            own3.schedule.updateWithIntervalStrings(wednesday: ["0100:2300"])

            Staff.withSession { it.flush() }

            IndividualPhoneRecord ipr1 = IndividualPhoneRecord.tryCreate(p1)
                .logFail("bootstrap: creating contact 1")
                .payload
            ipr1.name = "John Smith"
            ipr1.mergeNumber(TestUtils.randPhoneNumber(), 0)
            IndividualPhoneRecord ipr2 = IndividualPhoneRecord.tryCreate(p2)
                .logFail("bootstrap: creating contact 2")
                .payload
            ipr2.mergeNumber(TestUtils.randPhoneNumber(), 0)
            IndividualPhoneRecord ipr3 = IndividualPhoneRecord.tryCreate(tp1)
                .logFail("bootstrap: creating contact 3")
                .payload
            ipr3.mergeNumber(TestUtils.randPhoneNumber(), 0)
            ipr3.mergeNumber(TestUtils.randPhoneNumber(), 1)

            GroupPhoneRecord gpr1 = GroupPhoneRecord.tryCreate(p1, "New Clients")
                .logFail("bootstrap: creating tag 1")
                .payload
            gpr1.members.addToPhoneRecords(ipr1)
            GroupPhoneRecord gpr2 = GroupPhoneRecord.tryCreate(p2, "Monday Group")
                .logFail("bootstrap: creating tag 2")
                .payload
            GroupPhoneRecord gpr3 = GroupPhoneRecord.tryCreate(tp1, "Tuesday Group")
                .logFail("bootstrap: creating tag 3")
                .payload

            Staff.withSession { it.flush() }

            RecordText rText1 = ipr1.record.storeOutgoing(RecordItemType.TEXT, Author.create(s1), TestUtils.randString())
                .logFail("bootstrap: creating record item 1")
                .payload
            rText1.addToReceipts(TestUtils.buildReceipt())
            RecordText rText2 = ipr2.record.storeOutgoing(RecordItemType.TEXT, Author.create(s1), TestUtils.randString())
                .logFail("bootstrap: creating record item 2")
                .payload
            rText2.addToReceipts(TestUtils.buildReceipt())
            RecordText rText3 = ipr3.record.storeOutgoing(RecordItemType.TEXT, Author.create(s1), TestUtils.randString())
                .logFail("bootstrap: creating record item 3")
                .payload
            rText3.addToReceipts(TestUtils.buildReceipt())
            RecordText rText4 = gpr3.record.storeOutgoing(RecordItemType.TEXT, Author.create(s1), TestUtils.randString())
                .logFail("bootstrap: creating record item 4")
                .payload
            rText4.addToReceipts(TestUtils.buildReceipt())

            Staff.withSession { it.flush() }

            PhoneRecord pr1 = PhoneRecord.tryCreate(SharePermission.DELEGATE, ipr1, p2)
                .logFail("bootstrap: sharing contact 1")
                .payload
            PhoneRecord pr2 = PhoneRecord.tryCreate(SharePermission.DELEGATE, ipr2, p1)
                .logFail("bootstrap: sharing contact 2")
                .payload

            Staff.withSession { it.flush() }
    	}
    }
    def destroy = {
    }
}
