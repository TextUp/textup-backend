package org.textup.media

import org.textup.test.*
import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import java.nio.file.*
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class AudioUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
    }

    def cleanup() {
        TestUtils.clearTempDirectory()
    }

    void "test constructor"() {
        given:
        String executableDirectory = TestUtils.config.textup.media.audio.executableDirectory
        String executableName = TestUtils.config.textup.media.audio.executableName
        String tempDirectory = TestUtils.config.textup.tempDirectory

        when: "empty"
        AudioUtils audioUtils = new AudioUtils(null, null, null)

        then:
        thrown IllegalArgumentException

        when: "invalid executable inputs"
        audioUtils = new AudioUtils("invalid", "invalid", tempDirectory)

        then:
        thrown IllegalArgumentException

        when: "invalid temp directory inputs"
        audioUtils = new AudioUtils(executableDirectory, executableName, "invalid")

        then:
        thrown IllegalArgumentException

        when: "all valid"
        audioUtils = new AudioUtils(executableDirectory, executableName, tempDirectory)

        then:
        notThrown IllegalArgumentException
    }

    void "test storing data in temp file"() {
        given: "valid audio util object"
        int tmpBaseline = TestUtils.numInTempDirectory
        AudioUtils audioUtils = TestUtils.getAudioUtils()

        when: "no data"
        Path path = audioUtils.createTempFile()

        then:
        path != null
        TestUtils.numInTempDirectory == tmpBaseline + 1

        when: "has data"
        path = audioUtils.createTempFile("abc".bytes)

        then:
        path != null
        TestUtils.numInTempDirectory == tmpBaseline + 2
    }

    void "test deleting file at the provided path"() {
        given: "valid audio util object"
        int tmpBaseline = TestUtils.numInTempDirectory
        AudioUtils audioUtils = TestUtils.getAudioUtils()
        Path path = audioUtils.createTempFile()
        assert TestUtils.numInTempDirectory == tmpBaseline + 1

        when: "invalid path"
        audioUtils.delete(Paths.get("invalid"))

        then:
        TestUtils.numInTempDirectory == tmpBaseline + 1

        when: "valid path"
        audioUtils.delete(path)

        then:
        TestUtils.numInTempDirectory == tmpBaseline + 0
    }

    void "test reading all bytes from file at provided path"() {
        given: "valid audio util object"
        AudioUtils audioUtils = TestUtils.getAudioUtils()
        byte[] data = "abc".bytes
        Path path = audioUtils.createTempFile(data)

        when: "invalid path"
        Result<byte[]> res = audioUtils.readAllBytes(Paths.get("invalid"))

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR

        when: "valid path"
        res = audioUtils.readAllBytes(path)

        then:
        res.status == ResultStatus.OK
        res.payload == data
    }

    void "test getting executing command string"() {
        given: "valid audio util object"
        AudioUtils audioUtils = TestUtils.getAudioUtils()

        when:
        String command = audioUtils.command

        then:
        command.contains(audioUtils._executable.toString())
        command.contains("-y")
        command.contains("-nostdin")
    }

    void "test getting executable options from mime type"() {
        given: "valid audio util object"
        AudioUtils audioUtils = TestUtils.getAudioUtils()

        expect: "non-audio types return an empty string"
        audioUtils.getCodecFromType(null) == ""
        audioUtils.getFormatFromType(null) == ""
        audioUtils.getCodecFromType(MediaType.IMAGE_JPEG) == ""
        audioUtils.getFormatFromType(MediaType.IMAGE_JPEG) == ""

        and: "audio types return appropriate options"
        audioUtils.getCodecFromType(MediaType.AUDIO_MP3).contains "libmp3lame"
        audioUtils.getFormatFromType(MediaType.AUDIO_MP3).contains "mp3"
        audioUtils.getCodecFromType(MediaType.AUDIO_OGG_VORBIS).contains "libvorbis"
        audioUtils.getFormatFromType(MediaType.AUDIO_OGG_VORBIS).contains "ogg"
        audioUtils.getCodecFromType(MediaType.AUDIO_OGG_OPUS).contains "libopus"
        audioUtils.getFormatFromType(MediaType.AUDIO_OGG_OPUS).contains "ogg"
        audioUtils.getCodecFromType(MediaType.AUDIO_WEBM_VORBIS).contains "libvorbis"
        audioUtils.getFormatFromType(MediaType.AUDIO_WEBM_VORBIS).contains "webm"
        audioUtils.getCodecFromType(MediaType.AUDIO_WEBM_OPUS).contains "libopus"
        audioUtils.getFormatFromType(MediaType.AUDIO_WEBM_OPUS).contains "webm"
    }

    void "test building input string for executable"() {
        given: "valid audio util object"
        AudioUtils audioUtils = TestUtils.getAudioUtils()
        Path path = Paths.get("testing")

        when: "input validation happens in public-facing method"
        List<String> args = audioUtils.formatInput(MediaType.IMAGE_JPEG, path)

        then:
        args[0].contains(path.toAbsolutePath().toString())

        when:
        args = audioUtils.formatInput(MediaType.AUDIO_MP3, path)

        then:
        args[0].contains(path.toAbsolutePath().toString())
    }

    void "test building output string for executable"() {
        given: "valid audio util object"
        AudioUtils audioUtils = TestUtils.getAudioUtils()
        Path path = Paths.get("testing")

        when: "input validation happens in public-facing method"
        List<String> args = audioUtils.formatOutput(MediaType.IMAGE_JPEG, path)

        then:
        args[0] == ""
        args[1] == ""
        args[2].contains("96k")
        args[3].contains(path.toAbsolutePath().toString())

        when:
        args = audioUtils.formatOutput(MediaType.AUDIO_MP3, path)

        then:
        args[0].contains("mp3")
        args[1].contains("libmp3lame")
        args[2].contains("96k")
        args[3].contains(path.toAbsolutePath().toString())
    }

    void "test converting handling invalid inputs"() {
        given: "valid audio util object"
        AudioUtils audioUtils = TestUtils.getAudioUtils()
        Path tempFile = audioUtils.createTempFile()
        Path invalidFile = Paths.get("invalid")

        when: "no inputs"
        Result<byte[]> res = audioUtils.convert(0l, null, null, null)

        then:
        res.status == ResultStatus.BAD_REQUEST

        when: "negative timeout"
        res = audioUtils.convert(-2, MediaType.AUDIO_MP3, tempFile, MediaType.AUDIO_MP3)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "audioUtils.convert.timeoutIsNegative"

        when: "input type is not an audio type"
        res = audioUtils.convert(10, MediaType.IMAGE_JPEG, tempFile, MediaType.AUDIO_MP3)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "audioUtils.convert.typesMustBeAudio"

        when: "input path does not exist or is not readable"
        res = audioUtils.convert(10, MediaType.AUDIO_MP3, invalidFile, MediaType.AUDIO_MP3)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "audioUtils.convert.inputPathInvalid"

        when: "output type is not an audio type"
        res = audioUtils.convert(10, MediaType.AUDIO_MP3, tempFile, MediaType.IMAGE_JPEG)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "audioUtils.convert.typesMustBeAudio"
    }

    @Unroll
    void "test converting #inputType to #outputType"() {
        given: "valid audio util object"
        AudioUtils audioUtils = TestUtils.getAudioUtils()
        byte[] inputData = TestUtils.getSampleDataForMimeType(inputType)
        Path inputPath = audioUtils.createTempFile(inputData)
        long timeout = AudioPostProcessor.CONVERSION_TIMEOUT_IN_MILLIS

        when:
        int numTempFiles = TestUtils.numInTempDirectory
        Result<byte[]> res = audioUtils.convert(timeout, inputType, inputPath, outputType)

        then: "converted and any created temp files are cleaned up"
        res.status == ResultStatus.OK
        res.payload instanceof byte[]
        TestUtils.numInTempDirectory == numTempFiles

        where:
        inputType                   | outputType
        MediaType.AUDIO_MP3         | AudioPostProcessor.SEND_TYPE
        MediaType.AUDIO_MP3         | AudioPostProcessor.ALT_TYPE
        MediaType.AUDIO_OGG_VORBIS  | AudioPostProcessor.SEND_TYPE
        MediaType.AUDIO_OGG_VORBIS  | AudioPostProcessor.ALT_TYPE
        MediaType.AUDIO_OGG_OPUS    | AudioPostProcessor.SEND_TYPE
        MediaType.AUDIO_OGG_OPUS    | AudioPostProcessor.ALT_TYPE
        MediaType.AUDIO_WEBM_VORBIS | AudioPostProcessor.SEND_TYPE
        MediaType.AUDIO_WEBM_VORBIS | AudioPostProcessor.ALT_TYPE
        MediaType.AUDIO_WEBM_OPUS   | AudioPostProcessor.SEND_TYPE
        MediaType.AUDIO_WEBM_OPUS   | AudioPostProcessor.ALT_TYPE
    }
}
