package org.textup.type

import grails.test.mixin.*
import grails.test.mixin.support.*
import org.textup.*
import org.textup.test.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class TokenTypeSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    void "test getting required keys and token size"() {
        expect:
        TokenType.values().every { !it.requiredKeys.isEmpty() }
        TokenType.values().every { it.tokenSize > 0 }

        and:
        TokenType.CALL_DIRECT_MESSAGE.tokenSize == Constants.DEFAULT_TOKEN_LENGTH
        TokenType.PASSWORD_RESET.tokenSize == Constants.DEFAULT_TOKEN_LENGTH
        TokenType.VERIFY_NUMBER.tokenSize < Constants.DEFAULT_TOKEN_LENGTH // for easier entry by users
        TokenType.NOTIFY_STAFF.tokenSize == Constants.DEFAULT_TOKEN_LENGTH
    }

    void "test building call direct message data"() {
        given:
        String ident = TestUtils.randString()
        String msg = TestUtils.randString()
        Long mId = TestUtils.randIntegerUpTo(88)
        VoiceLanguage lang = VoiceLanguage.ENGLISH

        when:
        Map data = TokenType.callDirectMessageData(null, null)

        then:
        data == [:]

        when: "neither message nor recording"
        data = TokenType.callDirectMessageData(ident, lang)

        then:
        data == [:]

        when: "only message"
        data = TokenType.callDirectMessageData(ident, lang, msg)

        then:
        data[TokenType.PARAM_CDM_IDENT] == ident
        data[TokenType.PARAM_CDM_LANG] == lang.toString()
        data[TokenType.PARAM_CDM_MESSAGE] == msg
        data[TokenType.PARAM_CDM_MEDIA] == null

        when: "only recording"
        data = TokenType.callDirectMessageData(ident, lang, null, mId)

        then:
        data[TokenType.PARAM_CDM_IDENT] == ident
        data[TokenType.PARAM_CDM_LANG] == lang.toString()
        data[TokenType.PARAM_CDM_MESSAGE] == null
        data[TokenType.PARAM_CDM_MEDIA] == mId

        when: "both message and recording"
        data = TokenType.callDirectMessageData(ident, lang, msg, mId)

        then:
        data[TokenType.PARAM_CDM_IDENT] == ident
        data[TokenType.PARAM_CDM_LANG] == lang.toString()
        data[TokenType.PARAM_CDM_MESSAGE] == msg
        data[TokenType.PARAM_CDM_MEDIA] == mId
    }

    void "test building password reset data"() {
        given:
        Long sId = TestUtils.randIntegerUpTo(88)

        when:
        Map data = TokenType.passwordResetData(null)

        then:
        data == [:]

        when:
        data = TokenType.passwordResetData(sId)

        then:
        data[TokenType.PARAM_PR_ID] == sId
    }

    void "test building verify number data"() {
        given:
        PhoneNumber pNum = TestUtils.randPhoneNumber()

        when:
        Map data = TokenType.verifyNumberData(null)

        then:
        data == [:]

        when:
        data = TokenType.verifyNumberData(pNum)

        then:
        data[TokenType.PARAM_VN_NUM] == pNum.number
    }

    void "test building notification data"() {
        given:
        Long sId = TestUtils.randIntegerUpTo(88)
        Long phoneId = TestUtils.randIntegerUpTo(88)
        Collection<Long> itemIds = [TestUtils.randIntegerUpTo(88)]

        when:
        Map data = TokenType.notifyStaffData(null, null, null)

        then:
        data == [:]

        when:
        data = TokenType.notifyStaffData(sId, itemIds, phoneId)

        then:
        data[TokenType.PARAM_NS_STAFF] == sId
        data[TokenType.PARAM_NS_ITEMS] == itemIds
        data[TokenType.PARAM_NS_PHONE] == phoneId
    }
}
