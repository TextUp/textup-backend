package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.springframework.context.MessageSource
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([CustomAccountDetails, Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
	Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
	AnnouncementReceipt, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class ContactSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

    def setup() {
    	setupData()
    }

    def cleanup() {
    	cleanupData()
    }

    void "test constraints"() {
    	when: "we have a contact with only a phone defined"
    	Contact c1 = new Contact(phone:p1)

    	then: "an empty record is automatically added"
    	c1.validate() == true
    	c1.record != null

    	when: "we add a tag membership and define a too-long note"
    	c1.save(flush:true, failOnError:true)
    	c1.note =  TestUtils.buildVeryLongString()

    	then:
    	c1.validate() == false
    	c1.errors.errorCount == 1

    	when: "we remove the note"
    	c1.note = null

    	then:
    	c1.validate() == true
    }

    void "test getting from num and subaccount id"() {
        given:
        String customAccountId = TestUtils.randString()
        p1.customAccount = Stub(CustomAccountDetails) { getAccountId() >> customAccountId }

        when:
        Contact c1 = new Contact(phone:p1)

        then: "an empty record is automatically added"
        c1.validate() == true
        c1.fromNum.number == p1.number.number
        c1.customAccountId == p1.customAccount.accountId
    }

 	void "test no duplicate numbers for one contact, autoincrement preference"() {
 		given:
 		int maxPref = c1.numbers.max { it.preference }.preference
 		int numNums = c1.numbers.size()
 		int numBaseline = ContactNumber.count()
 		String number = "1234349230"
        List<String> sortedNums = c1.numbers
            .sort(false) { a, b -> a.preference <=> b.preference}
            .collect { it.number }

    	when: "we try to add a unique number"
    	ContactNumber cn = c1.mergeNumber(number).payload
    	cn.save(flush:true, failOnError:true)

        List<ContactNumber> contactSorted = c1.sortedNumbers
        sortedNums << number

    	then:
    	c1.numbers.size() == numNums + 1
    	cn.preference == maxPref + 1
        sortedNums.size() == contactSorted.size()
        sortedNums.eachWithIndex { n, i ->
            assert contactSorted[i].number == n
        }
    	ContactNumber.count() == numBaseline + 1

    	when: "we try to add a duplicate number"
    	cn = c1.mergeNumber(number).payload
    	cn.save(flush:true, failOnError:true)

    	then: "duplicate is ignored"
    	c1.numbers.size() == numNums + 1
    	cn.preference == maxPref + 1
    	ContactNumber.count() == numBaseline + 1

    	when: "we try to add another unique number"
        String number2 = "1234390980"
    	cn = c1.mergeNumber(number2).payload
    	cn.save(flush:true, failOnError:true)

        contactSorted = c1.sortedNumbers
        sortedNums << number2

    	then:
    	c1.numbers.size() == numNums + 2
    	cn.preference == maxPref + 2
        sortedNums.size() == contactSorted.size()
        sortedNums.eachWithIndex { n, i ->
            assert contactSorted[i].number == n
        }
    	ContactNumber.count() == numBaseline + 2

		when: "we try to add another unique number"
        String number3 = "1234390981"
		cn = c1.mergeNumber(number3).payload
    	cn.save(flush:true, failOnError:true)

        contactSorted = c1.sortedNumbers
        sortedNums << number3

    	then:
    	c1.numbers.size() == numNums + 3
    	cn.preference == maxPref + 3
        sortedNums.size() == contactSorted.size()
        sortedNums.eachWithIndex { n, i ->
            assert contactSorted[i].number == n
        }
    	ContactNumber.count() == numBaseline + 3

    	when: "we delete a number"
    	c1.deleteNumber(number)
    	c1.save(flush:true, failOnError:true)

        contactSorted = c1.sortedNumbers
        sortedNums.removeAll { it == number }

    	then:
    	c1.numbers.size() == numNums + 2
        sortedNums.size() == contactSorted.size()
        sortedNums.eachWithIndex { n, i ->
            assert contactSorted[i].number == n
        }
    	ContactNumber.count() == numBaseline + 2
    }

    void "test getting name or number"() {
        given:
        String name = TestUtils.randString()

        when: "contact has name"
        c1.name = name

        then:
        c1.nameOrNumber == name

        when: "contact no name"
        c1.name = null

        then:
        c1.nameOrNumber == c1.numbers[0].number
    }

    void "test static finders"() {
        given: "a phone with contacts"
        Phone phone1 = new Phone(numberAsString:TestUtils.randPhoneNumberString()),
            phone2 = new Phone(numberAsString:TestUtils.randPhoneNumberString())
        phone1.updateOwner(s1)
        phone2.updateOwner(s2)
        phone1.save(failOnError:true)
        phone2.save(failOnError:true)
        List<Contact> contacts = []
        List<Contact> sharedContacts = []
        Map<ContactStatus,String> statusToContactNum = [:]
        Map<ContactStatus,String> statusToSharedContactNum = [:]
        ContactStatus.values().each { ContactStatus cStatus ->
            String strNum1 = TestUtils.randPhoneNumberString()
            String strNum2 = TestUtils.randPhoneNumberString()
            // creating contacts
            statusToContactNum[cStatus] = strNum1
            contacts << phone1.createContact([status:cStatus], [strNum1]).payload
            // creating shared contacts
            statusToSharedContactNum[cStatus] = strNum2
            Contact otherContact = phone2.createContact([status:cStatus], [strNum2]).payload
            SharedContact sc1 = new SharedContact(contact:otherContact, sharedBy:phone2,
                sharedWith:phone1, permission:SharePermission.DELEGATE)
            sc1.save(flush:true, failOnError:true)
            sharedContacts << sc1
            // create deleted contact and shared contact that should not show up
            phone1.createContact([status:cStatus, isDeleted:true], [strNum1])
            Contact otherContact2 = phone2.createContact([status:cStatus, isDeleted:true], [strNum2]).payload
            SharedContact sc2 = new SharedContact(contact:otherContact, sharedBy:phone2,
                sharedWith:phone1, permission:SharePermission.DELEGATE)
            sc2.save(flush:true, failOnError:true)
            // ensure that getting shared contacts respects when contact is deleted
            assert otherContact2.sharedContacts.isEmpty() == true
            if (cStatus == ContactStatus.ACTIVE || cStatus == ContactStatus.UNREAD ||
                cStatus == ContactStatus.ARCHIVED) {
                assert otherContact.sharedContacts[0]?.id == sc1.id
            }
            else {
                assert otherContact.sharedContacts.isEmpty() == true
            }
        }
        String otherNum = TestUtils.randPhoneNumberString()

        expect:
        Contact.countForPhoneAndSearch(phone1, otherNum) == 0
        Contact.countForPhoneAndSearch(phone1, "") == 0
        Contact.countForPhoneAndSearch(phone1, null) == 0
        Contact.countForPhoneAndSearch(phone1, statusToContactNum[ContactStatus.UNREAD]) == 1
        Contact.countForPhoneAndSearch(phone1, statusToContactNum[ContactStatus.ACTIVE]) == 1
        Contact.countForPhoneAndSearch(phone1, statusToContactNum[ContactStatus.ARCHIVED]) == 1
        Contact.countForPhoneAndSearch(phone1, statusToContactNum[ContactStatus.BLOCKED]) == 0
        Contact.countForPhoneAndSearch(phone1, statusToSharedContactNum[ContactStatus.UNREAD]) == 1
        Contact.countForPhoneAndSearch(phone1, statusToSharedContactNum[ContactStatus.ACTIVE]) == 1
        Contact.countForPhoneAndSearch(phone1, statusToSharedContactNum[ContactStatus.ARCHIVED]) == 1
        Contact.countForPhoneAndSearch(phone1, statusToSharedContactNum[ContactStatus.BLOCKED]) == 0

        Contact.listForPhoneAndSearch(phone1, otherNum).isEmpty()
        Contact.listForPhoneAndSearch(phone1, "").isEmpty()
        Contact.listForPhoneAndSearch(phone1, null).isEmpty()
        Contact.listForPhoneAndSearch(phone1, statusToContactNum[ContactStatus.UNREAD])[0].id in contacts*.contactId
        Contact.listForPhoneAndSearch(phone1, statusToContactNum[ContactStatus.ACTIVE])[0].id in contacts*.contactId
        Contact.listForPhoneAndSearch(phone1, statusToContactNum[ContactStatus.ARCHIVED])[0].id in contacts*.contactId
        Contact.listForPhoneAndSearch(phone1, statusToContactNum[ContactStatus.BLOCKED]).isEmpty()
        Contact.listForPhoneAndSearch(phone1, statusToSharedContactNum[ContactStatus.UNREAD])[0].id in sharedContacts*.contactId
        Contact.listForPhoneAndSearch(phone1, statusToSharedContactNum[ContactStatus.ACTIVE])[0].id in sharedContacts*.contactId
        Contact.listForPhoneAndSearch(phone1, statusToSharedContactNum[ContactStatus.ARCHIVED])[0].id in sharedContacts*.contactId
        Contact.listForPhoneAndSearch(phone1, statusToSharedContactNum[ContactStatus.BLOCKED]).isEmpty()

        Contact.countForPhoneAndStatuses(phone1) == 4  // defaults to `ContactStatus.ACTIVE_STATUSES`
        Contact.countForPhoneAndStatuses(phone1, null) == 4  // defaults to `ContactStatus.ACTIVE_STATUSES`
        Contact.countForPhoneAndStatuses(phone1, []) == 4  // defaults to `ContactStatus.ACTIVE_STATUSES`
        Contact.countForPhoneAndStatuses(phone1, [ContactStatus.UNREAD]) == 2
        Contact.countForPhoneAndStatuses(phone1, [ContactStatus.ACTIVE]) == 2
        Contact.countForPhoneAndStatuses(phone1, [ContactStatus.ARCHIVED]) == 2
        Contact.countForPhoneAndStatuses(phone1, [ContactStatus.BLOCKED]) == 1 // Shared.sharedWithMe excludes BLOCKED

        Contact.listForPhoneAndStatuses(phone1)*.id.every { it in contacts*.contactId || it in sharedContacts*.contactId }
        Contact.listForPhoneAndStatuses(phone1, null)*.id.every { it in contacts*.contactId || it in sharedContacts*.contactId }
        Contact.listForPhoneAndStatuses(phone1, [])*.id.every { it in contacts*.contactId || it in sharedContacts*.contactId }
        Contact.listForPhoneAndStatuses(phone1, [ContactStatus.UNREAD])*.id.every { it in contacts*.contactId || it in sharedContacts*.contactId }
        Contact.listForPhoneAndStatuses(phone1, [ContactStatus.ACTIVE])*.id.every { it in contacts*.contactId || it in sharedContacts*.contactId }
        Contact.listForPhoneAndStatuses(phone1, [ContactStatus.ARCHIVED])*.id.every { it in contacts*.contactId || it in sharedContacts*.contactId }
        Contact.listForPhoneAndStatuses(phone1, [ContactStatus.BLOCKED])*.id.every { it in contacts*.contactId }

        Contact.listForPhoneAndNum(phone1, new PhoneNumber(number:otherNum)).isEmpty()
        Contact.listForPhoneAndNum(phone1, new PhoneNumber(number:"")).isEmpty()
        Contact.listForPhoneAndNum(phone1, new PhoneNumber(number:null)).isEmpty()
        Contact.listForPhoneAndNum(phone1, new PhoneNumber(number:statusToContactNum[ContactStatus.UNREAD]))[0].id in contacts*.contactId
        Contact.listForPhoneAndNum(phone1, new PhoneNumber(number:statusToContactNum[ContactStatus.ACTIVE]))[0].id in contacts*.contactId
        Contact.listForPhoneAndNum(phone1, new PhoneNumber(number:statusToContactNum[ContactStatus.ARCHIVED]))[0].id in contacts*.contactId
        Contact.listForPhoneAndNum(phone1, new PhoneNumber(number:statusToContactNum[ContactStatus.BLOCKED]))[0].id in contacts*.contactId
        Contact.listForPhoneAndNum(phone1, new PhoneNumber(number:statusToSharedContactNum[ContactStatus.UNREAD])).isEmpty()
        Contact.listForPhoneAndNum(phone1, new PhoneNumber(number:statusToSharedContactNum[ContactStatus.ACTIVE])).isEmpty()
        Contact.listForPhoneAndNum(phone1, new PhoneNumber(number:statusToSharedContactNum[ContactStatus.ARCHIVED])).isEmpty()
        Contact.listForPhoneAndNum(phone1, new PhoneNumber(number:statusToSharedContactNum[ContactStatus.BLOCKED])).isEmpty()
    }
}
