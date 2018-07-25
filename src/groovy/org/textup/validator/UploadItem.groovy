package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import java.awt.Graphics2D
import java.awt.Image
import java.awt.image.BufferedImage
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.imageio.stream.ImageOutputStream
import org.apache.commons.codec.binary.Base64
import org.textup.type.MediaType

// [FUTURE] will need to extend this class to support audio. Currently only supports images

@GrailsCompileStatic
@EqualsAndHashCode
@ToString
@Validateable
class UploadItem {

    MediaVersion version
    String mimeType
    String key = UUID.randomUUID().toString()
    byte[] data

    private BufferedImage _image

    static constraints = { // default nullable: false
        mimeType blank:false, validator: { String mimeType ->
            if (!MediaType.isValidMimeType(mimeType)) {
                ["invalidType"]
            }
        }
    }

    // Methods
    // -------

    Result<UploadItem> tryResizeToWidth(int maxWidthInPixels) {
        if (!validate()) { return Helpers.resultFactory.failWithValidationErrors(errors, false) }
        ImageWriter writer
        try {
            writer = getWriter(mimeType)
            // set height to -1 to keep aspect ratio
            Image resizedImg = _image.getScaledInstance(maxWidthInPixels, -1, Image.SCALE_DEFAULT)
            _image = imageToBufferedImage(resizedImg)
            data = getDataFromImage(_image, writer, writer.defaultWriteParam)
            Helpers.resultFactory.success(this)
        }
        catch (Throwable e) {
            log.debug("Uploaditem.tryResizeToWidth: maxWidthInPixels: ${maxWidthInPixels}: ${e.message}")
            Helpers.resultFactory.failWithThrowable(e, false)
        }
        finally { writer?.dispose() }
    }
    Result<UploadItem> tryCompress(long maxSizeInBytes) {
        if (!validate()) { return Helpers.resultFactory.failWithValidationErrors(errors, false) }
        ImageWriter writer
        try {
            writer = getWriter(mimeType)
            float currentQuality = 0.9,
                qualityStep = 0.1,
                minQuality = 0.5
            byte[] currData = data
            BufferedImage currImage = _image
            while (currData.length > maxSizeInBytes && currentQuality > minQuality) {
                // step 1: set compression parameters
                ImageWriteParam param = writer.defaultWriteParam
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
                param.setCompressionQuality(currentQuality)
                // step 2: set up appropriate streams to collect the writer's output
                currData = getDataFromImage(bImg, writer, param)
                currImage = getImageFromData(currData)
                currentQuality -= qualityStep
            }
            // step 3: after breaking out of the loop, store the newly-compressed values and return
            data = currData
            _image = currImage
            Helpers.resultFactory.success(this)
        }
        catch (Throwable e) {
            log.debug("Uploaditem.tryCompress: maxSizeInBytes: ${maxSizeInBytes}: ${e.message}")
            Helpers.resultFactory.failWithThrowable(e, false)
        }
        finally { writer?.dispose() }
    }

    // Property access
    // ---------------

    MediaType getType() { MediaType.convertMimeType(this.mimeType) }

    Integer getWidthInPixels() { _image?.width }

    Long getSizeInBytes() { data?.length }

    void setData(byte[] newData) {
        if (newData) {
            data = newData
            _image = getImageFromData(newData)
        }
    }

    // Helpers
    // -------

    protected static ImageWriter getWriter(String mType) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType(mType)
        ImageWriter writer = writers.next()
    }

    protected static BufferedImage getImageFromData(byte[] data) {
        new ByteArrayInputStream(newData).withCloseable { ByteArrayInputStream bInStream ->
            ImageIO.read(bInStream)
        }
    }

    protected static BufferedImage imageToBufferedImage(Image img) {
        // step 1: convert java.awt.Image to java.awt.image.BufferedImage
        // From https://stackoverflow.com/a/13605411
        BufferedImge bImg = new BufferedImage(img.getWidth(null), img.getHeight(null),
            BufferedImage.TYPE_INT_ARGB)
        // step 2: draw the Image onto the BufferedImage
        Graphics2D graphics = bImg.createGraphics()
        graphics.drawImage(img, 0, 0, null)
        graphics.dispose()
        // step 3: return buffered image
        bImg
    }

    protected static byte[] getDataFromImage(BufferedImage bImg, ImageWriter writer,
        ImageWriteParam param) {

        new ByteArrayOutputStream().withCloseable { ByteArrayOutputStream bOutStream ->
            ImageIO.createImageOutputStream(bOutStream)
                .withCloseable { ImageOutputStream iOutStream ->
                    writer.setOutput(iOutStream)
                    writer.write(null, new IIOImage(bImg, null, null), param)
                }
            bOutStream.toByteArray()
        }
    }
}
