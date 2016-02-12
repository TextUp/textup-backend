package org.textup.util

import grails.converters.JSON
import groovy.json.JsonSlurper
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.types.OrgStatus
import org.textup.types.SharePermission
import org.textup.types.StaffStatus
import spock.lang.Shared
import spock.lang.Specification

class CustomSpec extends Specification {

	@Shared
	int iterationCount = 1

    @Shared
    ConfigObject config = new ConfigSlurper()
    	.parse(new File("grails-app/conf/Config.groovy").toURL())

	Organization org, org2
	Team t1, t2, otherT1, otherT2
	Staff s1, s2, s3, otherS1, otherS2, otherS3
	Phone p1, p2, p3, otherP1, otherP2, otherP3,
		tPh1, tPh2, otherTPh1, otherTPh2
    Contact c1, c1_1, c1_2, c2, c2_1, tC1, tC2, otherC2, otherTC2
    RecordText rText1, rText2, rTeText1, rTeText2, otherRText2, otherRTeText2
    SharedContact sc1, sc2
    ContactTag tag1, tag1_1, tag2, teTag1, teTag2, otherTag2, otherTeTag2

	String loggedInUsername
    String loggedInPassword

    // Unit
    // ----


    void setupData() {
        setupData(iterationCount)
    }
    void setupData(int iterNum) {
        loggedInUsername = "loggedinstaff$iterNum"
        loggedInPassword = "password"

        overrideConstructors(iterNum)
        organizations(iterNum)
        teamsWithPhones(iterNum)
        staffWithPhones(iterNum)
        teamMemberships(iterNum)
        contactsWithItems(iterNum)
        shareContacts(iterNum)
        tags(iterNum)
        tagMemberships(iterNum)
    }
    void cleanupData() { iterationCount++ }

    // Integration
    // -----------

    void setupIntegrationData() {
        setupIntegrationData(iterationCount)
    }
    void setupIntegrationData(int iterNum) {
        loggedInUsername = "loggedinstaff$iterNum"
        loggedInPassword = "password"

        organizations(iterNum)
        teamsWithPhones(iterNum)
        staffWithPhones(iterNum)
        teamMemberships(iterNum)
        contactsWithItems(iterNum)
        shareContacts(iterNum)
        tags(iterNum)
        tagMemberships(iterNum)
    }
    void cleanupIntegrationData() { cleanupData() }

    // Helpers
    // -------

    protected Object jsonToObject(JSON data) {
        new JsonSlurper().parseText(data.toString())
    }

    protected def getBean(String beanName) {
        grailsApplication.mainContext.getBean(beanName)
    }
    protected ResultFactory getResultFactory() {
        getBean("resultFactory")
    }
    protected MessageSource mockMessageSource() {
        [getMessage:{ String c, Object[] p, Locale l ->
            c
        }] as MessageSource
    }

    // Setup data
    // ----------

