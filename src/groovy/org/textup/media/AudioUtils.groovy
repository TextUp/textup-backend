package org.textup.media

import grails.compiler.GrailsTypeChecked
import java.nio.file.*
import org.textup.*
import org.textup.type.*
import org.textup.util.*

@GrailsTypeChecked
class AudioUtils {

    private final Path _executable
    private final Path _tempDirectory

    AudioUtils(String executableDirectory, String executableName, String tempDirectory)
        throws IllegalArgumentException {
        if ([executableDirectory, executableName, tempDirectory].any { it == null}) {
            throw new IllegalArgumentException("Please pass in all required inputs.")
        }
        _executable = Paths.get(executableDirectory, executableName).toAbsolutePath()
        _tempDirectory = Paths.get(tempDirectory).toAbsolutePath()
        if (!Files.isExecutable(_executable)) {
            throw new IllegalArgumentException("Path to command must be executable")
        }
        if (!Files.isDirectory(_tempDirectory) || !Files.isReadable(_tempDirectory) ||
            !Files.isWritable(_tempDirectory)) {

            throw new IllegalArgumentException("Path to temp directory must be readable and writeable")
        }
    }

    Path createTempFile(byte[] data = null) {
        try {
            Path tempFile = Files.createTempFile(_tempDirectory, null, null)
            data ? Files.write(tempFile, data) : tempFile
        }
        catch (Throwable e) {
            log.error("AudioUtils.createTempFile: ${e}, ${e.message}")
            null
        }
    }

    void delete(Path path) {
        try {
            if (path) { Files.delete(path) }
        }
        catch (Throwable e) {
            log.error("AudioUtils.delete: ${path}, ${e}, ${e.message}")
        }
    }

    // Do not short circuit if input type = output type because this is the CONTAINER type.
    // We want to normalize both container AND encoding. For example, Twilio returns audio with
    // container of mp3, but the content is pcm. For this case, we need to encode to mp3 anyways
    // or else using the Play verb won’t work.
    Result<byte[]> convert(long timeout, MediaType inputType, Path inputPath, MediaType outputType) {
        if (timeout < 0) {
            return IOCUtils.resultFactory.failWithCodeAndStatus("audioUtils.convert.timeoutIsNegative",
                ResultStatus.BAD_REQUEST)
        }
        if (!MediaType.AUDIO_TYPES.contains(inputType) || !MediaType.AUDIO_TYPES.contains(outputType)) {
            return IOCUtils.resultFactory.failWithCodeAndStatus("audioUtils.convert.typesMustBeAudio",
                ResultStatus.BAD_REQUEST)
        }
        if (!Files.isReadable(inputPath)) {
            return IOCUtils.resultFactory.failWithCodeAndStatus("audioUtils.convert.inputPathInvalid",
                ResultStatus.BAD_REQUEST)
        }
        Path outputPath = createTempFile()
        StringBuilder sOut = new StringBuilder(),
            sErr = new StringBuilder()
        Process process = buildCommand(inputType, inputPath, outputType, outputPath).execute()
        process.consumeProcessOutput(sOut, sErr)
        process.waitForOrKill(timeout)
        if (process.exitValue() == 0) {
            readAllBytes(outputPath).then { byte[] newData ->
                delete(outputPath)
                IOCUtils.resultFactory.success(newData)
            }
        }
        else {
            delete(outputPath)
            log.error("AudioUtils.convert: ${process.exitValue()} \nout> $sOut \nerr> $sErr ")
            IOCUtils.resultFactory.failWithCodeAndStatus("audioUtils.convert.failed",
                ResultStatus.INTERNAL_SERVER_ERROR, [inputType, outputType])
        }
    }

    // Helpers
    // -------

    protected Result<byte[]> readAllBytes(Path path) {
        try {
            IOCUtils.resultFactory.success(Files.readAllBytes(path))
        }
        catch(Throwable e) {
            log.error("AudioUtils.readAllBytes: ${path}, ${e}, ${e.message}")
            IOCUtils.resultFactory.failWithThrowable(e)
        }
    }

    protected String buildCommand(MediaType inputType, Path inputPath, MediaType outputType, Path outputPath) {
        List<String> parts = [getCommand()]
        parts.addAll(formatInput(inputType, inputPath))
        parts.addAll(formatOutput(outputType, outputPath))
        parts.join(" ")
    }

    protected String getCommand() {
        "${_executable.toString()} -y -nostdin"
    }

    // Don't need to also include codec option for input because all audio data types we support
    // have native decoders built into ffMPEG
    protected List<String> formatInput(MediaType type, Path path) {
        ["-i ${path.toAbsolutePath().toString()}"]
    }

    protected List<String> formatOutput(MediaType type, Path path) {
        [getFormatFromType(type), getCodecFromType(type), "-b:a 96k", path.toAbsolutePath().toString()]
    }

    protected String getFormatFromType(MediaType type) {
        switch (type) {
            case MediaType.AUDIO_MP3:
                return "-f mp3"
            case MediaType.AUDIO_OGG_VORBIS:
                return "-f ogg"
            case MediaType.AUDIO_OGG_OPUS:
                return "-f ogg"
            case MediaType.AUDIO_WEBM_VORBIS:
                return "-f webm"
            case MediaType.AUDIO_WEBM_OPUS:
                return "-f webm"
            default:
                return ""
        }
    }

    protected String getCodecFromType(MediaType type) {
        switch (type) {
            case MediaType.AUDIO_MP3:
                return "-c libmp3lame"
            case MediaType.AUDIO_OGG_VORBIS:
                return "-c libvorbis"
            case MediaType.AUDIO_OGG_OPUS:
                return "-c libopus"
            case MediaType.AUDIO_WEBM_VORBIS:
                return "-c libvorbis"
            case MediaType.AUDIO_WEBM_OPUS:
                return "-c libopus"
            default:
                return ""
        }
    }
}
