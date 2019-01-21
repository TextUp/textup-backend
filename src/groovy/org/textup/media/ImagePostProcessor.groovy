package org.textup.media

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import java.awt.image.BufferedImage
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class ImagePostProcessor implements CanProcessMedia {

    protected static final ImagePostProcessor.ImageData SEND_VERSION = new ImagePostProcessor.ImageData(maxWidthInPixels: 640, maxSizeInBytes: 5000)
    protected static final List<ImagePostProcessor.ImageData> ALT_VERSIONS = [
        new ImagePostProcessor.ImageData(maxWidthInPixels: 1280, maxSizeInBytes: 200000),
        new ImagePostProcessor.ImageData(maxWidthInPixels: 640, maxSizeInBytes: 100000),
        new ImagePostProcessor.ImageData(maxWidthInPixels: 320, maxSizeInBytes: 50000)
    ]

    private final MediaType _type
    private final byte[] _data
    private final BufferedImage _image

    ImagePostProcessor(MediaType t1, byte[] d1) {
        _type = t1
        if (d1) {
            _data = d1
            _image = ImageUtils.tryGetImageFromData(d1)
        }
    }

    void close() {}

    Result<UploadItem> createInitialVersion() {
        getInitialUploadItem()
    }

    Result<UploadItem> createSendVersion() {
        buildUploadItem(SEND_VERSION)
    }

    ResultGroup<UploadItem> createAlternateVersions() {
        new ResultGroup<UploadItem>(ALT_VERSIONS.collect { ImagePostProcessor.ImageData d1 -> buildUploadItem(d1) })
    }

    // Helpers
    // -------

    protected Result<UploadItem> buildUploadItem(ImagePostProcessor.ImageData target) {
        getInitialUploadItem().then { UploadItem uItem ->
            ImageUtils.tryResizeToWidth(_type, _data, _image, target.maxWidthInPixels)
                .then { Tuple<byte[], BufferedImage> after ->
                    ImageUtils.tryCompress(_type, after.first, after.second, target.maxSizeInBytes)
                }
                .thenEnd { Tuple<byte[], BufferedImage> after ->
                    uItem.data = after.first
                    uItem.image = after.second
                }
            if (uItem.validate()) {
                IOCUtils.resultFactory.success(uItem)
            }
            else { IOCUtils.resultFactory.failWithValidationErrors(uItem.errors) }
        }
    }

    protected Result<UploadItem> getInitialUploadItem() {
        UploadItem uItem = new UploadItem(type: _type, data: _data, image: _image)
        if (uItem.validate()) {
            IOCUtils.resultFactory.success(uItem)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(uItem.errors) }
    }

    protected static class ImageData {
        int maxWidthInPixels
        long maxSizeInBytes
    }
}
