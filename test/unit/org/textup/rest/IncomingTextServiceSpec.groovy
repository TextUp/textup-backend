package org.textup.rest

import grails.test.mixin.TestFor
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

// TODO

@TestFor(IncomingTextService)
class IncomingTextServiceSpec extends Specification {

    // // Texts
    // // -----

    // void "test build texts helper"() {
    //     given:
    //     IncomingText text = new IncomingText(apiId: "testing", message: "hello", numSegments: 88)
    //     assert text.validate()
    //     IncomingSession session = new IncomingSession(phone:p1, numberAsString: "1112223333")
    //     assert session.save(flush: true, failOnError: true)
    //     RecordText rText1
    //     Closure<Void> storeText = { rText1 = it }
    //     int tBaseline = RecordText.count()
    //     int rptBaseline = RecordItemReceipt.count()

    //     when:
    //     assert service.buildTextsHelper(text, session, storeText, c1) == null
    //     RecordText.withSession { it.flush() }

    //     then: "new text created + closure called"
    //     rText1 instanceof RecordText
    //     RecordText.count() == tBaseline + 1
    //     RecordItemReceipt.count() == rptBaseline + 1
    // }

    // void "test building texts overall"() {
    //     given:
    //     service.socketService = GroovyMock(SocketService)
    //     Phone newPhone = new Phone(numberAsString:TestUtils.randPhoneNumberString())
    //     newPhone.updateOwner(s1)
    //     newPhone.save(flush:true, failOnError:true)
    //     IncomingText text = new IncomingText(apiId: "testing", message: "hello", numSegments: 88)
    //     assert text.validate()
    //     IncomingSession sess1 = new IncomingSession(phone:p1, numberAsString: "1112223333")
    //     assert sess1.save(flush: true, failOnError: true)

    //     int cBaseline = Contact.count()
    //     int rBaseline = Record.count()
    //     int tBaseline = RecordText.count()
    //     int rptBaseline = RecordItemReceipt.count()

    //     when:
    //     Result<Tuple<List<RecordText>, List<Contact>>> res = service.buildTexts(newPhone, text, sess1)
    //     Contact.withSession { it.flush() }

    //     then: "new contact and text is created"
    //     1 * service.socketService.sendContacts(*_)
    //     res.status == ResultStatus.OK
    //     res.payload.first.size() == 1
    //     res.payload.first[0].contents == text.message
    //     res.payload.second.size() == 1
    //     res.payload.second[0].sortedNumbers[0].number == sess1.numberAsString
    //     Contact.count() == cBaseline + 1
    //     Record.count() == rBaseline + 1
    //     RecordText.count() == tBaseline + 1
    //     RecordItemReceipt.count() == rptBaseline + 1
    // }

    // void "test building response to incoming text"() {
    //     given:
    //     BasicNotification bn1 = GroovyMock(BasicNotification)
    //     RecordText rText = GroovyMock(RecordText)
    //     service.announcementService = GroovyMock(AnnouncementService)
    //     String msg = TestUtils.randString()

    //     when: "no notifications"
    //     Result<Closure> res = service.buildTextResponse(p1, null, [rText], [])

    //     then: "sends away message"
    //     1 * rText.setHasAwayMessage(true)
    //     1 * service.announcementService.tryBuildTextInstructions(*_) >> new Result(payload: [msg])
    //     res.status == ResultStatus.OK
    //     TestUtils.buildXml(res.payload).contains(TestUtils.buildXml { Message(msg) })
    //     TestUtils.buildXml(res.payload).contains(TestUtils.buildXml { Message(p1.buildAwayMessage()) })

    //     when: "has notifications"
    //     res = service.buildTextResponse(p1, null, [rText], [bn1])

    //     then:
    //     0 * rText._
    //     1 * service.announcementService.tryBuildTextInstructions(*_) >> new Result(payload: [msg])
    //     TestUtils.buildXml(res.payload).contains(TestUtils.buildXml { Message(msg) })
    //     TestUtils.buildXml(res.payload).contains(TestUtils.buildXml { Message(p1.buildAwayMessage()) }) == false
    // }

