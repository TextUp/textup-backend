package org.textup

import grails.test.spock.IntegrationSpec
import grails.validation.ValidationErrors
import spock.lang.Shared

class StaffIntegrationSpec extends IntegrationSpec {

    @Shared 
    int iterationCount = 1
    
    def cleanup() { iterationCount++ }

    void "test deletion with numbers"() {
    	given:
        Organization org1 = new Organization(name:"11Org$iterationCount")
        org1.location = new Location(address:"Testing Address", lat:0G, lon:0G)
        org1.save(flush:true)
        Team o1T1 = new Team(name:"Team1", org:org1)
        Team o1T2 = new Team(name:"Team2", org:org1)
        o1T1.location = new Location(address:"Testing Address", lat:0G, lon:1G)
        o1T2.location = new Location(address:"Testing Address", lat:1G, lon:1G)
        o1T1.save(flush:true, failOnError:true)
        o1T2.save(flush:true, failOnError:true)

        Staff s1 = new Staff(username:"integStaff1", password:"password", 
            name:"Staff", email:"staff@textup.org", org:org1)
        s1.personalPhoneNumberAsString = "111 222 3333"
        s1.save(flush:true, failOnError:true)
        Staff s2 = new Staff(username:"integStaff2", password:"password", 
            name:"Staff", email:"staff@textup.org", org:org1)
        s2.personalPhoneNumberAsString = "111 222 3333"
        s2.save(flush:true, failOnError:true)

        StaffPhone p1 = new StaffPhone()
        p1.numberAsString = "1008334445"
        s1.phone = p1
        p1.save(flush:true, failOnError:true)
        StaffPhone p2 = new StaffPhone()
        p2.numberAsString = "1008334446"
        s2.phone = p2
        p2.save(flush:true, failOnError:true)

        Contact c0 = p2.createContact().payload
        Contact c1 = p1.createContact().payload
        Contact c2 = p1.createContact().payload 
        ContactTag tag1 = p1.createTag(name:"Tag 1").payload
        ContactTag tag2 = p1.createTag(name:"Tag 2").payload
        [c0, c1, c2, tag1, tag2, p1, p2]*.save(flush:true, failOnError:true)

        (new TagMembership(contact:c1, tag:tag1)).save(flush:true, failOnError:true)
        (new TagMembership(contact:c2, tag:tag1)).save(flush:true, failOnError:true)
        (new TagMembership(contact:c2, tag:tag2)).save(flush:true, failOnError:true)

        //Must come before share contact or else will have differentTeams error
        (new TeamMembership(staff:s1, team:o1T1)).save(flush:true, failOnError:true)
        (new TeamMembership(staff:s1, team:o1T2)).save(flush:true, failOnError:true)
        (new TeamMembership(staff:s2, team:o1T1)).save(flush:true, failOnError:true)

        Result shareRes1 = p1.shareContact(c1, p2, Constants.SHARED_DELEGATE)
        Result shareRes2 = p2.shareContact(c0, p1, Constants.SHARED_DELEGATE)
        assert shareRes1.success && shareRes2.success
        SharedContact sc1 = shareRes1.payload
        SharedContact sc2 = shareRes2.payload
        [sc1, sc2]*.save(flush:true, failOnError:true)
    	
        (c1.mergeNumber("111 222 3333").payload).save(flush:true, failOnError:true)
        (c1.mergeNumber("111 222 3334").payload).save(flush:true, failOnError:true)
        (c2.mergeNumber("111 222 8888").payload).save(flush:true, failOnError:true)

    	when: "we delete"
    	int ctBaseline = ContactTag.count(), cBaseline = Contact.count(), 
    		tamBaseline = TagMembership.count(), pBaseline = Phone.count(), 
    		rBaseline = Record.count(), nBaseline = ContactNumber.count(),
    		scBaseline = SharedContact.count(), temBaseline = TeamMembership.count(),
    		schedBaseline = Schedule.count()
    	s1.delete(flush:true)

    	then: 
    	ContactTag.count() == ctBaseline - 2
        Contact.count() == cBaseline - 2
        TagMembership.count() == tamBaseline - 3
        Phone.count() == pBaseline - 1
        Record.count() == rBaseline - 2
        TeamMembership.count() == temBaseline - 2
        Schedule.count() == schedBaseline - 1
        SharedContact.count() == scBaseline - 2
		ContactNumber.count() == nBaseline - 3
    }
}
