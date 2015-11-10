package org.textup.util

import org.textup.*
import org.springframework.context.MessageSource
import spock.lang.Shared
import spock.lang.Specification
import grails.plugin.springsecurity.SpringSecurityService

class CustomSpec extends Specification {

	@Shared
	int iterationCount = 1

    @Shared
    ConfigObject config = new ConfigSlurper().parse(new File("grails-app/conf/Config.groovy").toURL())

	Organization org, org2
	Team t1, t2, otherT1, otherT2
	Staff s1, s2, s3, otherS1, otherS2, otherS3
	StaffPhone p1, p2, p3, otherP1, otherP2, otherP3
	TeamPhone tPh1, tPh2, otherTPh1, otherTPh2
    Contact c1, c1_1, c1_2, c2, c2_1, tC1, tC2, otherC2, otherTC2
    RecordText rText1, rText2, rTeText1, rTeText2, otherRText2, otherRTeText2
    SharedContact sc1, sc2
    ContactTag tag1, tag1_1, tag2, teTag1, teTag2, otherTag2, otherTeTag2

	String loggedInUsername
    String loggedInPassword

    protected def getBean(String beanName) {
        grailsApplication.mainContext.getBean(beanName)
    }
    protected ResultFactory getResultFactory() {
        getBean("resultFactory")
    }

    protected void setupData() {
        setupData(iterationCount)
    }
    protected void setupData(int customIterationCount) {
        iterationCount = customIterationCount
        loggedInUsername = "loggedinstaff$iterationCount"
        loggedInPassword = "password"

    	overrideConstructors()
        organizations()
        teamsWithPhones()
        staffWithPhones()
        teamMemberships()
        contactsWithItems()
        shareContacts()
        tags()
        tagMemberships()
    }
    protected void cleanupData() { iterationCount++ }

    ////////////////////////
    // Setup data methods //
    ////////////////////////

    protected void overrideConstructors() {
        ResultFactory fac = grailsApplication.mainContext.getBean("resultFactory")
        fac.messageSource = [getMessage:{ String c, Object[] p, Locale l -> c }] as MessageSource

        Staff.metaClass.constructor = { Map m->
            def instance = new Staff()
            instance.properties = m
            instance.resultFactory = getResultFactory()
            instance
        }
        Contact.metaClass.constructor = { Map m->
            def instance = new Contact()
            instance.properties = m
            instance.resultFactory = getResultFactory()
            instance
        }
        Record.metaClass.constructor = { Map m->
            def instance = new Record()
            instance.properties = m
            instance.resultFactory = getResultFactory()
            instance
        }
        RecordText.metaClass.constructor = { Map m->
            def instance = new RecordText()
            instance.properties = m
            instance.resultFactory = getResultFactory()
            instance
        }
        WeeklySchedule.metaClass.constructor = { Map m->
            def instance = new WeeklySchedule()
            instance.properties = m
            instance.resultFactory = getResultFactory()
            instance
        }
    }

    protected void organizations() {
        //our org
        org = new Organization(name:"1organiz$iterationCount")
        org.location = new Location(address:"Testing Address", lat:0G, lon:0G)
        org.save(flush:true)
        //other org
        org2 = new Organization(name:"2organiz$iterationCount")
        org2.location = new Location(address:"Testing Address", lat:0G, lon:0G)
        org2.save(flush:true)
    }

