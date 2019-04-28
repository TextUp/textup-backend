package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class PhoneJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling when not logged in"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") {
            Result.createError([], ResultStatus.NOT_FOUND)
        }

        when:
        Map json = TestUtils.objToJsonMap(p1)

        then:
        json.awayMessage == p1.awayMessage
        json.awayMessageMaxLength == ValidationUtils.TEXT_BODY_LENGTH * 2
        json.id == p1.id
        json.isActive == p1.isActive()
        json.language == p1.language.toString()
        json.media == null
        json.number == p1.number.prettyPhoneNumber
        json.useVoicemailRecordingIfPresent == p1.useVoicemailRecordingIfPresent
        json.voice == p1.voice.toString()
        json.allowSharingWithOtherTeams == null
        json.tags == null
        json.policies == null

        cleanup:
        tryGetActiveAuthUser?.restore()
    }

    void "test marshalling logged in"() {
        given:
        Team t1 = TestUtils.buildTeam()

        Staff s1 = TestUtils.buildStaff()
        t1.addToMembers(s1)
        Staff s2 = TestUtils.buildStaff()
        t1.addToMembers(s2)
        Staff s3 = TestUtils.buildStaff()

        Phone tp1 = TestUtils.buildActiveTeamPhone(t1)
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(tp1)

        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") {
            Result.createSuccess(s3)
        }
        MockedMethod isAdminAt = MockedMethod.create(Organizations, "isAdminAt") { true }

        when: "an admin"
        Map json = TestUtils.objToJsonMap(tp1)

        then: "can access phone"
        json.awayMessage == tp1.awayMessage
        json.awayMessageMaxLength == ValidationUtils.TEXT_BODY_LENGTH * 2
        json.id == tp1.id
        json.isActive == tp1.isActive()
        json.language == tp1.language.toString()
        json.media == null
        json.number == tp1.number.prettyPhoneNumber
        json.useVoicemailRecordingIfPresent == tp1.useVoicemailRecordingIfPresent
        json.voice == tp1.voice.toString()
        json.allowSharingWithOtherTeams == tp1.owner.allowSharingWithOtherTeams
        json.tags instanceof Collection
        json.tags.size() == 1
        json.tags[0].id == gpr1.id
        json.policies instanceof Collection
        json.policies.size() == 2
        json.policies.any { it.staffId == s1.id }
        json.policies.any { it.staffId == s2.id }

        when: "a non-admin staff member"
        isAdminAt = MockedMethod.create(isAdminAt) { false }
        json = TestUtils.objToJsonMap(tp1)

        then: "this is not a phone we can access"
        json.allowSharingWithOtherTeams == null
        json.tags == null
        json.policies == null

        when: "non-admin and this is our phone"
        tryGetActiveAuthUser = MockedMethod.create(tryGetActiveAuthUser) { Result.createSuccess(s1) }
        json = TestUtils.objToJsonMap(tp1)

        then: "can access phone"
        json.allowSharingWithOtherTeams == tp1.owner.allowSharingWithOtherTeams
        json.tags instanceof Collection
        json.tags.size() == 1
        json.tags[0].id == gpr1.id
        json.policies instanceof Collection
        json.policies.size() == 2
        json.policies.any { it.staffId == s1.id }
        json.policies.any { it.staffId == s2.id }

        cleanup:
        tryGetActiveAuthUser?.restore()
        isAdminAt?.restore()
    }

    void "test marshalling with various voicemail options"() {
        given:
        String customMsg = TestUtils.randString()

        Phone p1 = TestUtils.buildActiveStaffPhone()
        p1.awayMessage = customMsg
        p1.media = null
        p1.useVoicemailRecordingIfPresent = false
        p1.voice = VoiceType.FEMALE

        MediaElement el1 = TestUtils.buildMediaElement()
        el1.sendVersion.type = MediaType.AUDIO_MP3
        MediaInfo mInfo = TestUtils.buildMediaInfo()
        mInfo.addToMediaElements(el1)

        Phone.withSession { it.flush() }

        when: "use robovoice to read away message -- no voicemail recording"
        Map json = TestUtils.objToJsonMap(p1)

        then:
        json.id == p1.id
        json.awayMessage.contains(customMsg)
        json.voice == VoiceType.FEMALE.toString()
        json.useVoicemailRecordingIfPresent == false
        json.media == null

        when: "use voicemail recording"
        p1.media = mInfo
        p1.useVoicemailRecordingIfPresent = true
        p1.save(flush: true, failOnError: true)

        json = TestUtils.objToJsonMap(p1)

        then:
        json.id == p1.id
        json.awayMessage.contains(customMsg)
        json.voice == VoiceType.FEMALE.toString()
        json.useVoicemailRecordingIfPresent == true
        json.media instanceof Map
        json.media.id == p1.media.id
        json.media.audio[0].uid == el1.uid
        json.media.audio[0].versions instanceof Collection
        json.media.audio[0].versions*.type.every { it in MediaType.AUDIO_TYPES*.mimeType }
    }
}
