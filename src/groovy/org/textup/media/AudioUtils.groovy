package org.textup.media

import grails.compiler.GrailsTypeChecked
import java.nio.file.*
import org.apache.commons.lang3.tuple.Pair
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
class AudioUtils {

    private final Path _executable
    private final Path _tempDirectory

    AudioUtils(String executableDirectory, String executableName, String tempDirectory)
        throws IllegalArgumentException {
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
            log.error("AudioUtils.close: ${path}, ${e}, ${e.message}")
        }
    }

    Result<byte[]> convert(long timeout, MediaType inputType, Path inputPath, MediaType outputType) {
        Path outputPath = createTempFile()
        StringBuilder sOut = new StringBuilder(),
            sErr = new StringBuilder()
        Process process = buildCommand(inputType, inputPath, outputType, outputPath).execute()
        process.consumeProcessOutput(sOut, sErr)
        process.waitForOrKill(timeout)
        if (process.exitValue() == 0) {
            readAllBytes(outputPath).then { byte[] newData ->
                delete(outputPath)
                Helpers.resultFactory.success(newData)
            }
        }
        else {
            log.error("AudioUtils.convertData: ${process.exitValue()} \nout> $sOut \nerr> $sErr ")
            Helpers.resultFactory.failWithCodeAndStatus("audioUtils.convert.failed",
                ResultStatus.INTERNAL_SERVER_ERROR, [inputType, outputType])
        }
    }

    // Helpers
    // -------

    protected Result<byte[]> readAllBytes(Path path) {
        try {
            Helpers.resultFactory.success(Files.readAllBytes(path))
        }
        catch(Throwable e) {
            log.error("AudioUtils.readAllBytes: ${path}, ${e}, ${e.message}")
            Helpers.resultFactory.failWithThrowable(e)
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

    protected List<String> formatInput(MediaType type, Path path) {
        [getOptionsFromType(type), "-i ${path.toAbsolutePath().toString()}"]
    }

    protected String formatOutput(MediaType type, Path path) {
        [getOptionsFromType(type), "-b:a 96k", path.toAbsolutePath().toString()]
    }

    protected String getOptionsFromType(MediaType type) {
        switch (type) {
            case MediaType.AUDIO_MP3:
                return "-c libmp3lame -f mp3"
            case MediaType.AUDIO_OGG_VORBIS:
                return "-c libvorbis -f ogg"
            case MediaType.AUDIO_OGG_OPUS:
                return "-c libopus -f ogg"
            case MediaType.AUDIO_WEBM_VORBIS:
                return "-c libvorbis -f webm"
            case MediaType.AUDIO_WEBM_OPUS:
                return "-c libopus -f webm"
            default:
                return ""
        }
    }
}
