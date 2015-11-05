package org.textup

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.joda.time.LocalTime
import org.springframework.context.MessageSource
import org.textup.util.CustomSpec
import spock.lang.Shared
import spock.lang.Specification
import static org.springframework.http.HttpStatus.*

@TestFor(StaffService)
@Domain([TagMembership, Contact, Phone, ContactTag, 
	ContactNumber, Record, RecordItem, RecordNote, RecordText, 
	RecordCall, RecordItemReceipt, PhoneNumber, SharedContact, 
	TeamMembership, StaffPhone, Staff, Team, Organization, 
	Schedule, Location, TeamPhone, WeeklySchedule, TeamContactTag])
@TestMixin(HibernateTestMixin)
class StaffServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }
    def setup() {
        super.setupData()
        service.resultFactory = getResultFactory()
    }
    def cleanup() { 
        super.cleanupData()
    }

    void "test create"() {
    	when: "we create a staff member with invalid fields"
        Map createInfo = [:]
        Result res = service.create(createInfo)

    	then: 
        res.success == false 
        res.type == Constants.RESULT_VALIDATION
        res.payload.errorCount == 6

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
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "staffService.create.orgNotFound"
        res.payload.status == NOT_FOUND

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
        res.type == Constants.RESULT_VALIDATION
        res.payload.errorCount == 2

    	when: "we create a staff member with an existing org"
        int sBaseline = Staff.count(),
            oBaseline = Organization.count(),
            lBaseline = Location.count()
        String username = "6sta$iterationCount", 
            personalPhoneNumber = "2223334444"
        createInfo = [
            username:username,
            password:"password",
            name:"Staff1",
            email:"staff@textup.org",
            personalPhoneNumber:personalPhoneNumber,
            org:[
                id:org.id
            ]
        ]
        res = service.create(createInfo)
        assert res.success 
        s1.save(flush:true, failOnError:true)

    	then: 
        Staff.count() == sBaseline + 1
        Organization.count() == oBaseline
        Location.count() == lBaseline 

        res.payload instanceof Staff
        res.payload.username == username
        res.payload.personalPhoneNumber.number == personalPhoneNumber

    	when: "we create a staff member with a new org"
        oBaseline = Organization.count()
        lBaseline = Location.count()
        sBaseline = Staff.count()
        String orgName = "I am a new org!!"
        username = "7sta$iterationCount"
        personalPhoneNumber = "2228884444"
        createInfo = [
            username:username,
            password:"password",
            name:"Staff2",
            email:"staff@textup.org",
            personalPhoneNumber:personalPhoneNumber,
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
        Staff.count() == sBaseline + 1
        Organization.count() == oBaseline + 1
        Location.count() == lBaseline + 1

        res.payload instanceof Staff
        res.payload.username == username
        res.payload.personalPhoneNumber.number == personalPhoneNumber
        Organization.findByName(orgName) != null
    }

    void "test create with validation errors"() {
        when: "we try to create a staff with invalid personal phone number"
        String personalPhoneNumber = "invalid123"
        Map createInfo = [
            username:"7sta$iterationCount",
            password:"password",
            name:"Staff3",
            email:"staff@textup.org",
            personalPhoneNumber:personalPhoneNumber,
            org:[id:org.id]
        ]
        Result res = service.create(createInfo)

        then:
        res.success == false 
        res.type == Constants.RESULT_VALIDATION
        res.payload.errorCount == 1
    }

    void "test update"() {
    	when: "we update a nonexistent staff member"
        Map updateInfo = [:]
        Result res = service.update(-88L, updateInfo)

    	then: 
        res.success == false 
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "staffService.update.notFound"
        res.payload.status == NOT_FOUND

    	when: "we try to update a status when we ARE NOT an admin"
        s1.status = Constants.STATUS_STAFF
        s1.save(flush:true, failOnError:true)
        service.authService = [isAdminAtSameOrgAs:{ Long sid -> false }]
        updateInfo = [status:Constants.STATUS_ADMIN]
        res = service.update(s1.id, updateInfo)

    	then:
        res.success == false 
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "staffService.update.statusNotAdmin"
        res.payload.status == FORBIDDEN

    	when: "we update status when we ARE an admin"
        s1.status = Constants.STATUS_ADMIN
        s1.save(flush:true, failOnError:true)
        service.authService = [isAdminAtSameOrgAs:{ Long sid -> true }]
        updateInfo = [status:Constants.STATUS_PENDING]
        res = service.update(s2.id, updateInfo)

    	then: 
        res.success == true 
        res.payload instanceof Staff
        res.payload.id == s2.id
        res.payload.status == Constants.STATUS_PENDING

    	when: "we add a staff phone"
        //create a contact without a staff phone
        Staff s4 = new Staff(username:"6sta$iterationCount", password:"password", 
            name:"Staff$iterationCount", email:"staff$iterationCount@textup.org", org:org)
        s4.personalPhoneNumberAsString = "111 222 3333"
        s4.save(flush:true, failOnError:true)

        int pBaseline = Phone.count()
        String num = "163333441$iterationCount"
        updateInfo = [phone:num]
        res = service.update(s4.id, updateInfo)
        assert res.success
        s4.save(flush:true, failOnError:true)

    	then:
        Phone.count() == pBaseline + 1
        res.payload instanceof Staff
        res.payload.id == s4.id
        res.payload.phone.number.number == num

        when: "we update the staff phone's number with an invalid number"
        num = "invalid123"
        updateInfo = [phone:num]
        res = service.update(s4.id, updateInfo)

        then:
        res.success == false 
        res.type == Constants.RESULT_VALIDATION
        res.payload.errorCount == 1

    	when: "we update the staff phone's number"
        pBaseline = Phone.count()
        num = "173333441$iterationCount"
        updateInfo = [phone:num]
        res = service.update(s4.id, updateInfo)
        assert res.success
        s4.save(flush:true, failOnError:true)

    	then: 
        Phone.count() == pBaseline
        res.payload instanceof Staff
        res.payload.id == s4.id
        res.payload.phone.number.number == num

    	when: "we update staff with invalid fields"
        updateInfo = [personalPhoneNumber:"invalid123"]
        res = service.update(s2.id, updateInfo)

    	then: 
        res.success == false 
        res.type == Constants.RESULT_VALIDATION
        res.payload.errorCount == 1

    	when: "we update staff with valid fields, including the schedule"
        String newName = "kiki", newEmail = "email@email.com",
            newPersonalPhoneNumber = "8889991111", 
            newPhoneNum = "183333441$iterationCount"
        updateInfo = [
            name:newName,
            email:newEmail, 
            personalPhoneNumber:newPersonalPhoneNumber,
            phone:newPhoneNum, 
            schedule:[
                monday:["0100:0230", "0330:0430"],
                thursday:["0230:0345", "0330:0430"]
            ]
        ]
        List<LocalInterval> mondayInts = [
            new LocalInterval(new LocalTime(1, 0), new LocalTime(2, 30)),
            new LocalInterval(new LocalTime(3, 30), new LocalTime(4, 30)),
        ], thursdayInts = [
            new LocalInterval(new LocalTime(2, 30), new LocalTime(4, 30)),
        ]
        res = service.update(s1.id, updateInfo)

    	then:
    	res.success == true 
        res.payload instanceof Staff
        res.payload.id == s1.id 
        res.payload.name == newName
        res.payload.email == newEmail
        res.payload.personalPhoneNumber.number == newPersonalPhoneNumber
        res.payload.phone.number.number == newPhoneNum
        res.payload.schedule.getAllAsLocalIntervals().each { String day, List<LocalInterval> ints ->
            if (day == "monday") {
                assert ints.every { it in mondayInts }
            }
            else if (day == "thursday") { 
                assert ints.every { it in thursdayInts }
            }
        }
    }
}
