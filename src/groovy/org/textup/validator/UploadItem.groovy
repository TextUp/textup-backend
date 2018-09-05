package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import java.awt.Graphics2D
import java.awt.Image
import java.awt.image.BufferedImage
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.imageio.stream.ImageOutputStream
import org.apache.commons.codec.binary.Base64
import org.textup.type.*
import org.textup.*

// [FUTURE] will need to extend this class to support audio. Currently only supports images

@EqualsAndHashCode
@GrailsTypeChecked
@Validateable
class UploadItem {

    MediaVersion mediaVersion
    MediaType type
    String key = UUID.randomUUID().toString()
    byte[] data

    private BufferedImage _image

    static constraints = { // default nullable: false
    }

    // Methods
    // -------

    Result<UploadItem> tryResizeToWidth(int maxWidthInPixels) {
        if (!validate()) {
            return Helpers.resultFactory.failWithValidationErrors(errors)
        }
        if (maxWidthInPixels <= 0) {
            return Helpers.resultFactory.failWithCodeAndStatus(
                "uploadItem.tryResizeToWidth.invalidWidth", ResultStatus.BAD_REQUEST)
        }
        ImageWriter writer
        try {
            writer = UploadItem.getWriter(type)
            // set height to -1 to keep aspect ratio
            Image resizedImg = _image.getScaledInstance(maxWidthInPixels, -1, Image.SCALE_DEFAULT)
            _image = UploadItem.imageToBufferedImage(resizedImg)
            data = UploadItem.getDataFromImage(_image, writer, writer.defaultWriteParam)
            Helpers.resultFactory.success(this)
        }
        catch (Throwable e) {
            log.debug("UploadItem.tryResizeToWidth: maxWidthInPixels: ${maxWidthInPixels}: ${e.message}")
            Helpers.resultFactory.failWithThrowable(e)
        }
        finally { writer?.dispose() }
    }

    Result<UploadItem> tryCompress(long maxSizeInBytes) {
        if (!validate()) {
            return Helpers.resultFactory.failWithValidationErrors(errors)
        }
        if (maxSizeInBytes <= 0) {
            return Helpers.resultFactory.failWithCodeAndStatus(
                "uploadItem.tryCompress.invalidSize", ResultStatus.BAD_REQUEST)
        }
        // short circuit if incompressible or if requested size is smaller than current
        if (!UploadItem.canCompress(type) || getSizeInBytes() <= maxSizeInBytes) {
            return Helpers.resultFactory.success(this)
        }
        ImageWriter writer
        try {
            writer = UploadItem.getWriter(type)
            float currentQuality = 0.9,
                qualityStep = 0.1,
                minQuality = 0.5
            byte[] currData = data
            BufferedImage currImage = _image
            while (currImage && currData.length > maxSizeInBytes && currentQuality > minQuality) {
                // step 1: set compression parameters
                ImageWriteParam param = UploadItem.tryGetCompressionParamsForWriter(writer, currentQuality)
                // step 2: set up appropriate streams to collect the writer's output
                currData = UploadItem.getDataFromImage(currImage, writer, param)
                currImage = UploadItem.tryGetImageFromData(currData)
                currentQuality -= qualityStep
            }
            // step 3: after breaking out of the loop, store the newly-compressed values and return
            data = currData // NOTE: this sets the property and doesn't trigger `setData`
            _image = currImage
            Helpers.resultFactory.success(this)
        }
        catch (Throwable e) {
            log.debug("UploadItem.tryCompress: maxSizeInBytes: ${maxSizeInBytes}: ${e.message}")
            Helpers.resultFactory.failWithThrowable(e)
        }
        finally { writer?.dispose() }
    }

    // Property access
    // ---------------

    int getWidthInPixels() { _image?.width ?: 0 } // so not null

    int getHeightInPixels() { _image?.height ?: 0 } // so not null

    long getSizeInBytes() { data?.length ?: 0 } // so not null

    void setData(byte[] newData) {
        if (newData) {
            data = newData
            _image = UploadItem.tryGetImageFromData(newData)
        }
    }

    // Helpers
    // -------

    protected static boolean canCompress(MediaType type) {
        switch (type) {
            case MediaType.IMAGE_JPEG: return true
            case MediaType.IMAGE_PNG: return false
            case MediaType.IMAGE_GIF: return true
            default: return false
        }
    }

    protected static ImageWriteParam tryGetCompressionParamsForWriter(ImageWriter writer, float quality) {
        ImageWriteParam param = writer.defaultWriteParam
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
        param.setCompressionType(param.compressionTypes[0])
        param.setCompressionQuality(quality)
        param
    }

    protected static ImageWriter getWriter(MediaType type) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType(type?.mimeType)
        writers.hasNext() ? writers.next() : null
    }

    // Note: returns null if no image readers able to process the provided byte data
    protected static BufferedImage tryGetImageFromData(byte[] data) {
        new ByteArrayInputStream(data).withStream { InputStream bInStream ->
            ImageIO.read(bInStream)
        }
    }

    protected static BufferedImage imageToBufferedImage(Image img) {
        // step 1: convert java.awt.Image to java.awt.image.BufferedImage
        // From https://stackoverflow.com/a/13605411
        // NOTE that OpenJDK does not have a native JPEG encoder changing the type to
        // BufferedImage.TYPE_3BYTE_BGR seems to avoid the exception.
        // See https://stackoverflow.com/a/17845696
        BufferedImage bImg = new BufferedImage(img.getWidth(null), img.getHeight(null),
            BufferedImage.TYPE_3BYTE_BGR)
        // step 2: draw the Image onto the BufferedImage
        Graphics2D graphics = bImg.createGraphics()
        graphics.drawImage(img, 0, 0, null)
        graphics.dispose()
        // step 3: return buffered image
        bImg
    }

    protected static byte[] getDataFromImage(BufferedImage bImg, ImageWriter writer,
        ImageWriteParam param) {
        ByteArrayOutputStream byteOutStream = new ByteArrayOutputStream()
        byteOutStream.withCloseable {
            ImageOutputStream imgOutStream = ImageIO.createImageOutputStream(byteOutStream)
            imgOutStream.withCloseable {
                writer.setOutput(imgOutStream)
                writer.write(null, new IIOImage(bImg, null, null), param)
            }
            byteOutStream.toByteArray()
        }
    }
}
