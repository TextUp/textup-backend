package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class AnnouncementJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling announcement"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement(p1)
        AnnouncementReceipt aRpt1 = TestUtils.buildAnnouncementReceipt(fa1)
        aRpt1.type = RecordItemType.TEXT
        AnnouncementReceipt aRpt2 = TestUtils.buildAnnouncementReceipt(fa1)
        aRpt2.type = RecordItemType.CALL

        FeaturedAnnouncement.withSession { it.flush() }

    	when:
    	Map json = TestUtils.objToJsonMap(fa1)

    	then:
        json.expiresAt == fa1.expiresAt.toString()
        json.id == fa1.id
        json.isExpired == fa1.isExpired
        json.message == fa1.message
        json.staff == fa1.phone.owner.ownerId
        json.team == null
        json.whenCreated == fa1.whenCreated.toString()

        json.receipts instanceof Map
        json.receipts.recipients instanceof Collection
        json.receipts.recipients.size() == 2
        aRpt1.session.number.prettyPhoneNumber in json.receipts.recipients
        aRpt2.session.number.prettyPhoneNumber in json.receipts.recipients
        json.receipts.textRecipients instanceof Collection
        json.receipts.textRecipients.size() == 1
        aRpt1.session.number.prettyPhoneNumber in json.receipts.recipients
        json.receipts.callRecipients instanceof Collection
        json.receipts.callRecipients.size() == 1
        aRpt2.session.number.prettyPhoneNumber in json.receipts.recipients
    }
}
