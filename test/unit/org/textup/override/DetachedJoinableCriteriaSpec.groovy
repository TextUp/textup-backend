package org.textup.override

import grails.gorm.DetachedCriteria
import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.hibernate.sql.JoinType
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class DetachedJoinableCriteriaSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test methods with an association of specified join type"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()

        when:
        DetachedJoinableCriteria customCriteria = new DetachedJoinableCriteria(PhoneRecord).build {
            createAliasWithJoin("shareSource", "ssource1", JoinType.LEFT_OUTER_JOIN)
            or {
                eq("id", ipr1.id)
                eq("ssource1.id", spr1.shareSource.id)
            }
        }
        Collection prs = customCriteria.list()

        then:
        prs.size() == 2
        ipr1 in prs
        spr1 in prs

        when:
        DetachedCriteria criteria = new DetachedCriteria(PhoneRecord).build {
            or {
                eq("id", ipr1.id)
                shareSource {
                    eq("id", spr1.shareSource.id)
                }
            }
        }
        prs = criteria.list()

        then: "`IndividualPhoneRecord` excluded by inner join for `shareSource`"
        prs == [spr1]
    }
}
