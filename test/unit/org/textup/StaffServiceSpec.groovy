package org.textup

import com.twilio.sdk.resource.instance.IncomingPhoneNumber
import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.joda.time.LocalTime
import org.springframework.context.MessageSource
import org.textup.types.OrgStatus
import org.textup.types.ResultType
import org.textup.types.StaffStatus
import org.textup.util.CustomSpec
import org.textup.validator.LocalInterval
import org.textup.validator.PhoneNumber
import spock.lang.Shared
import spock.lang.Specification
import static org.springframework.http.HttpStatus.*

@TestFor(StaffService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, StaffRole, Role])
@TestMixin(HibernateTestMixin)
class StaffServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    private String _methodJustCalled

    def setup() {
        super.setupData()
        service.resultFactory = getResultFactory()
        service.mailService = [
            notifyAdminsOfPendingStaff: { String pendingName, List<Staff> admins ->
                new Result(type:ResultType.SUCCESS, success:true, payload:null)
            },
            notifySuperOfNewOrganization: { String orgName ->
                new Result(type:ResultType.SUCCESS, success:true, payload:null)
            },
            notifyPendingOfApproval: { Staff approvedStaff ->
                new Result(type:ResultType.SUCCESS, success:true, payload:null)
            },
            notifyPendingOfRejection: { Staff rejectedStaff ->
                new Result(type:ResultType.SUCCESS, success:true, payload:null)
            }
        ] as MailService
        service.phoneService = [
            updatePhoneForNumber: { Phone p1, PhoneNumber pNum ->
                p1.number = pNum
                _methodJustCalled = "updatePhoneForNumber"
                new Result(type:ResultType.SUCCESS, success:true, payload:p1)
            },
            updatePhoneForApiId: { Phone p1, String apiId ->
                p1.numberAsString = "${iterationCount}123324901".take(10)
                _methodJustCalled = "updatePhoneForApiId"
                new Result(type:ResultType.SUCCESS, success:true, payload:p1)
            }
        ] as PhoneService
    }

    def cleanup() {
        super.cleanupData()
    }

    // Create
    // ------

    void "test create"() {
        given: "baselines"
        int schedBaseline = WeeklySchedule.count()
        int sBaseline = Staff.count()
        int oBaseline = Organization.count()
        int lBaseline = Location.count()
        int rBaseline = StaffRole.count()

    	when: "we create a staff member with invalid fields"
        Map createInfo = [:]
        Result res = service.create(createInfo)

    	then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "staffService.create.mustSpecifyOrg"
        res.payload.status == UNPROCESSABLE_ENTITY

        WeeklySchedule.count() == schedBaseline
        Staff.count() == sBaseline
        Organization.count() == oBaseline
        Location.count() == lBaseline
        StaffRole.count() == rBaseline

    	when: "we associate staff member with a nonexistent organization"
        createInfo = [
            username:"6sta$iterationCount",
            password:"password",
            name:"Staff1",
            email:"staff@textup.org",
            personalPhoneNumber:"12223334444",
            org:[
                id:-88L
            ]
        ]
        res = service.create(createInfo)

    	then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "staffService.create.orgNotFound"
        res.payload.status == NOT_FOUND

        WeeklySchedule.count() == schedBaseline
        Staff.count() == sBaseline
        Organization.count() == oBaseline
        Location.count() == lBaseline
        StaffRole.count() == rBaseline

    	when: "we create a staff member with invalid new organization"
        createInfo = [
            username:"6sta$iterationCount",
            password:"password",
            name:"Staff1",
            email:"staff@textup.org",
            personalPhoneNumber:"12223334444",
            org:[
                name:"I am a new org",
                location:[
                    address:"address",
                    lat:-888G,
                    lon:888G
                ]
            ]
        ]
        res = service.create(createInfo)

    	then:
        res.success == false
        res.type == ResultType.VALIDATION
        res.payload.errorCount == 2
        WeeklySchedule.count() == schedBaseline
        Staff.count() == sBaseline
        Organization.count() == oBaseline
        Location.count() == lBaseline
        StaffRole.count() == rBaseline

    	when: "we create a staff member with an existing org"
        String username = "6sta$iterationCount",
            personalPhoneAsString = "2223334444"
        createInfo = [
            username:username,
            password:"password",
            name:"Staff1",
            email:"staff@textup.org",
            personalPhoneNumber:personalPhoneAsString,
            org:[
                id:org.id
            ]
        ]
        res = service.create(createInfo)
        assert res.success
        s1.save(flush:true, failOnError:true)

    	then:
        WeeklySchedule.count() == schedBaseline + 1
        Staff.count() == sBaseline + 1
        Organization.count() == oBaseline
        Location.count() == lBaseline
        StaffRole.count() == rBaseline + 0 // adding in role a separate step

        res.payload instanceof Staff
        res.payload.status == StaffStatus.PENDING
        res.payload.username == username
        res.payload.personalPhoneAsString == personalPhoneAsString

    	when: "we create a staff member with a new org"
        String orgName = "I am a new org!!"
        username = "7sta$iterationCount"
        personalPhoneAsString = "2228884444"
        createInfo = [
            username:username,
            password:"password",
            name:"Staff2",
            email:"staff@textup.org",
            personalPhoneNumber:personalPhoneAsString,
            org:[
                name:orgName,
                location:[
                    address:"new org address",
                    lat:2G,
                    lon:-8G
                ]
            ]
        ]
        res = service.create(createInfo)
        assert res.success
        s1.save(flush:true, failOnError:true)

    	then:
        WeeklySchedule.count() == schedBaseline + 2
        Staff.count() == sBaseline + 2
        Organization.count() == oBaseline + 1
        Location.count() == lBaseline + 1
        StaffRole.count() == rBaseline + 0 // adding in role a separate step

        res.payload instanceof Staff
        res.payload.status == StaffStatus.ADMIN
        res.payload.org.status == OrgStatus.PENDING
        res.payload.username == username
        res.payload.personalPhoneAsString == personalPhoneAsString
        Organization.findByName(orgName) != null

        when: "add staff to new user"
        Staff newStaff = res.payload
        res = service.addRoleToStaff(newStaff.id)
        assert res.success
        s1.save(flush:true, failOnError:true)

        then:
        WeeklySchedule.count() == schedBaseline + 2
        Staff.count() == sBaseline + 2
        Organization.count() == oBaseline + 1
        Location.count() == lBaseline + 1
        StaffRole.count() == rBaseline + 1

        res.payload instanceof Staff
        res.payload.status == StaffStatus.ADMIN
        res.payload.org.status == OrgStatus.PENDING
        res.payload.username == username
        res.payload.personalPhoneAsString == personalPhoneAsString
        Organization.findByName(orgName) != null
    }

    void "test create with validation errors"() {
        given: "baselines"
        int schedBaseline = WeeklySchedule.count()
        int sBaseline = Staff.count()
        int oBaseline = Organization.count()
        int lBaseline = Location.count()
        int rBaseline = StaffRole.count()

        when: "we try to create a staff with invalid personal phone number"
        String personalPhoneNumber = "invalid123"
        Map createInfo = [
            username:"7sta$iterationCount",
            password:"password",
            name:"Staff3",
            email:"staff@textup.org",
            personalPhoneNumber:personalPhoneNumber,
            org:[
                id:org.id
            ]
        ]
        Result res = service.create(createInfo)

        then:
        res.success == false
        res.type == ResultType.VALIDATION
        res.payload.errorCount == 1

        Staff.count() == sBaseline
        Organization.count() == oBaseline
        Location.count() == lBaseline
        StaffRole.count() == rBaseline
        WeeklySchedule.count() == schedBaseline
    }

    // Update
    // ------

    void "test update"() {
        when: "update staff valid"
        int pBaseline = Phone.count()
        String awayMsg = "calm down."
        Result<Staff> res = service.update(s1.id, [awayMessage:awayMsg], null)
        assert res.success
        s1.save(flush:true, failOnError:true)

        then:
        Phone.count() == pBaseline
        res.payload instanceof Staff
        res.payload.id == s1.id
        res.payload.phone.awayMessage == awayMsg

        when: "update staff invalid"
        res = service.update(s1.id, [email:"invalid"], null)

        then:
        res.success == false
        res.type == ResultType.VALIDATION
        res.payload.errorCount == 1
    }

    void "test find staff for id"() {
        when: "nonexistent id"
        Result<Staff> res = service.findStaffForId(-88L)

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == NOT_FOUND
        res.payload.code == "staffService.update.notFound"

        when: "valid id"
        res = service.findStaffForId(s2.id)

        then:
        res.success == true
        res.payload instanceof Staff
        res.payload == s2
    }

    void "test update staff fields"() {
        given: "baselines"
        int schedBaseline = WeeklySchedule.count()
        int sBaseline = Staff.count()
        int oBaseline = Organization.count()
        int lBaseline = Location.count()
        int rBaseline = StaffRole.count()

        when: "invalid fields"
        String name = "hellobud"
        String username = "iamakitten"
        String pwd = "iloveunicorns"
        String invalidEmail = "whatismy"
        String personalPhoneAsString = "2223334444"
        Map updateInfo = [
            name: name,
            username:username,
            password:pwd,
            email:invalidEmail,
            personalPhoneNumber:personalPhoneAsString
        ]
        Result res = service.updateStaffInfo(s1, updateInfo, null)

        then:
        Staff.count() == sBaseline
        Organization.count() == oBaseline
        Location.count() == lBaseline
        StaffRole.count() == rBaseline
        WeeklySchedule.count() == schedBaseline
        res.success == false
        res.type == ResultType.VALIDATION
        res.payload.errorCount == 1

        when: "update staff fields"
        String email = "ok123@ok.com"
        updateInfo.email = email
        res = service.updateStaffInfo(s1, updateInfo, null)

        then:
        Staff.count() == sBaseline
        Organization.count() == oBaseline
        Location.count() == lBaseline
        StaffRole.count() == rBaseline
        WeeklySchedule.count() == schedBaseline
        res.success == true
        res.payload instanceof Staff
        res.payload.name == name
        res.payload.username == username
        res.payload.password == pwd
        res.payload.email == email
        res.payload.personalPhoneAsString == personalPhoneAsString

        when: "update away message"
        String awayMsg = "i am away right now. calm down."
        updateInfo = [awayMessage:awayMsg]
        res = service.updateStaffInfo(s1, updateInfo, null)

        then:
        Staff.count() == sBaseline
        Organization.count() == oBaseline
        Location.count() == lBaseline
        StaffRole.count() == rBaseline
        WeeklySchedule.count() == schedBaseline
        res.success == true
        res.payload instanceof Staff
        res.payload.phone.awayMessage == awayMsg

        when: "update schedule"
        updateInfo = [
            schedule:[
                monday:["0100:0230", "0330:0430"],
                thursday:["0230:0345", "0330:0430"]
            ]
        ]
        res = service.updateStaffInfo(s1, updateInfo, null)
        List<LocalInterval> mondayInts = [
            new LocalInterval(new LocalTime(1, 0), new LocalTime(2, 30)),
            new LocalInterval(new LocalTime(3, 30), new LocalTime(4, 30)),
        ], thursdayInts = [
            new LocalInterval(new LocalTime(2, 30), new LocalTime(4, 30)),
        ]

        then:
        Staff.count() == sBaseline
        Organization.count() == oBaseline
        Location.count() == lBaseline
        StaffRole.count() == rBaseline
        WeeklySchedule.count() == schedBaseline
        res.success == true
        res.payload instanceof Staff
        res.payload.schedule.getAllAsLocalIntervals().each { String day,
            List<LocalInterval> ints ->
            if (day == "monday") {
                assert ints.every { it in mondayInts }
            }
            else if (day == "thursday") {
                assert ints.every { it in thursdayInts }
            }
        }
    }

    void "test update status"() {
        when: "as staff"
        service.authService = [isAdminAtSameOrgAs:{ Long sId ->
            false
        }] as AuthService
        Map updateInfo = [status:"adMiN"]
        Result<Staff> res = service.updateStaffInfo(s1, updateInfo, null)

        then: "silently ignore"
        res.success == true
        res.payload instanceof Staff
        res.payload.id == s1.id
        res.payload.status == s1.status

        when: "as admin"
        service.authService = [isAdminAtSameOrgAs:{ Long sId ->
            true
        }] as AuthService
        updateInfo = [status:"penDiNG"]
        res = service.updateStaffInfo(s2, updateInfo, null)

        then:
        res.success == true
        res.payload instanceof Staff
        res.payload.id == s2.id
        res.payload.status == StaffStatus.PENDING

        when: "as admin with invalid status"
        updateInfo = [status:"invalid"]
        res = service.updateStaffInfo(s2, updateInfo, null)

        then:
        res.success == false
        res.type == ResultType.VALIDATION
        res.payload.errorCount == 1
    }

    void "test update phone number with phone number"() {
        given:
        int baseline = Phone.count()

        when: "with phone number"
        _methodJustCalled = null
        Result<Staff> res = service.updatePhoneNumber(s1, [phone:"1112223333"])

        then:
        res.success == true
        res.payload instanceof Staff
        _methodJustCalled == "updatePhoneForNumber"
        Phone.count() == baseline

        when: "with api id"
        _methodJustCalled = null
        res = service.updatePhoneNumber(s1, [phoneId:"hello!"])

        then:
        res.success == true
        res.payload instanceof Staff
        _methodJustCalled == "updatePhoneForApiId"
        Phone.count() == baseline
    }
    void "test create phone for staff"() {
        given: "staff with no phone"
        Staff staff = new Staff(username:"6sta$iterationCount", password:"password",
            name:"Staff$iterationCount", email:"staff$iterationCount@textup.org",
            org:org, personalPhoneAsString:"1112223333")
        staff.save(flush:true, failOnError:true)
        int pBaseline = Phone.count()
        int oBaseline = PhoneOwnership.count()

        when: "update with phone"
        String number = "163333441$iterationCount"
        Result<Staff> res = service.updatePhoneNumber(staff, [phone:number])
        assert res.success
        staff.save(flush:true, failOnError:true)

        then:
        Phone.count() == pBaseline + 1
        PhoneOwnership.count() == oBaseline + 1
        res.payload instanceof Staff
        res.payload.id == staff.id
    }
}
