package org.textup

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import java.util.UUID
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.joda.time.LocalTime
import org.textup.type.OrgStatus
import org.textup.type.StaffStatus
import org.textup.util.*
import org.textup.validator.LocalInterval
import org.textup.validator.PhoneNumber
import spock.lang.Shared
import spock.lang.Specification

@TestFor(StaffService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, StaffRole, Role, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class StaffServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        super.setupData()
        service.resultFactory = TestUtils.getResultFactory(grailsApplication)
        service.mailService = [
            notifyAboutPendingStaff: { Staff s1, List<Staff> admins ->
                new Result(status:ResultStatus.OK, payload:null)
            },
            notifyAboutPendingOrg: { Organization org1 ->
                new Result(status:ResultStatus.OK, payload:null)
            },
            notifyApproval: { Staff approvedStaff ->
                new Result(status:ResultStatus.OK, payload:null)
            },
            notifyRejection: { Staff rejectedStaff ->
                new Result(status:ResultStatus.OK, payload:null)
            },
            notifyInvitation: { Staff invitedBy, Staff s1, String password, String lockCode ->
                new Result(status:ResultStatus.OK, payload:null)
            }
        ] as MailService
        service.authService = {
            getIsActive: { true }
        } as AuthService
        service.phoneService = [
            mergePhone: { Staff s1, Map body, String timezone ->
                new Result(status:ResultStatus.OK, payload:s1)
            }
        ] as PhoneService
        WeeklySchedule.metaClass.updateWithIntervalStrings = { Map params, String timezone="UTC" ->
            service.resultFactory.success()
        }
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
        Result res = service.create(createInfo, null)

    	then:
        res.success == false
        res.errorMessages[0] == "staffService.create.mustSpecifyOrg"
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        WeeklySchedule.count() == schedBaseline
        Staff.count() == sBaseline
        Organization.count() == oBaseline
        Location.count() == lBaseline
        StaffRole.count() == rBaseline

    	when: "we associate staff member with a nonexistent organization"
        createInfo = [
            username:"6sta${iterNum}",
            password:"password",
            name:"Staff1",
            email:"staff@textup.org",
            lockCode: Constants.DEFAULT_LOCK_CODE,
            personalPhoneNumber:"12223334444",
            org:[
                id:-88L
            ]
        ]
        res = service.create(createInfo, null)

    	then:
        res.success == false
        res.errorMessages[0] == "staffService.create.orgNotFound"
        res.status == ResultStatus.NOT_FOUND

        WeeklySchedule.count() == schedBaseline
        Staff.count() == sBaseline
        Organization.count() == oBaseline
        Location.count() == lBaseline
        StaffRole.count() == rBaseline

    	when: "we create a staff member with invalid new organization"
        createInfo = [
            username:"6sta${iterNum}",
            password:"password",
            name:"Staff1",
            email:"staff@textup.org",
            personalPhoneNumber:"12223334444",
            lockCode: Constants.DEFAULT_LOCK_CODE,
            org:[
                name:"I am a new org",
                location:[
                    address:"address",
                    lat:-888G,
                    lon:888G
                ]
            ]
        ]
        res = service.create(createInfo, null)

    	then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 2
        WeeklySchedule.count() == schedBaseline
        Staff.count() == sBaseline
        Organization.count() == oBaseline
        Location.count() == lBaseline
        StaffRole.count() == rBaseline

    	when: "we create a staff member with an existing org"
        String username = UUID.randomUUID().toString(),
            personalPhoneAsString = "2223334444"
        createInfo = [
            username:username,
            password:"password",
            name:"Staff1",
            email:"staff@textup.org",
            personalPhoneNumber:personalPhoneAsString,
            lockCode: Constants.DEFAULT_LOCK_CODE,
            org:[
                id:org.id
            ]
        ]
        res = service.create(createInfo, null)
        assert res.success
        assert res.payload.instanceOf(Staff)
        res.payload.save(flush:true, failOnError:true)

    	then:
        WeeklySchedule.count() == schedBaseline + 1
        Staff.count() == sBaseline + 1
        Organization.count() == oBaseline
        Location.count() == lBaseline
        StaffRole.count() == rBaseline + 0 // adding in role a separate step

        res.status == ResultStatus.CREATED
        res.payload instanceof Staff
        res.payload.status == StaffStatus.PENDING
        res.payload.username == username
        res.payload.personalPhoneAsString == personalPhoneAsString

    	when: "we create a staff member with a new org"
        String orgName = "I am a new org!!"
        username = UUID.randomUUID().toString()
        personalPhoneAsString = "2228884444"
        createInfo = [
            username:username,
            password:"password",
            name:"Staff2",
            email:"staff@textup.org",
            lockCode: Constants.DEFAULT_LOCK_CODE,
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
        res = service.create(createInfo, null)
        assert res.success
        assert res.payload.instanceOf(Staff)
        res.payload.save(flush:true, failOnError:true)

    	then:
        WeeklySchedule.count() == schedBaseline + 2
        Staff.count() == sBaseline + 2
        Organization.count() == oBaseline + 1
        Location.count() == lBaseline + 1
        StaffRole.count() == rBaseline + 0 // adding in role a separate step

        res.status == ResultStatus.CREATED
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
        assert res.payload.instanceOf(Staff)
        res.payload.save(flush:true, failOnError:true)

        then:
        WeeklySchedule.count() == schedBaseline + 2
        Staff.count() == sBaseline + 2
        Organization.count() == oBaseline + 1
        Location.count() == lBaseline + 1
        StaffRole.count() == rBaseline + 1

        res.status == ResultStatus.OK
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
            username:"7sta${iterNum}",
            password:"password",
            name:"Staff3",
            email:"staff@textup.org",
            lockCode: Constants.DEFAULT_LOCK_CODE,
            personalPhoneNumber:personalPhoneNumber,
            org:[
                id:org.id
            ]
        ]
        Result res = service.create(createInfo, null)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1

        Staff.count() == sBaseline
        Organization.count() == oBaseline
        Location.count() == lBaseline
        StaffRole.count() == rBaseline
        WeeklySchedule.count() == schedBaseline
    }

    void "test create valid captcha validation"() {
        given: "not logged in so requires captcha validation"
        service.authService = {
            getIsActive: { false }
        } as AuthService

        when: "missing captcha response"
        Result res = service.verifyCreateRequest([captcha:null])

        then: "invalid"
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "staffService.create.couldNotVerifyCaptcha"

        when: "has all fields but some are incorrect"
        service.metaClass.doVerifyRequest = { String url -> [success:false] }
        res = service.verifyCreateRequest([captcha:"invalid!"])

        then: "invalid"
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "staffService.create.couldNotVerifyCaptcha"

        when: "all fields and all correct"
        service.metaClass.doVerifyRequest = { String url -> [success:true] }
        res = service.verifyCreateRequest([captcha:"valid!"])

        then: "valid"
        res.success == true
        res.status == ResultStatus.NO_CONTENT
        res.payload == null
    }

    // Update
    // ------

    void "test update"() {
        given:
        int pBaseline = Phone.count()
        String awayMsg = "calm down.",
            newName = "ting ting bai"

        when: "updating away message"
        String originalAwayMsg = s1.phone.awayMessage
        Result<Staff> res = service.update(s1.id, [
            phone:[awayMessage:awayMsg]
        ], null)
        assert res.success
        s1.save(flush:true, failOnError:true)

        then: "away message is updated in the phoneService"
        Phone.count() == pBaseline
        res.status == ResultStatus.OK
        res.payload instanceof Staff
        res.payload.id == s1.id
        res.payload.phone.awayMessage != awayMsg
        res.payload.phone.awayMessage == originalAwayMsg

        when: "update staff valid"
        res = service.update(s1.id, [name:newName], null)
        assert res.success
        s1.save(flush:true, failOnError:true)

        then:
        Phone.count() == pBaseline
        res.status == ResultStatus.OK
        res.payload instanceof Staff
        res.payload.id == s1.id
        res.payload.name == newName

        when: "update staff invalid"
        res = service.update(s1.id, [email:"invalid"], null)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
    }

    void "test find staff for id"() {
        when: "nonexistent id"
        Result<Staff> res = service.findStaffForId(-88L)

        then:
        res.success == false
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "staffService.update.notFound"

        when: "valid id"
        res = service.findStaffForId(s2.id)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Staff
        res.payload.id == s2.id
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
        String username = UUID.randomUUID().toString()
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
        Result res = service.update(s1.id, updateInfo, null)

        then:
        Staff.count() == sBaseline
        Organization.count() == oBaseline
        Location.count() == lBaseline
        StaffRole.count() == rBaseline
        WeeklySchedule.count() == schedBaseline
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1

        when: "update staff fields"
        String email = "ok123@ok.com"
        updateInfo.email = email
        res = service.update(s1.id, updateInfo, null)

        then:
        Staff.count() == sBaseline
        Organization.count() == oBaseline
        Location.count() == lBaseline
        StaffRole.count() == rBaseline
        WeeklySchedule.count() == schedBaseline
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Staff
        res.payload.name == name
        res.payload.username == username
        res.payload.password == pwd
        res.payload.email == email
        res.payload.personalPhoneAsString == personalPhoneAsString

        when: "remove personal phone number by passing in an empty string"
        res = service.update(s1.id, [personalPhoneNumber: ""], null)

        then:
        Staff.count() == sBaseline
        Organization.count() == oBaseline
        Location.count() == lBaseline
        StaffRole.count() == rBaseline
        WeeklySchedule.count() == schedBaseline
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Staff
        res.payload.personalPhoneAsString == ""

        when: "update schedule"
        updateInfo = [
            schedule:[
                monday:["0100:0230", "0330:0430"],
                thursday:["0230:0345", "0330:0430"]
            ]
        ]
        res = service.update(s1.id, updateInfo, null)
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
        res.status == ResultStatus.OK
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

    void "test update lock code"() {
        given: "a valid staff"
        assert s1.validate() == true
        String defaultLock = Constants.DEFAULT_LOCK_CODE

        when: "blank lock code"
        Map updateInfo = [lockCode:""]
        String originalLockCode = s1.lockCode
        Result<Staff> res = service.update(s1.id, updateInfo, null)

        then: "no change"
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Staff
        res.payload.id == s1.id
        res.payload.lockCode == originalLockCode

        when: "too long lock code"
        updateInfo.lockCode = "${defaultLock}${defaultLock}"
        res = service.update(s1.id, updateInfo, null)

        then: "invalid"
        res.success == false
        res.errorMessages.size() == 1
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when: "lock code non-numbers"
        updateInfo.lockCode = "ab12"
        res = service.update(s1.id, updateInfo, null)

        then: "invalid"
        res.success == false
        res.errorMessages.size() == 1
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when: "lock code only apropriate length with only numbers"
        updateInfo.lockCode = Constants.DEFAULT_LOCK_CODE
        res = service.update(s1.id, updateInfo, null)

        then: "valid"
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Staff
        res.payload.id == s1.id
        res.payload.lockCode == updateInfo.lockCode
    }

    void "test update status"() {
        when: "as staff"
        service.authService = [isAdminAtSameOrgAs:{ Long sId ->
            false
        }] as AuthService
        Map updateInfo = [status:"adMiN"]
        Result<Staff> res = service.update(s1.id, updateInfo, null)

        then: "silently ignore"
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Staff
        res.payload.id == s1.id
        res.payload.status == s1.status

        when: "as admin"
        service.authService = [isAdminAtSameOrgAs:{ Long sId ->
            true
        }] as AuthService
        updateInfo = [status:"penDiNG"]
        res = service.update(s2.id, updateInfo, null)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Staff
        res.payload.id == s2.id
        res.payload.status == StaffStatus.PENDING

        when: "as admin with invalid status"
        updateInfo = [status:"invalid"]
        res = service.update(s2.id, updateInfo, null)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
    }
}
