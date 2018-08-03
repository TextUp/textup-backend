package org.textup.util

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import groovy.transform.TypeCheckingMode
import groovy.xml.MarkupBuilder
import java.nio.file.Paths
import org.apache.commons.io.IOUtils
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.quartz.Scheduler
import org.quartz.TriggerKey
import org.springframework.context.MessageSource
import org.springframework.context.MessageSourceResolvable
import org.textup.*
import org.textup.rest.TwimlBuilder
import org.textup.type.*
import org.textup.validator.*

@GrailsTypeChecked
class TestHelpers {

    private static final Random RANDOM = new Random()

    // Display
    // -------

    static Map jsonToMap(JSON json) {
        Helpers.toJson(json.toString()) as Map
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static String buildXml(Closure data) {
        StringWriter writer = new StringWriter()
        MarkupBuilder xmlBuilder = new MarkupBuilder(writer)
        xmlBuilder(data)
        writer.toString().replaceAll(/<call>|<\/call>|\s+/, "").trim()
    }

    // Utilities
    // ---------

    static String randPhoneNumber() {
        int randString = RANDOM.nextInt(Math.pow(10, 10) as Integer)
        "${Constants.TEST_DEFAULT_AREA_CODE}${randString}".padRight(10, "0")[0..9]
    }

    // Image samples
    // -------------

    static byte[] getSampleDataForMimeType(String mimeType) {
        switch (mimeType) {
            case Constants.MIME_TYPE_PNG: return TestHelpers.getPngSampleData()
            case Constants.MIME_TYPE_JPEG: return TestHelpers.getJpegSampleData512()
            default: return TestHelpers.getGifSampleData()
        }
    }

    static byte[] getJpegSampleData512() {
        getSampleData("512x512.jpeg")
    }

    static byte[] getJpegSampleData256() {
        getSampleData("256x256.jpeg")
    }

    static byte[] getPngSampleData() {
        getSampleData("800x600.png")
    }

    static byte[] getGifSampleData() {
        getSampleData("400x400.gif")
    }

    protected static byte[] getSampleData(String fileName) {
        String root = Paths.get(".").toAbsolutePath().normalize().toString()
        new FileInputStream("${root}/test/assets/${fileName}").withStream { InputStream iStream ->
            IOUtils.toByteArray(iStream)
        }
    }

    // Mocking dependencies
    // --------------------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Object getBean(GrailsApplication grailsApplication, String beanName) {
        grailsApplication.mainContext.getBean(beanName)
    }

    static ResultFactory getResultFactory(GrailsApplication grailsApplication) {
        ResultFactory resultFactory = TestHelpers.getBean(grailsApplication, "resultFactory") as ResultFactory
        resultFactory.messageSource = TestHelpers.mockMessageSource()
        resultFactory
    }

    static TwimlBuilder getTwimlBuilder(GrailsApplication grailsApplication) {
        TwimlBuilder twimlBuilder = TestHelpers.getBean(grailsApplication, "twimlBuilder") as TwimlBuilder
        twimlBuilder.resultFactory = TestHelpers.getResultFactory(grailsApplication)
        twimlBuilder.messageSource = TestHelpers.mockMessageSource()
        twimlBuilder.linkGenerator = TestHelpers.mockLinkGenerator()
        twimlBuilder
    }

    static LinkGenerator mockLinkGenerator() {
        [link: { Map m -> (m.params ?: [:]).toString() }] as LinkGenerator
    }

    static Scheduler mockScheduler() {
        [getTrigger: { TriggerKey key -> null }] as Scheduler
    }

    static MessageSource mockMessageSource() {
        [getMessage: { String c, Object[] p, Locale l -> c }] as MessageSource
    }

    static MessageSource mockMessageSourceWithResolvable() {
        [
            getMessage: { MessageSourceResolvable resolvable, Locale l -> resolvable.codes.last() }
        ] as MessageSource
    }

    // Object generators
    // -----------------

    static MediaElement buildMediaElement(BigDecimal sendSize = 88) {
        MediaElement e1 = new MediaElement(type: MediaType.IMAGE,
            sendVersion: new MediaElementVersion(mediaVersion: MediaVersion.SEND,
                key: UUID.randomUUID().toString(),
                sizeInBytes: sendSize.longValue(),
                widthInPixels: 888))
        assert e1.validate()
        e1
    }

    static RecordItemReceipt buildReceipt(ReceiptStatus status = ReceiptStatus.PENDING) {
        RecordItemReceipt rpt = new RecordItemReceipt(status: status,
            contactNumberAsString: TestHelpers.randPhoneNumber(),
            apiId: UUID.randomUUID().toString())
        rpt
    }

    static TempRecordReceipt buildTempReceipt(ReceiptStatus status = ReceiptStatus.PENDING) {
        TempRecordReceipt rpt = new TempRecordReceipt(status: status,
            contactNumberAsString: TestHelpers.randPhoneNumber(),
            apiId: UUID.randomUUID().toString())
        assert rpt.validate()
        rpt
    }

    static OutgoingMessage buildOutgoingMessage(String message = "hi") {
        OutgoingMessage text = new OutgoingMessage(message: message,
            contacts: new ContactRecipients(),
            sharedContacts: new SharedContactRecipients(),
            tags: new ContactTagRecipients())
        assert text.validate()
        text
    }
}
