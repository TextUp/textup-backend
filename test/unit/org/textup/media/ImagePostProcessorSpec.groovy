package org.textup.media

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class ImagePostProcessorSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    @Unroll
    void "test creating initial version for #type"() {
        given:
        byte[] data = TestHelpers.getSampleDataForMimeType(type)
        ImagePostProcessor processor = new ImagePostProcessor(type, data)

        when:
        Result<UploadItem> res = processor.createInitialVersion()

        then:
        res.status == ResultStatus.OK
        res.payload instanceof UploadItem
        res.payload.type == type
        res.payload.data == data
        res.payload.isPublic == false

        where:
        type                 | _
        MediaType.IMAGE_PNG  | _
        MediaType.IMAGE_JPEG | _
        MediaType.IMAGE_GIF  | _
    }

    @Unroll
    void "test creating send version for #type"() {
        given:
        byte[] data = TestHelpers.getSampleDataForMimeType(type)
        ImagePostProcessor processor = new ImagePostProcessor(type, data)

        when: "pass in content type and data"
        Result<UploadItem> res = processor.createSendVersion()

        then: "create send version with appropriate width and file size"
        res.status == ResultStatus.OK
        res.payload instanceof UploadItem
        res.payload.type == type
        res.payload.widthInPixels <= ImagePostProcessor.SEND_VERSION.maxWidthInPixels
        res.payload.sizeInBytes < data.length
        res.payload.heightInPixels != null
        res.payload.isPublic == false

        where:
        type                 | _
        MediaType.IMAGE_PNG  | _
        MediaType.IMAGE_JPEG | _
        MediaType.IMAGE_GIF  | _
    }

    @Unroll
    void "test creating display versions for #type"() {
        given:
        byte[] data = TestHelpers.getSampleDataForMimeType(type)
        ImagePostProcessor processor = new ImagePostProcessor(type, data)

        when:
        ResultGroup<UploadItem> resGroup = processor.createAlternateVersions()

        then:
        resGroup.successStatus == ResultStatus.OK
        resGroup.successes.size() == 3
        ImagePostProcessor.ALT_VERSIONS.every { ImagePostProcessor.ImageData altVersion ->
            resGroup.payload
                .findAll { it.widthInPixels <= altVersion.maxWidthInPixels }
                .size() > 0
        }
        resGroup.payload.every { it.type == type }
        resGroup.payload.every { it.widthInPixels != null }
        resGroup.payload.every { it.heightInPixels != null }
        resGroup.payload.every { it.sizeInBytes != null }
        resGroup.payload.every { it.isPublic == false }

        where:
        type                 | _
        MediaType.IMAGE_PNG  | _
        MediaType.IMAGE_JPEG | _
        MediaType.IMAGE_GIF  | _
    }
}