    protected void teamsWithPhones() {
        //teams for our org
        t1 = new Team(name:"Team1", org:org)
        t2 = new Team(name:"Team2", org:org)
        t1.location = new Location(address:"Testing Address", lat:0G, lon:1G)
        t2.location = new Location(address:"Testing Address", lat:1G, lon:1G)
        t1.save(flush:true, failOnError:true)
        t2.save(flush:true, failOnError:true)
        //add team phones
        tPh1 = new TeamPhone()
        tPh1.resultFactory = getResultFactory()
        tPh1.numberAsString = "160333444${iterationCount}"
        t1.phone = tPh1
        tPh1.save(flush:true, failOnError:true)
        tPh2 = new TeamPhone()
        tPh2.resultFactory = getResultFactory()
        tPh2.numberAsString = "170333444${iterationCount}"
        t2.phone = tPh2
        tPh2.save(flush:true, failOnError:true)

        //teams for other org
        otherT1 = new Team(name:"Team1", org:org2)
        otherT2 = new Team(name:"Team2", org:org2)
        otherT1.location = new Location(address:"Testing Address", lat:0G, lon:1G)
        otherT2.location = new Location(address:"Testing Address", lat:1G, lon:1G)
        otherT1.save(flush:true, failOnError:true)
        otherT2.save(flush:true, failOnError:true)
        //add a team phone
        otherTPh1 = new TeamPhone()
        otherTPh1.resultFactory = getResultFactory()
        otherTPh1.numberAsString = "180333444${iterationCount}"
        otherT1.phone = otherTPh1
        otherTPh1.save(flush:true, failOnError:true)
        otherTPh2 = new TeamPhone()
        otherTPh2.resultFactory = getResultFactory()
        otherTPh2.numberAsString = "190333444${iterationCount}"
        otherT2.phone = otherTPh2
        otherTPh2.save(flush:true, failOnError:true)
    }

    protected void staffWithPhones() {
        //staff for our org
        s1 = new Staff(username:loggedInUsername, password:loggedInPassword,
            name:"Staff$iterationCount", email:"staff$iterationCount@textup.org", org:org)
        s2 = new Staff(username:"1sta$iterationCount", password:"password",
            name:"Staff$iterationCount", email:"staff$iterationCount@textup.org", org:org)
        s3 = new Staff(username:"2sta$iterationCount", password:"password",
            name:"Staff$iterationCount", email:"staff$iterationCount@textup.org", org:org)
        s1.personalPhoneNumberAsString = "111 222 3333"
        s2.personalPhoneNumberAsString = "111 222 3333"
        s3.personalPhoneNumberAsString = "111 222 3333"
        s1.save(flush:true, failOnError:true)
        s2.save(flush:true, failOnError:true)
        s3.save(flush:true, failOnError:true)
        //phone numbers for staff at our org
        p1 = new StaffPhone()
        p1.resultFactory = getResultFactory()
        p1.numberAsString = "100333444${iterationCount}"
        s1.phone = p1
        p1.save(flush:true, failOnError:true)
        p2 = new StaffPhone()
        p2.resultFactory = getResultFactory()
        p2.numberAsString = "111333444$iterationCount"
        s2.phone = p2
        p2.save(flush:true, failOnError:true)
        p3 = new StaffPhone()
        p3.resultFactory = getResultFactory()
        p3.numberAsString = "123333441$iterationCount"
        s3.phone = p3
        p3.save(flush:true, failOnError:true)

        //staff for other org
        otherS1 = new Staff(username:"3sta$iterationCount", password:"password",
            name:"Staff$iterationCount", email:"staff$iterationCount@textup.org", org:org2)
        otherS2 = new Staff(username:"4sta$iterationCount", password:"password",
            name:"Staff$iterationCount", email:"staff$iterationCount@textup.org", org:org2)
        otherS3 = new Staff(username:"5sta$iterationCount", password:"password",
            name:"Staff$iterationCount", email:"staff$iterationCount@textup.org", org:org2)
        otherS1.personalPhoneNumberAsString = "111 222 3333"
        otherS2.personalPhoneNumberAsString = "111 222 3333"
        otherS3.personalPhoneNumberAsString = "111 222 3333"
        otherS1.save(flush:true, failOnError:true)
        otherS2.save(flush:true, failOnError:true)
        otherS3.save(flush:true, failOnError:true)
        //phone numbers for staff at our org
        otherP1 = new StaffPhone()
        otherP1.resultFactory = getResultFactory()
        otherP1.numberAsString = "130333444${iterationCount}"
        otherS1.phone = otherP1
        otherP1.save(flush:true, failOnError:true)
        otherP2 = new StaffPhone()
        otherP2.resultFactory = getResultFactory()
        otherP2.numberAsString = "141333444$iterationCount"
        otherS2.phone = otherP2
        otherP2.save(flush:true, failOnError:true)
        otherP3 = new StaffPhone()
        otherP3.resultFactory = getResultFactory()
        otherP3.numberAsString = "153333441$iterationCount"
        otherS3.phone = otherP3
        otherP3.save(flush:true, failOnError:true)
    }

