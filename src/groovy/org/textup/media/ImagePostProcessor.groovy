package org.textup.media

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import java.awt.image.BufferedImage
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class ImagePostProcessor implements CanProcessMedia {

    protected static final ImagePostProcessor.ImageData SEND_VERSION =
        new ImagePostProcessor.ImageData(maxWidthInPixels: 640, maxSizeInBytes: 5000)
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

    @Override
    void close() {}

    @Override
    Result<UploadItem> createInitialVersion() {
        UploadItem.tryCreate(_type, _data, _image)
    }

    @Override
    Result<UploadItem> createSendVersion() {
        buildUploadItem(SEND_VERSION)
    }

    @Override
    ResultGroup<UploadItem> createAlternateVersions() {
        ResultGroup.collect(ALT_VERSIONS) { ImagePostProcessor.ImageData d1 -> buildUploadItem(d1) }
    }

    // Helpers
    // -------

    protected Result<UploadItem> buildUploadItem(ImagePostProcessor.ImageData target) {
        ImageUtils.tryResizeToWidth(_type, _data, _image, target.maxWidthInPixels)
            .then { Tuple<byte[], BufferedImage> tup1 ->
                Tuple.split(tup1) { byte[] newData, BufferedImage newImage ->
                    ImageUtils.tryCompress(_type, newData, newImage, target.maxSizeInBytes)
                }
            }
            .then { Tuple<byte[], BufferedImage> tup1 ->
                Tuple.split(tup1) { byte[] newData, BufferedImage newImage ->
                    UploadItem.tryCreate(_type, newData, newImage)
                }
            }
    }

    protected static class ImageData {
        int maxWidthInPixels
        long maxSizeInBytes
    }
}
