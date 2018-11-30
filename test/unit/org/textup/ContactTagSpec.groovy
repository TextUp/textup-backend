package org.textup

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.type.AuthorType
import org.textup.type.ContactStatus
import org.textup.type.VoiceLanguage
import org.textup.util.*
import spock.lang.Shared

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class ContactTagSpec extends CustomSpec {

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
        when: "we have a blank tag"
        ContactTag t1 = new ContactTag()

        then: "invalid"
        t1.validate() == false
        t1.errors.errorCount == 2

        when: "we add a tag with all fields filled"
        String tagName = "tag1"
        t1 = new ContactTag(phone:p1, name:tagName)

        then: "valid"
        t1.validate()
        t1.save(flush:true, failOnError:true)

        when: "we add a tag with a duplicate name"
        t1 = new ContactTag(phone:p1, name:tagName)

        then:
        t1.validate() == false
        t1.errors.errorCount == 1

        when: "we add a tag with a unique name"
        t1.name = "${tagName}UNIQUE"

        then:
        t1.validate() == true
    }

    void "test language"() {
        given: "a valid empty contact"
        ContactTag ct1 = new ContactTag(phone:p1, name:UUID.randomUUID().toString())
        assert ct1.validate() == true

        when: "we set the underlying record's language"
        ct1.record.language = VoiceLanguage.RUSSIAN

        then: "that change is reflected"
        ct1.language == VoiceLanguage.RUSSIAN

        when: "we set the language using the proxy method"
        ct1.language = VoiceLanguage.PORTUGUESE

        then: "change is still reflected"
        ct1.language == VoiceLanguage.PORTUGUESE
        ct1.record.language == VoiceLanguage.PORTUGUESE
    }

    void "test retrieving members"() {
        when: "tag with no members"
        ContactTag t1 = new ContactTag(phone:p1, name:"tag1")
        t1.save(flush:true, failOnError:true)

        then:
        t1.members == null

        when: "we add an active contact"
        c1.status == ContactStatus.ACTIVE
        t1.addToMembers(c1)
        t1.save(flush:true, failOnError:true)

        then:
        t1.members.size() == 1
        c1.tags.contains(t1)
        t1.getMembersByStatus([ContactStatus.ACTIVE]).size() == 1
        t1.getMembersByStatus([ContactStatus.ACTIVE])[0] == c1
        t1.getMembersByStatus([ContactStatus.UNREAD]).size() == 0
        t1.getMembersByStatus([ContactStatus.ARCHIVED]).size() == 0
        t1.getMembersByStatus([ContactStatus.BLOCKED]).size() == 0

        when: "we change contact status to unread"
        c1.status = ContactStatus.UNREAD
        c1.save(flush:true, failOnError:true)

        then:
        c1.tags.contains(t1)
        t1.getMembersByStatus([ContactStatus.UNREAD]).size() == 1
        t1.getMembersByStatus([ContactStatus.UNREAD])[0] == c1
        t1.getMembersByStatus([ContactStatus.ACTIVE]).size() == 0
        t1.getMembersByStatus([ContactStatus.ARCHIVED]).size() == 0
        t1.getMembersByStatus([ContactStatus.BLOCKED]).size() == 0

        when: "we change contact status to archived"
        c1.status = ContactStatus.ARCHIVED
        c1.save(flush:true, failOnError:true)

        then:
        c1.tags.contains(t1)
        t1.getMembersByStatus([ContactStatus.ARCHIVED]).size() == 1
        t1.getMembersByStatus([ContactStatus.ARCHIVED])[0] == c1
        t1.getMembersByStatus([ContactStatus.ACTIVE]).size() == 0
        t1.getMembersByStatus([ContactStatus.UNREAD]).size() == 0
        t1.getMembersByStatus([ContactStatus.BLOCKED]).size() == 0

        when: "we change contact status to blocked"
        c1.status = ContactStatus.BLOCKED
        c1.save(flush:true, failOnError:true)

        then:
        c1.tags.contains(t1)
        t1.getMembersByStatus([ContactStatus.BLOCKED]).size() == 1
        t1.getMembersByStatus([ContactStatus.BLOCKED])[0] == c1
        t1.getMembersByStatus([ContactStatus.ACTIVE]).size() == 0
        t1.getMembersByStatus([ContactStatus.ARCHIVED]).size() == 0
        t1.getMembersByStatus([ContactStatus.UNREAD]).size() == 0
    }

    void "test static finders"() {
        when: "tags and contacts all not deleted"
        Contact contact1 = p1.createContact([:], [TestUtils.randPhoneNumber()]).payload
        Contact contact2 = p1.createContact([:], [TestUtils.randPhoneNumber()]).payload
        Contact contact3 = p1.createContact([:], [TestUtils.randPhoneNumber()]).payload
        ContactTag tag1 = p1.createTag([name:TestUtils.randPhoneNumber()]).payload
        ContactTag tag2 = p1.createTag([name:TestUtils.randPhoneNumber()]).payload

        tag1.addToMembers(contact1)
        tag1.addToMembers(contact2)
        tag2.addToMembers(contact1)
        ContactTag.withSession { it.flush() }

        Collection<ContactTag> tags = [tag1, tag2]

        then:
        ContactTag.findEveryByContactIds([]*.id).isEmpty()
        ContactTag.findEveryByContactIds([contact3]*.id).isEmpty()
        ContactTag.findEveryByContactIds([contact1]*.id).size() == 2
        ContactTag.findEveryByContactIds([contact1]*.id)*.id.every { it in tags*.id }

        ContactTag.findEveryByContactIds([contact2]*.id).size() == 1
        ContactTag.findEveryByContactIds([contact2]*.id)*.id.every { it in tags*.id }

        ContactTag.findEveryByContactIds([contact1, contact2]*.id).size() == 2
        ContactTag.findEveryByContactIds([contact1, contact2]*.id)*.id.every { it in tags*.id }

        ContactTag.findEveryByContactIds([contact3, contact2]*.id).size() == 1
        ContactTag.findEveryByContactIds([contact3, contact2]*.id)*.id.every { it in tags*.id }

        ContactTag.findEveryByContactIds([contact1, contact2, contact3]*.id).size() == 2
        ContactTag.findEveryByContactIds([contact1, contact2, contact3]*.id)*.id.every { it in tags*.id }

        when: "both tags deleted, contacts not deleted"
        tags.each { ContactTag cTag ->
            cTag.isDeleted = true
            cTag.save(flush:true, failOnError:true)
        }
        [contact1, contact2, contact3].each { Contact contact ->
            contact.isDeleted = false
            contact.save(flush:true, failOnError:true)
        }

        then: "no results"
        ContactTag.findEveryByContactIds([contact1, contact2, contact3]*.id).size() == 0
        ContactTag.findEveryByContactIds([contact1, contact2, contact3]*.id).isEmpty() == true

        when: "contacts all deleted, tags not deleted"
        tags.each { ContactTag cTag ->
            cTag.isDeleted = false
            cTag.save(flush:true, failOnError:true)
        }
        [contact1, contact2, contact3].each { Contact contact ->
            contact.isDeleted = true
            contact.save(flush:true, failOnError:true)
        }

        then: "no results"
        ContactTag.findEveryByContactIds([contact1, contact2, contact3]*.id).size() == 0
        ContactTag.findEveryByContactIds([contact1, contact2, contact3]*.id).isEmpty() == true

        when: "neither tags nor contacts are deleted"
        tags.each { ContactTag cTag ->
            cTag.isDeleted = false
            cTag.save(flush:true, failOnError:true)
        }
        [contact1, contact2, contact3].each { Contact contact ->
            contact.isDeleted = false
            contact.save(flush:true, failOnError:true)
        }

        then: "results show up again"
        ContactTag.findEveryByContactIds([contact1, contact2, contact3]*.id).size() == 2
        ContactTag.findEveryByContactIds([contact1, contact2, contact3]*.id)*.id.every { it in tags*.id }
    }
}
