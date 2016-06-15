import grails.util.GrailsUtil
import org.textup.*
import org.textup.rest.*
import org.textup.types.*
import org.textup.util.*
import org.textup.validator.*

class BootStrap {

	def marshallerInitializerService

    def init = { servletContext ->
    	//marshaller and renderer customization from
    	//http://groovyc.net/non-trivial-restful-apis-in-grails-part-1/
    	marshallerInitializerService.initialize()
    	if (GrailsUtil.environment == "development" ||
    		GrailsUtil.environment == "production") {
    		//Define security roles
	        Role adminRole = Role.findByAuthority("ROLE_ADMIN") ?:
	        	new Role(authority:"ROLE_ADMIN").save(flush:true)
	        Role userRole = Role.findByAuthority("ROLE_USER") ?:
	        	new Role(authority:"ROLE_USER").save(flush:true)

	    	if (Organization.count() == 0) {
	    		//create an unverified org
	    		Organization org1 = new Organization(name:"Shelter Rhode Island",
	    			status: OrgStatus.PENDING)
		    	org1.location = new Location(address:"30 Howard Ave, North Providence, \
		    		Rhode Island 02911, United States", lat:41.858923G, lon:-71.473439G)
		    	org1.save(flush:true)
		    	Staff pendingOrgStaff = new Staff(username:"demo-pending", password:"password",
		    		name:"Mallory Pending1", email:"connect@textup.org", org:org1,
		    		status:StaffStatus.ADMIN)
		    	pendingOrgStaff.save(flush:true, failOnError:true)
				StaffRole.create(pendingOrgStaff, userRole, true)

	    		//create our full-fledged demo org
	    		Organization org = new Organization(name:"Rhode Island House",
	    			status: OrgStatus.APPROVED)
		    	org.location = new Location(address:"577 Cranston St",
		    		lat:41.807982G, lon:-71.435045G)
		    	org.save(flush:true)

	    		//create the super user
	    		Staff superUser = new Staff(username:"super", password:"password",
		    		name:"Super", email:"connect@textup.org", org:org, status:StaffStatus.ADMIN)
	    		superUser.save(flush:true, failOnError:true)
	    		StaffRole.create(superUser, adminRole, true)

		    	//create teams
		    	Team t1 = new Team(name:"Rapid Rehousing", org:org)
				Team t2 = new Team(name:"Housing First", org:org)
				t1.location = new Location(address:"577 Cranston St", lat:0G, lon:1G)
				t2.location = new Location(address:"577 Cranston St", lat:1G, lon:1G)
				t1.save(flush:true, failOnError:true)
				t2.save(flush:true, failOnError:true)
				//create phones for teams
				Phone tPh1 = new Phone()
		        tPh1.numberAsString = "9252755153"
		        tPh1.updateOwner(t1)
		        tPh1.save(flush:true, failOnError:true)

				//create staff
				Staff admin = new Staff(username:"demo-eric", password:"password",
		    		name:"Eric Bai", email:"eric@textup.org", org:org,
		    		status:StaffStatus.ADMIN)
		    	Staff s1 = new Staff(username:"demo-michelle", password:"password",
		    		name:"Michelle Petersen", email:"michelle@textup.org", org:org,
		    		status:StaffStatus.ADMIN)
				Staff s2 = new Staff(username:"demo-staff2", password:"password",
					name:"Johnny Staff3", email:"connect@textup.org", org:org,
					status:StaffStatus.PENDING)
		    	admin.personalPhoneAsString = "6262027548"
		    	s1.personalPhoneAsString = "5865338761"
		    	s2.personalPhoneAsString = "2678887452"
		    	admin.save(flush:true, failOnError:true)
				s1.save(flush:true, failOnError:true)
				s2.save(flush:true, failOnError:true)
				//set availability
				admin.schedule.updateWithIntervalStrings(wednesday:["0100:2300"])
				s1.schedule.updateWithIntervalStrings(friday:["0100:2300"])
				s2.schedule.updateWithIntervalStrings(wednesday:["0100:2300"])
				s1.save(flush:true, failOnError:true)
				//create staff phones
		    	Phone p1 = new Phone(),
		    		p2 = new Phone(),
		    		p3 = new Phone()
		    	p1.numberAsString = "4012340315"
		    	p1.updateOwner(admin)
		    	p1.save(flush:true, failOnError:true)
		    	p2.numberAsString = "4012878248"
		    	p2.updateOwner(s1)
		    	p2.save(flush:true, failOnError:true)
		    	p3.numberAsString = "1112223335"
		    	p3.updateOwner(s2)
		    	p3.save(flush:true, failOnError:true)
		    	// create roles for staff
		    	StaffRole.create(admin, userRole, true)
		    	StaffRole.create(s1, userRole, true)
		    	StaffRole.create(s2, userRole, true)
		    	//add staff to teams
		    	t1.addToMembers(admin)
		    	t1.addToMembers(s1)
		    	t2.addToMembers(s1)
		    	t2.addToMembers(s2)
		    	t1.save(flush:true, failOnError:true)
				t2.save(flush:true, failOnError:true)

		    	//create contacts with items
		    	Contact c1 = p1.createContact([name:'John Smith'], ["2678887452"]).payload
				Contact c2 = p2.createContact([:], ["2678887452"]).payload
				Contact tC1 = tPh1.createContact([:], ["2678887452"]).payload
				[c1, c2, tC1]*.save(flush:true, failOnError:true)
				RecordText rText1 = c1.record
					.addText([contents:"Hi! Hope you're doing well today."], null).payload
		        RecordText rText2 = c2.record
		        	.addText([contents:"Hi! Hope you're doing well today."], null).payload
		        RecordText rTeText1 = tC1.record
		        	.addText([contents:"Hi! Hope you're doing well today."], null).payload
				[rText1, rText2, rTeText1]*.save(flush:true, failOnError:true)

				//share contacts
				SharedContact sc1 = p1.share(c1, p2, SharePermission.DELEGATE).payload
		        SharedContact sc2 = p2.share(c2, p1, SharePermission.DELEGATE).payload
		        [sc1, sc2]*.save(flush:true, failOnError:true)

		    	//create tags
		    	ContactTag tag1 = p1.createTag(name:"New Clients").payload
				ContactTag tag2 = p2.createTag(name:"Monday Group").payload
    		    ContactTag teTag1 = tPh1.createTag(name:"Shared New Clients").payload
        		[tag1, tag2, teTag1]*.save(flush:true, failOnError:true)
		    	//create items for team tags
		    	teTag1.record.addText([contents:"Hi! Hope you're doing well today."], null)
		    		.payload
		    		.save(flush:true, failOnError:true)
		    	//tag memberships
		    	tag1.addToMembers(c1)
		    	tag1.save(flush:true, failOnError:true)
		    }
    	}
    }
    def destroy = {
    }
}
