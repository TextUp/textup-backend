package org.textup.media

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.type.*
import org.textup.validator.*

@GrailsTypeChecked
interface CanProcessMedia extends Closeable {
    Result<? extends UploadItem> createSendVersion()
    ResultGroup<? extends UploadItem> createAlternateVersions()
}