    protected void overrideConstructors(int iterNum) {
        ResultFactory fac = grailsApplication.mainContext.getBean("resultFactory")
        fac.messageSource = mockMessageSource()
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
        Phone.metaClass.constructor = { Map m->
            def instance = new Phone()
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
        FeaturedAnnouncement.metaClass.constructor = { Map m->
            def instance = new FeaturedAnnouncement()
            instance.properties = m
            instance.resultFactory = getResultFactory()
            instance
        }
    }

    protected void organizations(int iterNum) {
        //our org
        org = new Organization(name:"1organiz$iterNum", status:OrgStatus.APPROVED)
        org.location = new Location(address:"Testing Address", lat:0G, lon:0G)
        org.save(flush:true)
        //other org
        org2 = new Organization(name:"2organiz$iterNum")
        org2.location = new Location(address:"Testing Address", lat:0G, lon:0G)
        org2.save(flush:true)
    }

    protected void teamsWithPhones(int iterNum) {
        //teams for our org
        t1 = new Team(name:"Team1", org:org)
        t2 = new Team(name:"Team2", org:org)
        t1.location = new Location(address:"Testing Address", lat:0G, lon:1G)
        t2.location = new Location(address:"Testing Address", lat:1G, lon:1G)
        t1.save(flush:true, failOnError:true)
        t2.save(flush:true, failOnError:true)
        //add team phones
        tPh1 = new Phone(numberAsString:"${iterNum}160333444".take(10))
        tPh1.updateOwner(t1)
        tPh1.save(flush:true, failOnError:true)
        tPh2 = new Phone(numberAsString:"${iterNum}170333444".take(10))
        tPh2.updateOwner(t2)
        tPh2.save(flush:true, failOnError:true)

        //teams for other org
        otherT1 = new Team(name:"Team1", org:org2)
        otherT2 = new Team(name:"Team2", org:org2)
        otherT1.location = new Location(address:"Testing Address", lat:0G, lon:1G)
        otherT2.location = new Location(address:"Testing Address", lat:1G, lon:1G)
        otherT1.save(flush:true, failOnError:true)
        otherT2.save(flush:true, failOnError:true)
        //add a team phone
        otherTPh1 = new Phone(numberAsString:"${iterNum}180333444".take(10))
        otherTPh1.updateOwner(otherT1)
        otherTPh1.save(flush:true, failOnError:true)
        otherTPh2 = new Phone(numberAsString:"${iterNum}190333444".take(10))
        otherTPh2.updateOwner(otherT2)
        otherTPh2.save(flush:true, failOnError:true)
    }

    protected void staffWithPhones(int iterNum) {
        //staff for our org
        s1 = new Staff(username:loggedInUsername, password:loggedInPassword,
            name:"Staff$iterNum", email:"staff$iterNum@textup.org",
            org:org, personalPhoneAsString:"1112223333", status:StaffStatus.ADMIN)
        s2 = new Staff(username:"1sta$iterNum", password:"password",
            name:"Staff$iterNum", email:"staff$iterNum@textup.org",
            org:org, personalPhoneAsString:"1112223333")
        s3 = new Staff(username:"2sta$iterNum", password:"password",
            name:"Staff$iterNum", email:"staff$iterNum@textup.org",
            org:org, personalPhoneAsString:"1112223333")
        s1.save(flush:true, failOnError:true)
        s2.save(flush:true, failOnError:true)
        s3.save(flush:true, failOnError:true)

        //phone numbers for staff at our org
        p1 = new Phone(numberAsString:"${iterNum}100333444".take(10))
        p1.updateOwner(s1)
        p1.save(flush:true, failOnError:true)
        p2 = new Phone(numberAsString:"${iterNum}111333444".take(10))
        p2.updateOwner(s2)
        p2.save(flush:true, failOnError:true)
        p3 = new Phone(numberAsString:"${iterNum}123333441".take(10))
        p3.updateOwner(s3)
        p3.save(flush:true, failOnError:true)

        //staff for other org
        otherS1 = new Staff(username:"3sta$iterNum", password:"password",
            name:"Staff$iterNum", email:"staff$iterNum@textup.org",
            org:org2, personalPhoneAsString:"1112223333")
        otherS2 = new Staff(username:"4sta$iterNum", password:"password",
            name:"Staff$iterNum", email:"staff$iterNum@textup.org",
            org:org2, personalPhoneAsString:"1112223333")
        otherS3 = new Staff(username:"5sta$iterNum", password:"password",
            name:"Staff$iterNum", email:"staff$iterNum@textup.org",
            org:org2, personalPhoneAsString:"1112223333")
        otherS1.save(flush:true, failOnError:true)
        otherS2.save(flush:true, failOnError:true)
        otherS3.save(flush:true, failOnError:true)
        //phone numbers for staff at our org
        otherP1 = new Phone(numberAsString:"${iterNum}130333444".take(10))
        otherP1.updateOwner(otherS1)
        otherP1.save(flush:true, failOnError:true)
        otherP2 = new Phone(numberAsString:"${iterNum}141333444".take(10))
        otherP2.updateOwner(otherS2)
        otherP2.save(flush:true, failOnError:true)
        otherP3 = new Phone(numberAsString:"${iterNum}153333441".take(10))
        otherP3.updateOwner(otherS3)
        otherP3.save(flush:true, failOnError:true)
        // staff roles
        Role role = Role.findOrCreateByAuthority("ROLE_USER")
        role.save(flush:true, failOnError:true)
        [s1, s2, s3, otherS1, otherS2, otherS3].each {
            StaffRole.create(s1, role, true)
        }
    }

    protected void teamMemberships(int iterNum) {
        t1.addToMembers(s1)
        t1.addToMembers(s2)
        t2.addToMembers(s2)
        t2.addToMembers(s3)
        otherT1.addToMembers(otherS1)
        otherT1.addToMembers(otherS2)
        otherT2.addToMembers(otherS2)
        otherT2.addToMembers(otherS3)
        [t1, t2, otherT1, otherT2]*.save(flush:true, failOnError:true)
    }

    protected void contactsWithItems(int iterNum) {
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

        [c1, c1_1, c1_2, c2, c2_1, tC1, tC2, otherC2, otherTC2]
        	*.save(flush:true, failOnError:true)

        //add record texts to all contacts
        rText1 = c1.record.addText([contents:"text"], null).payload
        rText2 = c2.record.addText([contents:"text"], null).payload
        rTeText1 = tC1.record.addText([contents:"text"], null).payload
        rTeText2 = tC2.record.addText([contents:"text"], null).payload

        otherRText2 = otherC2.record.addText([contents:"text"], null).payload
        otherRTeText2 = otherTC2.record.addText([contents:"text"], null).payload

        [rText1, rText2, rTeText1, rTeText2, otherRText2, otherRTeText2]
        	*.save(flush:true, failOnError:true)
    }

    protected void shareContacts(int iterNum) {
        sc1 = p1.share(c1, p2, SharePermission.DELEGATE).payload
        sc2 = p2.share(c2, p1, SharePermission.DELEGATE).payload
        [sc1, sc2]*.save(flush:true, failOnError:true)
    }

    protected void tags(int iterNum) {
        tag1 = p1.createTag(name:"Tag1").payload
        tag1_1 = p1.createTag(name:"Tag2").payload
        tag2 = p2.createTag(name:"Tag1").payload
        teTag1 = tPh1.createTag(name:"Tag1").payload
        teTag2 = tPh2.createTag(name:"Tag1").payload

        otherTag2 = otherP2.createTag(name:"Tag1").payload
        otherTeTag2 = otherTPh2.createTag(name:"Tag1").payload
        [tag1, tag1_1, tag2, teTag1, teTag2, otherTag2, otherTeTag2]
        	*.save(flush:true, failOnError:true)

        teTag1.record.addText([contents:"text"], null)
        	.payload.save(flush:true, failOnError:true)
        teTag2.record.addText([contents:"text"], null)
        	.payload.save(flush:true, failOnError:true)
    }

    protected void tagMemberships(int iterNum) {
    	tag1.addToMembers(c1)
		tag1.addToMembers(c1_1)
		tag1_1.addToMembers(c1_2)
		[tag1, tag1_1]*.save(flush:true, failOnError:true)
    }
}
