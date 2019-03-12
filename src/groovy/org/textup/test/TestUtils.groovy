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
import org.joda.time.*
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

    // [NOTE] use `new JSON()` instead of `as JSON` because the case is only implemented for domain objects
    // see: https://stackoverflow.com/a/30989508
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Map objToJsonMap(Object obj) {
        JSON.use(MarshallerUtils.MARSHALLER_DEFAULT) {
            DataFormatUtils.jsonToObject(new JSON(obj).toString()) as Map
        }
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static String buildXml(Closure data) {
        StringWriter writer = new StringWriter()
        MarkupBuilder xmlBuilder = new MarkupBuilder(writer)
        xmlBuilder(data)
        writer.toString().replaceAll(/<call>|<\/call>|\s+/, "").trim()
    }

    static GPathResult buildXmlTransformOutput(GrailsApplication grailsApplication, RecordItemRequest iReq) {
        // [IMPORTANT] here are testing the XSL transform from our marshalled XML to a format
        // XSL-FO understands. Before we marshall, we need to make sure we set the phone id
        // on the request for the marshallers as we do in `pdfService`
        RequestUtils.trySet(RequestUtils.PHONE_ID, iReq.mutablePhone.id)

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
        PhoneNumber.tryCreate(TestUtils.randPhoneNumberString()).payload
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

    static String randString() { UUID.randomUUID().toString() }

    static URI randUri() { new URI("https://www.example.com/${TestUtils.randString()}") }

    static URL randUrl() { TestUtils.randUri().toURL() }

    static String randLinkString() { TestUtils.randUri().toString() }

    static String randEmail() { "${TestUtils.randString()}@textup.org" }

    static TypeMap randTypeMap() { TypeMap.create((TestUtils.randString()): TestUtils.randString()) }

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
    static void standardMockSetup() {
        MockedMethod.force(AuthUtils, "encodeSecureString") { it }
        IOCUtils.metaClass."static".getLinkGenerator = { -> TestUtils.mockLinkGenerator() }
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
        TestUtils.mockJsonToString()
    }

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
        [link: { Map m -> (m ?: [:]).toString() }] as LinkGenerator
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

    // [NOTE] may need to also mix in `ControllerUnitTestMixin` via `@TestMixin` for as JSON casting to work
    // see https://stackoverflow.com/a/15485593
    // [NOTE] use `new JSON()` instead of `as JSON` because the case is only implemented for domain objects
    // see: https://stackoverflow.com/a/30989508
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static void mockJsonToString() {
        // in unit tests, don't have custom `default` marshallers so replace with simple JSON cast
        DataFormatUtils.metaClass."static".toJsonString = { it ? new JSON(it).toString() : "" }
    }

    // Object generators
    // -----------------

    static Author buildAuthor() {
        Author.create(TestUtils.randIntegerUpTo(88, true) as Long,
            TestUtils.randString(),
            AuthorType.STAFF)
    }

    static Role buildRole() {
        new Role(authority: TestUtils.randString()).save(flush: true, failOnError: true)
    }

    static Location buildLocation() {
        Location loc1 = new Location(address: TestUtils.randString(),
            lat: TestUtils.randIntegerUpTo(90),
            lng: TestUtils.randIntegerUpTo(180))
        loc1.save(flush:true, failOnError:true)
    }

    static Organization buildOrg(OrgStatus status = OrgStatus.PENDING) {
        new Organization(name: TestUtils.randString(), location: TestUtils.buildLocation(), status: status)
            .save(flush: true, failOnError: true)
    }

    static Staff buildStaff(Organization org1 = null) {
        Staff s1 = new Staff(name: TestUtils.randString(),
            username: TestUtils.randString(),
            password: TestUtils.randString(),
            email: TestUtils.randEmail(),
            personalNumber: TestUtils.randPhoneNumber(),
            status: StaffStatus.STAFF,
            org: org1 ?: TestUtils.buildOrg())
        s1.save(flush: true, failOnError: true)
    }

    static Team buildTeam(Organization org1 = null) {
        Team t1 = Team.tryCreate(org1 ?: TestUtils.buildOrg(),
            TestUtils.randString(),
            TestUtils.buildLocation())
            .logFail("buildTeam")
            .payload as Team
        t1.save(flush: true, failOnError: true)
    }

    static OwnerPolicy buildOwnerPolicy(PhoneOwnership thisOwner = null, Staff thisStaff = null) {
        PhoneOwnership own1 = thisOwner ?: TestUtils.buildActiveStaffPhone().owner
        Staff s1 = thisStaff ?: TestUtils.buildStaff()
        OwnerPolicy op1 = OwnerPolicy.tryCreate(own1, s1.id)
            .logFail("buildOwnerPolicy")
            .payload as OwnerPolicy
        op1.save(flush: true, failOnError: true)
    }

    static Phone buildTeamPhone(Team thisTeam = null) {
        Team t1 = thisTeam ?: TestUtils.buildTeam()
        Phone p1 = Phone.tryCreate(t1.id, PhoneOwnershipType.GROUP)
            .logFail("buildTeamPhone")
            .payload as Phone
        p1.save(flush: true, failOnError: true)
    }

    static Phone buildActiveTeamPhone(Team thisTeam = null) {
        Phone p1 = TestUtils.buildTeamPhone(thisTeam)
        p1.tryActivate(TestUtils.randPhoneNumber(), TestUtils.randString())
        p1.save(flush: true, failOnError: true)
    }

    static Phone buildStaffPhone(Staff thisStaff = null) {
        Staff s1 = thisStaff ?: TestUtils.buildStaff()
        Phone p1 = Phone.tryCreate(s1.id, PhoneOwnershipType.INDIVIDUAL)
            .logFail("buildStaffPhone")
            .payload as Phone
        p1.save(flush: true, failOnError: true)
    }

    static Phone buildActiveStaffPhone(Staff thisStaff = null) {
        Phone p1 = TestUtils.buildStaffPhone(thisStaff)
        p1.tryActivate(TestUtils.randPhoneNumber(), TestUtils.randString())
        p1.save(flush: true, failOnError: true)
    }

    static IncomingSession buildSession(Phone thisPhone = null) {
        Phone p1 = thisPhone ?: TestUtils.buildActiveStaffPhone()
        IncomingSession is1 = IncomingSession.tryCreate(p1, TestUtils.randPhoneNumber())
            .logFail("buildSession")
            .payload as IncomingSession
        is1.save(flush: true, failOnError: true)
    }

    static FeaturedAnnouncement buildAnnouncement(Phone thisPhone = null) {
        Phone p1 = thisPhone ?: TestUtils.buildActiveStaffPhone()
        FeaturedAnnouncement fa1 = FeaturedAnnouncement
            .tryCreate(p1, DateTime.now().plusDays(2), TestUtils.randString())
            .logFail("buildAnnouncement")
            .payload as FeaturedAnnouncement
        fa1.save(flush: true, failOnError: true)
    }

    static AnnouncementReceipt buildAnnouncementReceipt(FeaturedAnnouncement thisAnnounce = null) {
        FeaturedAnnouncement fa1 = thisAnnounce ?: TestUtils.buildAnnouncement()
        IncomingSession is1 = TestUtils.buildSession(fa1.phone)
        AnnouncementReceipt aRpt1 = AnnouncementReceipt.tryCreate(fa1, is1, RecordItemType.CALL)
            .logFail("buildAnnouncementReceipt")
            .payload as AnnouncementReceipt
        aRpt1.save(flush: true, failOnError: true)
    }

    static PhoneRecord buildSharedPhoneRecord(PhoneRecord recToShare = null, Phone sWith = null) {
        PhoneRecord toShare = recToShare ?: TestUtils.buildIndPhoneRecord()
        Phone p1 = sWith ?: TestUtils.buildActiveStaffPhone()
        PhoneRecord pr1 = PhoneRecord.tryCreate(SharePermission.DELEGATE, toShare, p1)
            .logFail("buildSharedPhoneRecord")
            .payload as PhoneRecord
        pr1.save(flush: true, failOnError: true)
    }

    static IndividualPhoneRecord buildIndPhoneRecord(Phone thisPhone = null, boolean addNumber = true) {
        Phone p1 = thisPhone ?: TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = IndividualPhoneRecord.tryCreate(p1)
            .logFail("buildIndPhoneRecord")
            .payload as IndividualPhoneRecord
        ipr1.name = TestUtils.randString()
        if (addNumber) {
            ipr1.mergeNumber(TestUtils.randPhoneNumber(), 0)
                .logFail("buildIndPhoneRecord")
        }
        ipr1.save(flush: true, failOnError: true)
    }

    static GroupPhoneRecord buildGroupPhoneRecord(Phone thisPhone = null) {
        Phone p1 = thisPhone ?: TestUtils.buildActiveStaffPhone()
        GroupPhoneRecord gpr1 = GroupPhoneRecord.tryCreate(p1, TestUtils.randString())
            .logFail("buildGroupPhoneRecord")
            .payload as GroupPhoneRecord
        gpr1.name = TestUtils.randString()
        gpr1.save(flush: true, failOnError: true)
    }

    static MediaInfo buildMediaInfo(MediaElement el1 = null) {
        MediaInfo mInfo1 = new MediaInfo()
        if (el1) {
            mInfo1.addToMediaElements(el1)
        }
        mInfo1.save(flush: true, failOnError: true)
    }

    static MediaElement buildMediaElement(BigDecimal sendSize = 88) {
        MediaElement el1 = new MediaElement()
        el1.sendVersion = TestUtils.buildMediaElementVersion(sendSize)
        el1.save(flush: true, failOnError: true)
    }

    static UploadItem buildUploadItem(MediaType type = MediaType.AUDIO_MP3) {
        UploadItem.tryCreate(type, getSampleDataForMimeType(type)).payload
    }

    static MediaElementVersion buildMediaElementVersion(BigDecimal sendSize = 88) {
        MediaElementVersion mVers1 = new MediaElementVersion(type: MediaType.IMAGE_JPEG,
            versionId: TestUtils.randString(),
            sizeInBytes: sendSize.longValue(),
            widthInPixels: 888)
        mVers1.save(flush: true, failOnError: true)
    }

    static Record buildRecord() {
        new Record().save(flush: true, failOnError: true)
    }

    static SimpleFutureMessage buildFutureMessage(Record thisRecord = null) {
        Record rec1 = thisRecord ?: TestUtils.buildRecord()
        SimpleFutureMessage sMsg1 = SimpleFutureMessage
            .tryCreate(rec1, FutureMessageType.TEXT, TestUtils.randString(), null)
            .logFail("buildFutureMessage")
            .payload as SimpleFutureMessage
        sMsg1.save(flush: true, failOnError: true)
    }

    static RecordItem buildRecordItem(Record thisRecord = null) {
        Record rec1 = thisRecord ?: TestUtils.buildRecord()
        new RecordItem(record: rec1).save(flush: true, failOnError: true)
    }

    static RecordCall buildRecordCall(Record thisRecord = null, boolean shouldAddRpt = true) {
        Record rec1 = thisRecord ?: TestUtils.buildRecord()
        RecordCall rCall1 = RecordCall.tryCreate(rec1)
            .logFail("buildRecordCall")
            .payload as RecordCall
        if (shouldAddRpt) {
            rCall1.addReceipt(TestUtils.buildTempReceipt(ReceiptStatus.SUCCESS)) // for duration
        }
        rCall1.save(flush: true, failOnError: true)
    }

    static RecordText buildRecordText(Record thisRecord = null) {
        Record rec1 = thisRecord ?: TestUtils.buildRecord()
        RecordText rText1 = RecordText.tryCreate(rec1, TestUtils.randString())
            .logFail("buildRecordText")
            .payload as RecordText
        rText1.save(flush: true, failOnError: true)
    }

    static RecordNote buildRecordNote(Record thisRecord = null) {
        Record rec1 = thisRecord ?: TestUtils.buildRecord()
        RecordNote rNote1 = RecordNote.tryCreate(rec1,
                TestUtils.randString(),
                TestUtils.buildMediaInfo(),
                TestUtils.buildLocation())
            .logFail("buildRecordNote")
            .payload as RecordNote
        rNote1.save(flush: true, failOnError: true)
    }

    static RecordItemReceipt buildReceipt(ReceiptStatus status = ReceiptStatus.PENDING) {
        RecordItemReceipt rpt1 = new RecordItemReceipt(status: status,
            contactNumberAsString: TestUtils.randPhoneNumberString(),
            apiId: TestUtils.randString(),
            item: TestUtils.buildRecordItem())
        rpt1.save(flush: true, failOnError: true)
    }

    static TempRecordReceipt buildTempReceipt(ReceiptStatus status = ReceiptStatus.PENDING) {
        TempRecordReceipt rpt1 = TempRecordReceipt
            .tryCreate(TestUtils.randString(), TestUtils.randPhoneNumber())
            .payload
        rpt1.status = status
        rpt1.numBillable = TestUtils.randIntegerUpTo(10)
        assert rpt1.validate()
        rpt1
    }

    static CustomAccountDetails buildCustomAccountDetails() {
        CustomAccountDetails cad1 = new CustomAccountDetails(accountId: TestUtils.randString(),
            authToken: TestUtils.randString())
        cad1.save(flush: true, failOnError: true)
    }

    static Token buildToken() {
        Map data = TokenType.passwordResetData(TestUtils.randIntegerUpTo(88) as Long)
        Token tok1 = Token.tryCreate(TokenType.PASSWORD_RESET, data).payload
        tok1.save(flush: true, failOnError: true)
    }

    static Notification buildNotification(Phone thisPhone = null) {
        Phone p1 = thisPhone ?: TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(p1)

        NotificationDetail nd1 = NotificationDetail.tryCreate(ipr1.toWrapper()).payload
        NotificationDetail nd2 = NotificationDetail.tryCreate(gpr1.toWrapper()).payload

        RecordItem rItem1 = TestUtils.buildRecordItem(ipr1.record)
        nd1.items << rItem1
        RecordItem rItem2 = TestUtils.buildRecordItem(gpr1.record)
        nd2.items << rItem2

        Notification notif1 = Notification.tryCreate(p1).payload
        notif1.addDetail(nd1)
        notif1.addDetail(nd2)

        assert notif1.validate()
        notif1
    }

    static NotificationInfo buildNotificationInfo() {
        new NotificationInfo(TestUtils.randString(),
            TestUtils.randPhoneNumber(),
            TestUtils.randIntegerUpTo(88, true),
            TestUtils.randIntegerUpTo(88, true),
            TestUtils.randIntegerUpTo(88, true),
            TestUtils.randString(),
            TestUtils.randIntegerUpTo(88, true),
            TestUtils.randIntegerUpTo(88, true),
            TestUtils.randString())
    }

    static TempRecordItem buildTempRecordItem() {
        TempRecordItem
            .tryCreate(TestUtils.randString(), TestUtils.buildMediaInfo(), TestUtils.buildLocation())
            .payload
    }

    // Mocking
    // -------

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
