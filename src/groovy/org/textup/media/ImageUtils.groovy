package org.textup.media

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import java.awt.Graphics2D
import java.awt.Image
import java.awt.image.BufferedImage
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.imageio.stream.ImageOutputStream
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
@Log4j
class ImageUtils {

    static boolean canCompress(MediaType type) {
        switch (type) {
            case MediaType.IMAGE_JPEG: return true
            case MediaType.IMAGE_PNG: return false
            case MediaType.IMAGE_GIF: return true
            default: return false
        }
    }

    // Note: returns null if no image readers able to process the provided byte data
    static BufferedImage tryGetImageFromData(byte[] data) {
        try {
            new ByteArrayInputStream(data).withStream { InputStream bInStream ->
                ImageIO.read(bInStream)
            }
        }
        catch (Throwable e) {
            log.error("ImageUtils.tryGetImageFromData: ${e}, ${e.message}")
            null
        }
    }

    static Result<Tuple<byte[], BufferedImage>> tryResizeToWidth(MediaType type, byte[] data,
        BufferedImage image, int maxWidthInPixels) {

        // short circuit if target width is invalid or if present width is already smaller than target width
        if (maxWidthInPixels <= 0 || image?.width < maxWidthInPixels) {
            return Helpers.resultFactory.success(data, image)
        }
        ImageWriter writer
        try {
            writer = ImageUtils.getWriter(type)
            // set height to -1 to keep aspect ratio
            Image resizedImg = image.getScaledInstance(maxWidthInPixels, -1, Image.SCALE_DEFAULT)
            BufferedImage newImage = ImageUtils.imageToBufferedImage(resizedImg)
            byte[] newData = ImageUtils.getDataFromImage(newImage, writer, writer.defaultWriteParam)
            Helpers.resultFactory.success(newData, newImage)
        }
        catch (Throwable e) {
            log.error("ImageUtils.tryResizeToWidth: maxWidthInPixels: ${maxWidthInPixels}, ${e.message}")
            Helpers.resultFactory.failWithThrowable(e)
        }
        finally { writer?.dispose() }
    }

    protected Result<Tuple<byte[], BufferedImage>> tryCompress(MediaType type, byte[] data,
        BufferedImage image, long maxSizeInBytes) {
        // short circuit if invalid input or incompressible or if requested size is smaller than current
        if (maxSizeInBytes <= 0 || !ImageUtils.canCompress(type) || !data || data.size() <= maxSizeInBytes) {
            return Helpers.resultFactory.success(data, image)
        }
        ImageWriter writer
        try {
            writer = ImageUtils.getWriter(type)
            float currentQuality = 0.9,
                qualityStep = 0.1,
                minQuality = 0.5
            byte[] currData = data
            BufferedImage currImage = image
            while (currImage && currData.length > maxSizeInBytes && currentQuality > minQuality) {
                // step 1: set compression parameters
                ImageWriteParam param = ImageUtils.tryGetCompressionParamsForWriter(writer, currentQuality)
                // step 2: set up appropriate streams to collect the writer's output
                currData = ImageUtils.getDataFromImage(currImage, writer, param)
                currImage = ImageUtils.tryGetImageFromData(currData)
                currentQuality -= qualityStep
            }
            // step 3: after breaking out of the loop, return the newly-compressed values
            return Helpers.resultFactory.success(currData, currImage)
        }
        catch (Throwable e) {
            log.error("ImageUtils.tryCompress: maxSizeInBytes: ${maxSizeInBytes}: ${e.message}")
            Helpers.resultFactory.failWithThrowable(e)
        }
        finally { writer?.dispose() }
    }

    // Helpers
    // -------

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
