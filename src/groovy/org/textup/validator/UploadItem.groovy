package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.textup.type.MediaType

@GrailsCompileStatic
@EqualsAndHashCode
@ToString
@Validateable
class UploadItem {

    MediaVersion version
    String mimeType
    String key = UUID.randomUUID().toString()
    byte[] data

    static constraints = { // default nullable: false
        mimeType blank:false, validator: { String mimeType ->
            if (!MediaType.isValidMimeType(mimeType)) {
                ["invalidType"]
            }
        }
    }

    MediaType getType() {
        MediaType.convertMimeType(this.mimeType)
    }

    Long getWidthInPixels() {

    }
    Long getSizeInBytes() {

    }

    ImageItem tryResizeToWidth(long maxWidthInPixels) {

    }
    ImageItem tryCompress(long maxSizeInBytes) {

    }
    // ImageItem tryCompress(float compressionFactor = 0.5f)

    // protected Result<List<UploadItem>> tryCompressUploads(List<UploadItem> uItems) {

    //     ByteArrayInputStream original = uItem?.stream
    //     if (!uItem?.validate() || uItem.mimeType != "image/jpeg") {
    //         return original
    //     }
    //     // TODO: need to properly close using `withCloseable` or finally
    //     try {
    //         BufferedImage img = ImageIO.read(original)
    //         // obtain image writer
    //         Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/jpeg")
    //         ImageWriter writer = writers.next()
    //         // set compression parameters
    //         ImageWriteParam param = writer.defaultWriteParam
    //         param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
    //         param.setCompressionQuality(quality);
    //         // create output
    //         ByteArrayOutputStream os = new ByteArrayOutputStream();
    //         ImageOutputStream outputStream = ImageIO.createImageOutputStream(os);
    //         writer.setOutput(outputStream);
    //         // write with compression
    //         writer.write(null, new IIOImage(img, null, null), param);
    //         // clean up
    //         outputStream.close()
    //         writer.dispose()
    //         ByteArrayInputStream compressed = new ByteArrayInputStream(os.toByteArray())
    //         compressed
    //     }
    //     catch (Throwable e) {
    //         log.debug("Helpers.tryCompressUploadItemStream: ${e.message}")
    //         original
    //     }
    // }
}
