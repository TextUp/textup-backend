package org.textup.media

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import java.awt.image.BufferedImage
import javax.imageio.*
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class ImageUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    void "test ability to compress various mime types"() {
        expect:
        ImageUtils.canCompress(MediaType.IMAGE_JPEG) == true
        ImageUtils.canCompress(MediaType.IMAGE_PNG) == false
        ImageUtils.canCompress(MediaType.IMAGE_GIF) == true
        ImageUtils.canCompress(null) == false
    }

    void "test getting writers for valid image types WITHOUT exception catching"() {
        expect:
        ImageUtils.getWriter(type) != null

        where:
        type                 | _
        MediaType.IMAGE_JPEG | _
        MediaType.IMAGE_PNG  | _
        MediaType.IMAGE_GIF  | _
    }

    void "test converting byte data to a BufferedImage WITHOUT exception catching"() {
        given:
        byte[] jpegTest = TestUtils.getJpegSampleData512()
        byte[] pngTest = TestUtils.getPngSampleData()
        assert jpegTest != null
        assert pngTest != null

        expect: "valid image input data to return a BufferedImage"
        ImageUtils.tryGetImageFromData(jpegTest) instanceof BufferedImage
        ImageUtils.tryGetImageFromData(pngTest) instanceof BufferedImage

        and: "invalid image input data to return null"
        ImageUtils.tryGetImageFromData("not valid image data".bytes) == null
    }

    void "test converting Image to BufferedImage WITHOUT exception catching"() {
        given: "an image"
        BufferedImage bImg = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)

        expect:
        ImageUtils.imageToBufferedImage(bImg) instanceof BufferedImage
    }

    // jpeg size fluctuates on decoding + encoding due to stripping of metadata and rounding errors
    // during the forward and reverse processes. See https://stackoverflow.com/a/29274870
    // The only want to ensure the exact same byte data is to copy byte by byte
    void "test repeated encoding + decoding jpeg WITHOUT exception catching"() {
        given: "encode our initial jpeg image from byte data"
        ImageWriter writer = ImageUtils.getWriter(MediaType.IMAGE_JPEG)
        byte[] initialData = TestUtils.getJpegSampleData512()
        BufferedImage jpgImg = ImageUtils.tryGetImageFromData(initialData)
        assert jpgImg != null
        ImageWriteParam param1 = writer.defaultWriteParam

        when: "convert with default params (compresssion cannot be turned off for jpeg)"
        byte[] convert1 = ImageUtils.getDataFromImage(jpgImg, writer, param1)

        then: "compression cannot be turned off + jpeg encoding/decoding has size fluctuations"
        initialData.length > convert1.length

        when: "convert a second time"
        BufferedImage jpgImg2 = ImageUtils.tryGetImageFromData(convert1)
        byte[] convert2 = ImageUtils.getDataFromImage(jpgImg2, writer, param1)

        then: "similar size to first conversion within a wide band"
        initialData.length > convert2.length
        Math.abs(convert2.length - convert1.length) < 10000

        when: "convert a third time"
        BufferedImage jpgImg3 = ImageUtils.tryGetImageFromData(convert2)
        byte[] convert3 = ImageUtils.getDataFromImage(jpgImg3, writer, param1)

        then: "similar size to first two conversions within a wide band"
        initialData.length > convert3.length
        Math.abs(convert3.length - convert1.length) < 10000
        Math.abs(convert3.length - convert2.length) < 10000
    }

    void "test decoding jpeg with compression"() {
        given: "encode our initial jpeg image from byte data"
        ImageWriter writer = ImageUtils.getWriter(MediaType.IMAGE_JPEG)
        byte[] initialData = TestUtils.getJpegSampleData512()
        BufferedImage jpgImg = ImageUtils.tryGetImageFromData(initialData)
        assert jpgImg != null

        when: "convert with no compression (cannot turn compression off for jpeg)"
        ImageWriteParam param1 = writer.defaultWriteParam
        param1.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
        param1.setCompressionQuality(1.0f)
        byte[] convert1 = ImageUtils.getDataFromImage(jpgImg, writer, param1)

        then:
        convert1 != null
        initialData.length > convert1.length

        when: "convert with compression params"
        ImageWriteParam param2 = ImageUtils.tryGetCompressionParamsForWriter(writer, 0.5f)
        byte[] convert2 = ImageUtils.getDataFromImage(jpgImg, writer, param2)

        then: "byte result has a smaller size"
        convert2 != null
        convert1.length > convert2.length
    }

    void "test trying to compress png results in exception"() {
        given: "encode our initial jpeg image from byte data"
        ImageWriter writer = ImageUtils.getWriter(MediaType.IMAGE_PNG)
        byte[] initialData = TestUtils.getPngSampleData()
        BufferedImage pngImg = ImageUtils.tryGetImageFromData(initialData)
        assert pngImg != null

        when: "try to convert png witout compression"
        ImageWriteParam param1 = writer.defaultWriteParam
        byte[] convert1 = ImageUtils.getDataFromImage(pngImg, writer, param1)

        then: "ok, but decoded byte data will not be same as the original"
        convert1 != null
        initialData.length != convert1.length

        when: "when we try to convert png with compression"
        ImageWriteParam param2 = ImageUtils.tryGetCompressionParamsForWriter(writer, 0.5f)
        byte[] convert2 = ImageUtils.getDataFromImage(pngImg, writer, param2)

        then: "compression not supported at all for png"
        thrown UnsupportedOperationException
    }

    void "test trying to compress gif requires setting compression type"() {
        given: "encode our initial jpeg image from byte data"
        ImageWriter writer = ImageUtils.getWriter(MediaType.IMAGE_GIF)
        byte[] initialData = TestUtils.getGifSampleData()
        BufferedImage gifImg = ImageUtils.tryGetImageFromData(initialData)
        assert gifImg != null

        when: "try to convert png witout compression"
        ImageWriteParam param1 = writer.defaultWriteParam
        byte[] convert1 = ImageUtils.getDataFromImage(gifImg, writer, param1)

        then: "ok, but decoded byte data will not be same as the original"
        convert1 != null
        initialData.length != convert1.length

        when: "when we try to convert png with compression"
        ImageWriteParam param2 = ImageUtils.tryGetCompressionParamsForWriter(writer, 0.1f)
        byte[] convert2 = ImageUtils.getDataFromImage(gifImg, writer, param2)

        then: "can be compressed, even if the compressed size is the same as the non-compressed"
        convert2 != null
    }

    @Unroll
    void "test resizing width for #type"() {
        given: "obj with data"
        IOCUtils.metaClass."static".getResultFactory = TestUtils.getResultFactory(grailsApplication)
        byte[] inputData1 = TestUtils.getSampleDataForMimeType(type)
        BufferedImage image1 = ImageUtils.tryGetImageFromData(inputData1)

        when: "resize to a zero width"
        Result<Tuple<byte[], BufferedImage>> res = ImageUtils
            .tryResizeToWidth(type, inputData1, image1, 0)

        then: "short circuit -- return inputs as is"
        res.status == ResultStatus.OK
        res.payload.first == inputData1
        res.payload.second == image1

        when: "resize to a negative width"
        res = ImageUtils.tryResizeToWidth(type, inputData1, image1, -1)

        then: "short circuit -- return inputs as is"
        res.status == ResultStatus.OK
        res.payload.first == inputData1
        res.payload.second == image1

        when: "resize to a width larger than current width"
        res = ImageUtils.tryResizeToWidth(type, inputData1, image1, image1.width * 2)

        then: "short circuit -- return inputs as is"
        res.status == ResultStatus.OK
        res.payload.first == inputData1
        res.payload.second == image1

        when: "resize to a positive width"
        float aspectRatio = 0.8
        int targetWidth = Math.floor(image1.width * aspectRatio)
        int originalHeight = image1.height
        res = ImageUtils.tryResizeToWidth(type, inputData1, image1, targetWidth)

        then: "successfully do so while preserving the aspect ratio"
        res.status == ResultStatus.OK
        res.payload.first != inputData1
        res.payload.second != image1
        targetWidth == res.payload.second.width
        Math.floor(originalHeight * aspectRatio) == res.payload.second.height

        where:
        type                 | _
        MediaType.IMAGE_PNG  | _
        MediaType.IMAGE_JPEG | _
        MediaType.IMAGE_GIF  | _
    }

    void "test compression short circuiting"() {
        given: "obj representing a PNG image (not compressible)"
        IOCUtils.metaClass."static".getResultFactory = TestUtils.getResultFactory(grailsApplication)
        byte[] inputData1 = TestUtils.getPngSampleData()
        BufferedImage image1 = ImageUtils.tryGetImageFromData(inputData1)

        when: "compress to a zero size"
        Result<Tuple<byte[], BufferedImage>> res = ImageUtils.tryCompress(MediaType.IMAGE_PNG,
            inputData1, image1, 0l)

        then: "short circuit -- return inputs as-is"
        res.status == ResultStatus.OK
        res.payload.first == inputData1
        res.payload.second == image1

        when: "compress to a negative size"
        res = ImageUtils.tryCompress(MediaType.IMAGE_PNG, inputData1, image1, -1l)

        then: "short circuit -- return inputs as-is"
        res.status == ResultStatus.OK
        res.payload.first == inputData1
        res.payload.second == image1
    }

    @Unroll
    void "test compression for #type"() {
        given: "obj with compressible data"
        IOCUtils.metaClass."static".getResultFactory = TestUtils.getResultFactory(grailsApplication)
        byte[] inputData1 = TestUtils.getSampleDataForMimeType(type)
        BufferedImage image1 = ImageUtils.tryGetImageFromData(inputData1)

        when: "compress to impossibly small size"
        long targetSize = 1
        Result<Tuple<byte[], BufferedImage>> res = ImageUtils
            .tryCompress(type, inputData1, image1, targetSize)

        then: "short circuit before hitting file size b/c of min quality standards"
        res.status == ResultStatus.OK
        if (ImageUtils.canCompress(type)) {
            assert res.payload.first.length < inputData1.length
            assert res.payload.first.length > targetSize // impossibly small threshold
        }
        else { assert res.payload.first.length == inputData1.length }

        when: "compress to more reasonable size"
        targetSize = inputData1.length * 0.8
        res = ImageUtils.tryCompress(type, inputData1, image1, targetSize)

        then: "successfully compress to be smaller than the max size threshold"
        res.status == ResultStatus.OK
        if (ImageUtils.canCompress(type)) {
            assert res.payload.first.length < inputData1.length
            assert res.payload.first.length <= targetSize
        }
        else { assert res.payload.first.length == inputData1.length }

        where:
        type                 | _
        MediaType.IMAGE_PNG  | _
        MediaType.IMAGE_JPEG | _
        MediaType.IMAGE_GIF  | _
    }
}