    protected void teamMemberships() {
        //add staff at our org to teams
        (new TeamMembership(staff:s1, team:t1)).save(flush:true, failOnError:true)
        (new TeamMembership(staff:s2, team:t1)).save(flush:true, failOnError:true)
        (new TeamMembership(staff:s2, team:t2)).save(flush:true, failOnError:true)
        (new TeamMembership(staff:s3, team:t2)).save(flush:true, failOnError:true)

        //add staff at other org to teams
        (new TeamMembership(staff:otherS1, team:otherT1))
            .save(flush:true, failOnError:true)
        (new TeamMembership(staff:otherS2, team:otherT1))
            .save(flush:true, failOnError:true)
        (new TeamMembership(staff:otherS2, team:otherT2))
            .save(flush:true, failOnError:true)
        (new TeamMembership(staff:otherS3, team:otherT2))
            .save(flush:true, failOnError:true)
    }

    protected void contactsWithItems() {
        //contacts
        c1 = p1.createContact([:], ["12223334444"]).payload
        c1_1 = p1.createContact([:], ["12223334445"]).payload
        c1_2 = p1.createContact([:], ["12223334446"]).payload
        c2 = p2.createContact([:], ["12223334444"]).payload
        c2_1 = p2.createContact([:], ["12223334445"]).payload
        tC1 = tPh1.createContact([:], ["12223334444"]).payload
        tC2 = tPh2.createContact([:], ["12223334444"]).payload

        otherC2 = otherP2.createContact([:], ["12223334444"]).payload
        otherTC2 = otherTPh2.createContact([:], ["12223334444"]).payload
        [c1, c1_1, c1_2, c2, c2_1, tC1, tC2, otherC2, otherTC2]*.save(flush:true, failOnError:true)

        //add record texts to all contacts
        rText1 = c1.record.addText([contents:"text"], null).payload
        rText2 = c2.record.addText([contents:"text"], null).payload
        rTeText1 = tC1.record.addText([contents:"text"], null).payload
        rTeText2 = tC2.record.addText([contents:"text"], null).payload

        otherRText2 = otherC2.record.addText([contents:"text"], null).payload
        otherRTeText2 = otherTC2.record.addText([contents:"text"], null).payload
        [rText1, rText2, rTeText1, rTeText2, otherRText2, otherRTeText2]*.save(flush:true, failOnError:true)
    }

    protected void shareContacts() {
        sc1 = p1.shareContact(c1, p2, Constants.SHARED_DELEGATE).payload
        sc2 = p2.shareContact(c2, p1, Constants.SHARED_DELEGATE).payload
        [sc1, sc2]*.save(flush:true, failOnError:true)
    }

    protected void tags() {
        tag1 = p1.createTag(name:"Tag1").payload
        tag1_1 = p1.createTag(name:"Tag2").payload
        tag2 = p2.createTag(name:"Tag1").payload
        teTag1 = tPh1.createTag(name:"Tag1").payload
        teTag2 = tPh2.createTag(name:"Tag1").payload

        otherTag2 = otherP2.createTag(name:"Tag1").payload
        otherTeTag2 = otherTPh2.createTag(name:"Tag1").payload
        [tag1, tag1_1, tag2, teTag1, teTag2, otherTag2, otherTeTag2]*.save(flush:true, failOnError:true)

        teTag1.record.addText([contents:"text"], null).payload.save(flush:true, failOnError:true)
        teTag2.record.addText([contents:"text"], null).payload.save(flush:true, failOnError:true)
    }

    protected void tagMemberships() {
        (new TagMembership(tag:tag1, contact:c1)).save(flush:true, failOnError:true)
        (new TagMembership(tag:tag1, contact:c1_1)).save(flush:true, failOnError:true)
        (new TagMembership(tag:tag1_1, contact:c1_2)).save(flush:true, failOnError:true)
    }
}
