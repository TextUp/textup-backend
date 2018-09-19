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
import org.textup.*
import org.textup.rest.TwimlBuilder
import org.textup.type.*
import org.textup.validator.*
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils

@GrailsTypeChecked
class TestHelpers {

    private static final Random RANDOM = new Random()
    private static final MockMessageSource MESSAGE_SOURCE = new MockMessageSource()

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
        int randString = TestHelpers.randIntegerUpTo(Math.pow(10, 10) as Integer)
        "${Constants.TEST_DEFAULT_AREA_CODE}${randString}".padRight(10, "0")[0..9]
    }

    static int randIntegerUpTo(Integer max) {
        RANDOM.nextInt(max)
    }

    static String randString() {
        UUID.randomUUID().toString()
    }

    static String encodeBase64String(byte[] rawData) {
        Base64.encodeBase64String(rawData)
    }

    static String getChecksum(String encodedData) {
        DigestUtils.md5Hex(encodedData)
    }

    // Image samples
    // -------------

    static byte[] getSampleDataForMimeType(MediaType type) {
        switch (type) {
            case MediaType.IMAGE_PNG: return TestHelpers.getPngSampleData()
            case MediaType.IMAGE_JPEG: return TestHelpers.getJpegSampleData512()
            case MediaType.IMAGE_GIF: return TestHelpers.getGifSampleData()
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
    protected static <T> T getBean(GrailsApplication grailsApplication, Class<T> beanName) {
        grailsApplication.mainContext.getBean(beanName)
    }

    static ResultFactory getResultFactory(GrailsApplication grailsApplication) {
        ResultFactory resultFactory = TestHelpers.getBean(grailsApplication, ResultFactory)
        resultFactory.messageSource = TestHelpers.mockMessageSource()
        resultFactory
    }

    static TwimlBuilder getTwimlBuilder(GrailsApplication grailsApplication) {
        TwimlBuilder twimlBuilder = TestHelpers.getBean(grailsApplication, TwimlBuilder)
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

    static MessageSource mockMessageSource() { MESSAGE_SOURCE }

    // Object generators
    // -----------------

    static MediaElement buildMediaElement(BigDecimal sendSize = 88) {
        MediaElement e1 = new MediaElement(type: MediaType.IMAGE_JPEG,
            sendVersion: new MediaElementVersion(mediaVersion: MediaVersion.SEND,
                versionId: UUID.randomUUID().toString(),
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
            apiId: UUID.randomUUID().toString(),
            numSegments: TestHelpers.randIntegerUpTo(10))
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
