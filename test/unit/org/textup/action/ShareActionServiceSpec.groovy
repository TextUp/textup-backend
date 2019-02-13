package org.textup.action

import grails.test.mixin.TestFor
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@TestFor(ShareActionService)
class ShareActionServiceSpec extends Specification {

    def setup() {
    }

    def cleanup() {
    }

    void "test something"() {
    }

    // TODO
    // void "test sharing contact operations"() {
    //     when: "we start sharing one of our contacts with someone on a different team"
    //     Result res = p1.share(c1, p3, SharePermission.DELEGATE)

    //     then:
    //     res.success == false
    //     res.status == ResultStatus.FORBIDDEN
    //     res.errorMessages.size() == 1
    //     res.errorMessages[0] == "phone.share.cannotShare"

    //     when: "we start sharing contact with someone on the same team"
    //     c1.status = ContactStatus.ARCHIVED
    //     assert c1.save(flush:true)
    //     res = p1.share(c1, p2, SharePermission.DELEGATE)

    //     then: "new shared contact created with contact's status"
    //     res.success == true
    //     res.status == ResultStatus.OK
    //     res.payload.instanceOf(SharedContact)
    //     res.payload.status == c1.status

    //     when: "we start sharing contact that we've already shared with the same person"
    //     c1.status = ContactStatus.UNREAD
    //     assert c1.save(flush:true)
    //     int sBaseline = SharedContact.count()
    //     res = p1.share(c1, p2, SharePermission.DELEGATE)
    //     assert res.success
    //     SharedContact shared0 = res.payload
    //     res.payload.save(flush:true, failOnError:true)

    //     then: "we don't create a duplicate SharedContact and status is updated"
    //     res.status == ResultStatus.OK
    //     shared0.instanceOf(SharedContact)
    //     SharedContact.count() == sBaseline
    //     shared0.status == c1.status

    //     when: "we share three more and list all shared so far"
    //     SharedContact shared1 = p1.share(c1_1, p2, SharePermission.DELEGATE).payload,
    //         shared2 = p1.share(c1_2, p2, SharePermission.DELEGATE).payload,
    //         shared3 = p2.share(c2, p1, SharePermission.DELEGATE).payload
    //     [shared1, shared2, shared3]*.save(flush:true, failOnError:true)

    //     then:
    //     p1.sharedByMe.every { it in [shared2, shared1, shared0]*.contact }
    //     p1.sharedWithMe == [shared3]

    //     when: "we stop sharing someone else's contact"
    //     res = p1.stopShare(tC1)

    //     then:
    //     res.success == false
    //     res.status == ResultStatus.BAD_REQUEST
    //     res.errorMessages.size() == 1
    //     res.errorMessages[0] == "phone.contactNotMine"

    //     when: "we stop sharing contact that is not shared"
    //     Contact c1_3 = p1.createContact([:], ["12223334447"]).payload
    //     c1_3.save(flush:true, failOnError:true)
    //     res = p1.stopShare(c1_3)

    //     then: "silently ignore that contact is not shared"
    //     res.success == true
    //     res.status == ResultStatus.NO_CONTENT

    //     when: "we stop sharing by phones"
    //     assert p1.stopShare(p2).success
    //     p1.merge(flush:true)

    //     then:
    //     p1.sharedByMe.isEmpty() == true
    //     p1.sharedWithMe.size() == 1
    //     p1.sharedWithMe[0].id == shared3.id

    //     when: "stop sharing by contacts"
    //     SharedContact shared4 = p2.share(c2, p3, SharePermission.DELEGATE).payload
    //     shared4.save(flush:true, failOnError:true)
    //     //same underlying contact
    //     assert p2.sharedByMe.every { it in [shared4, shared3]*.contact }
    //     assert p1.sharedWithMe[0].id == shared3.id && p3.sharedWithMe[0].id == shared4.id

    //     p2.stopShare(c2)
    //     p2.merge(flush:true)

    //     then:
    //     p2.sharedByMe.isEmpty() == true
    //     p1.sharedWithMe.isEmpty() == true
    //     p3.sharedWithMe.isEmpty() == true
    // }
}
