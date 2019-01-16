package org.textup.interface

import grails.compiler.GrailsTypeChecked
import org.springframework.validation.Errors

@GrailsTypeChecked
interface IsIncomingMedia extends Validateable {
    String getAccountId()
    String getMimeType()
    String getUrl()
    String getMediaId()
    boolean getIsPublic()
    Result<Boolean> delete()

    boolean validate()
    Errors getErrors()
}
