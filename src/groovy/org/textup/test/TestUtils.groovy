package org.textup.test

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import groovy.util.slurpersupport.GPathResult
import groovy.xml.MarkupBuilder
import java.nio.file.*
import javax.xml.transform.stream.*
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.codehaus.groovy.reflection.*
import org.quartz.*
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.media.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Log4j
class TestUtils {

    private static final Random RANDOM = new Random()
    private static final MockMessageSource MESSAGE_SOURCE = new MockMessageSource()
    private static final HashSet<String> GENERATED_NUMBERS = new HashSet<>()
    private static final ConfigObject CONFIG = new ConfigSlurper()
        .parse(new File("grails-app/conf/Config.groovy").toURL())
    private static final OutputStreamCaptor OUTPUT_CAPTOR = new OutputStreamCaptor()

    // Display
    // -------

    static Map jsonToMap(JSON json) {
        DataFormatUtils.jsonToObject(json.toString()) as Map
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static String buildXml(Closure data) {
        StringWriter writer = new StringWriter()
        MarkupBuilder xmlBuilder = new MarkupBuilder(writer)
        xmlBuilder(data)
        writer.toString().replaceAll(/<call>|<\/call>|\s+/, "").trim()
    }

    static GPathResult buildXmlTransformOutput(GrailsApplication grailsApplication, RecordItemRequest iReq) {
        String xmlString = DataFormatUtils.toXmlString(DataFormatUtils.jsonToObject(iReq))
        StringWriter writer = new StringWriter()

        StreamSource src = new StreamSource(new StringReader(xmlString))
        StreamResult sRes = new StreamResult(writer)
        getBean(grailsApplication, PdfService).buildTransformer().transform(src, sRes)

        new XmlSlurper().parseText(writer.toString())
    }

    // Utilities
    // ---------

    static PhoneNumber randPhoneNumber() {
        PhoneNumber pNum = new PhoneNumber(number: TestUtils.randPhoneNumberString())
        assert pNum.validate()
        pNum
    }
    static String randPhoneNumberString() {
        String randNumber = generatePhoneNumber()
        while (GENERATED_NUMBERS.contains(randNumber)) {
            randNumber = generatePhoneNumber()
        }
        GENERATED_NUMBERS.add(randNumber)
        randNumber
    }
    private static String generatePhoneNumber() {
        int randString = TestUtils.randIntegerUpTo(Math.pow(10, 10) as Integer)
        "${TestConstants.TEST_DEFAULT_AREA_CODE}${randString}".padRight(10, "0")[0..9]
    }

    static int randIntegerUpTo(Integer max, boolean ensurePositive = false) {
        int rand = RANDOM.nextInt(max)
        (ensurePositive && rand <= 0) ? 1 : rand
    }

    static String randString() {
        UUID.randomUUID().toString()
    }

    static String randUrl() {
        new URI("https://www.example.com/${TestUtils.randString()}")
    }

    static String encodeBase64String(byte[] rawData) {
        Base64.encodeBase64String(rawData)
    }

    static String getChecksum(String encodedData) {
        DigestUtils.md5Hex(encodedData)
    }

    static String buildVeryLongString() {
        StringBuilder sBuilder = new StringBuilder()
        ValidationUtils.MAX_TEXT_COLUMN_SIZE.times { it -> sBuilder << it }
        sBuilder.toString()
    }

    // Media
    // -----

    static byte[] getSampleDataForMimeType(MediaType type) {
        switch (type) {
            case MediaType.IMAGE_PNG:
                return TestUtils.getPngSampleData()
            case MediaType.IMAGE_JPEG:
                return TestUtils.getJpegSampleData512()
            case MediaType.IMAGE_GIF:
                return TestUtils.getGifSampleData()
            case MediaType.AUDIO_MP3:
                return TestUtils.getSampleData("audio.mp3")
            case MediaType.AUDIO_OGG_VORBIS:
                return TestUtils.getSampleData("audio-vorbis.ogg")
            case MediaType.AUDIO_OGG_OPUS:
                return TestUtils.getSampleData("audio-opus.ogg")
            case MediaType.AUDIO_WEBM_VORBIS:
                return TestUtils.getSampleData("audio-vorbis.webm")
            case MediaType.AUDIO_WEBM_OPUS:
                return TestUtils.getSampleData("audio-opus.webm")
            default:
                return [] as byte[]
        }
    }

    static Map buildAddMediaAction(MediaType type) {
        byte[] data = TestUtils.getSampleDataForMimeType(type)
        String encodedData = TestUtils.encodeBase64String(data)
        [
            action: MediaAction.ADD,
            mimeType: type.mimeType,
            data: encodedData,
            checksum: TestUtils.getChecksum(encodedData)
        ]
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

    // Files
    // -----

    static void clearTempDirectory() {
        TestUtils.tempDirectory.toFile().listFiles().each { File file -> file.delete() }
    }

    static int getNumInTempDirectory() {
        TestUtils.tempDirectory.toFile().listFiles().length
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Path getTempDirectory() {
        String tempDirectory = TestUtils.config.textup.tempDirectory
        Paths.get(tempDirectory)
    }

    // Mocking dependencies
    // --------------------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static <T> T getBean(GrailsApplication grailsApplication, Class<T> beanName) {
        grailsApplication.mainContext.getBean(beanName)
    }

    static ConfigObject getConfig() {
        CONFIG
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static AudioUtils getAudioUtils() {
        String executableDirectory = TestUtils.config.textup.media.audio.executableDirectory
        String executableName = TestUtils.config.textup.media.audio.executableName
        String tempDirectory = TestUtils.config.textup.tempDirectory
        new AudioUtils(executableDirectory, executableName, tempDirectory)
    }

    static ResultFactory getResultFactory(GrailsApplication grailsApplication) {
        TestUtils.getBean(grailsApplication, ResultFactory)
    }

    static LinkGenerator mockLinkGenerator() {
        [link: { Map m -> (m.params ?: [:]).toString() }] as LinkGenerator
    }

    static LinkGenerator mockLinkGeneratorWithDomain(String domain = "https://www.example.com") {
        [link: { Map m ->
            Object queryParams = m.params ?: [:]
            "${domain}?${queryParams.toString()}".toString()
        }] as LinkGenerator
    }

    static Scheduler mockScheduler() {
        [getTrigger: { TriggerKey key -> null }] as Scheduler
    }

    static MessageSource mockMessageSource() {
        MESSAGE_SOURCE
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static void mockJsonToString() {
        // in unit tests, don't have custom `default` marshallers so replace with simple JSON cast
        DataFormatUtils.metaClass."static".toJsonString = { it ? (it as JSON).toString() : "" }
    }

    // Object generators
    // -----------------

    static Location buildLocation() {
        Location loc1 = new Location(address:"Testing Address", lat:0G, lon:0G)
        loc1.save(flush:true, failOnError:true)
    }

    static MediaElement buildMediaElement(BigDecimal sendSize = 88) {
        MediaElement e1 = new MediaElement()
        e1.sendVersion = TestUtils.buildMediaElementVersion(sendSize)
        assert e1.validate()
        e1
    }

    static UploadItem buildUploadItem(MediaType type = MediaType.AUDIO_MP3) {
        UploadItem uItem = new UploadItem(type: type, data: getSampleDataForMimeType(type))
        assert uItem.validate()
        uItem
    }

    static MediaElementVersion buildMediaElementVersion(BigDecimal sendSize = 88) {
        MediaElementVersion mVers1 = new MediaElementVersion(type: MediaType.IMAGE_JPEG,
            versionId: UUID.randomUUID().toString(),
            sizeInBytes: sendSize.longValue(),
            widthInPixels: 888)
        assert mVers1.validate()
        mVers1
    }

    static RecordItemReceipt buildReceipt(ReceiptStatus status = ReceiptStatus.PENDING) {
        RecordItemReceipt rpt1 = new RecordItemReceipt(status: status,
            contactNumberAsString: TestUtils.randPhoneNumberString(),
            apiId: UUID.randomUUID().toString())
        rpt1
    }

    static TempRecordReceipt buildTempReceipt(ReceiptStatus status = ReceiptStatus.PENDING) {
        TempRecordReceipt rpt1 = TempRecordReceipt
            .tryCreate(TestUtils.randString(), TestUtils.randPhoneNumber())
            .payload
        rpt1.status = status
        rpt1.numSegments = TestUtils.randIntegerUpTo(10)
        assert rpt1.validate()
        rpt1
    }

    static RecordItemRequest buildRecordItemRequest(Phone p1) {
        RecordItemRequest.tryCreate(p1, [], false).payload
    }

    static CustomAccountDetails buildCustomAccountDetails() {
        CustomAccountDetails cad1 = new CustomAccountDetails(accountId: TestUtils.randString(),
            authToken: TestUtils.randString())
        cad1.save(flush: true, failOnError: true)
    }

    // Mocking
    // -------

    static MockedMethod mock(Object obj, String methodName, Closure action = null) {
        new MockedMethod(obj, methodName, action)
    }
    static MockedMethod forceMock(Object obj, String methodName, Closure action = null) {
        try {
            new MockedMethod(obj, methodName, action, true)
        }
        catch (IllegalArgumentException e) {
            log.info("TestUtils.forceMock: ${e.message}")
        }
    }

    static ByteArrayOutputStream captureAllStreamsReturnStdOut() {
        OUTPUT_CAPTOR.capture().first
    }
    static ByteArrayOutputStream captureAllStreamsReturnStdErr() {
        OUTPUT_CAPTOR.capture().second
    }
    static void restoreAllStreams() {
        OUTPUT_CAPTOR.restore()
    }
}