    // void "test finishing processing texts helper"() {
    //     given:
    //     service.notificationService = GroovyMock(NotificationService)
    //     service.socketService = GroovyMock(SocketService)
    //     IncomingText text = new IncomingText(apiId: "testing", message: "hello", numSegments: 88)
    //     assert text.validate()
    //     MediaInfo invalidMedia = new MediaInfo()
    //     MediaElement e1 = TestUtils.buildMediaElement()
    //     e1.sendVersion.type = null
    //     invalidMedia.addToMediaElements(e1)
    //     assert invalidMedia.validate() == false
    //     List<Long> textIds = [rText1, rText2]*.id
    //     List<BasicNotification> notifs = []

    //     when: "has some errors"
    //     Result<Void> res = service.finishProcessingTextHelper(text, textIds, notifs, invalidMedia)

    //     then: "errors are bubbled up"
    //     0 * service.notificationService._
    //     0 * service.socketService._
    //     res.status == ResultStatus.OK.UNPROCESSABLE_ENTITY

    //     when: "no errors"
    //     res = service.finishProcessingTextHelper(text, textIds, notifs, null)

    //     then:
    //     1 * service.notificationService.send(*_) >> new ResultGroup()
    //     1 * service.socketService.sendItems(*_) >> new ResultGroup()
    //     res.status == ResultStatus.NO_CONTENT
    // }

    // @DirtiesRuntime
    // void "test finishing processing texts overall"() {
    //     given:
    //     service.incomingMediaService = GroovyMock(IncomingMediaService)
    //     IncomingText text = Stub(IncomingText) { getApiId() >> "hi" }
    //     TypeMap params = TypeMap.create()
    //     MediaElement e1 = TestUtils.buildMediaElement()
    //     MockedMethod finishProcessingTextHelper = MockedMethod.create(service, "finishProcessingTextHelper") { new Result() }
    //     int mBaseline = MediaInfo.count()
    //     int eBaseline = MediaElement.count()

    //     when: "no media"
    //     Result<Void> res = service.finishProcessingText(text, null, null, params)
    //     MediaInfo.withSession { it.flush() }

    //     then:
    //     0 * service.incomingMediaService._
    //     finishProcessingTextHelper.callCount == 1
    //     res.status == ResultStatus.OK
    //     MediaInfo.count() == mBaseline
    //     MediaElement.count()  == eBaseline

    //     when: "has media"
    //     params.NumMedia = 8
    //     res = service.finishProcessingText(text, null, null, params)
    //     MediaInfo.withSession { it.flush() }

    //     then:
    //     1 * service.incomingMediaService.process(*_) >> new Result(payload: e1).toGroup()
    //     finishProcessingTextHelper.callCount == 2
    //     res.status == ResultStatus.OK
    //     MediaInfo.count() == mBaseline + 1
    //     MediaElement.count()  == eBaseline + 1
    // }

    // @DirtiesRuntime
    // void "test processing texts overall for blocked vs not blocked"() {
    //     given:
    //     service.notificationService = GroovyMock(NotificationService)
    //     service.threadService = GroovyMock(ThreadService)
    //     MockedMethod finishProcessingText = MockedMethod.create(service, "finishProcessingText")
    //         { new Result() }
    //     MockedMethod buildTextResponse = MockedMethod.create(service, "buildTextResponse")
    //         { new Result() }

    //     when: "blocked"
    //     MockedMethod buildTexts = MockedMethod.create(service, "buildTexts")
    //         { new Result(payload: Tuple.create([], [])) }
    //     Result<Closure> res = service.processText(null, null, null, null)

    //     then:
    //     buildTexts.callCount == 1
    //     1 * service.notificationService.build(*_) >> []
    //     1 * service.threadService.delay(*_) >> { args -> args[2].call(); null; }
    //     finishProcessingText.callCount == 1
    //     buildTextResponse.callCount == 0
    //     res.status == ResultStatus.OK
    //     TestUtils.buildXml(res.payload).contains("twimlBuilder.text.blocked")

    //     when: "not blocked"
    //     buildTexts.restore()
    //     buildTexts = MockedMethod.create(service, "buildTexts")
    //         { new Result(payload: Tuple.create([], [c1])) }
    //     res = service.processText(null, null, null, null)

    //     then:
    //     buildTexts.callCount == 1
    //     1 * service.notificationService.build(*_) >> []
    //     1 * service.threadService.delay(*_) >> { args -> args[2].call(); null; }
    //     finishProcessingText.callCount == 2
    //     buildTextResponse.callCount == 1
    //     res.status == ResultStatus.OK
    // }
}
