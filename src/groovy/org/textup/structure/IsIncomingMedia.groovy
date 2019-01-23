package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.springframework.validation.Errors
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
interface IsIncomingMedia extends CanValidate {
    String getAccountId()
    String getMimeType()
    String getUrl()
    String getMediaId()
    boolean getIsPublic()
    Result<Boolean> delete()

    boolean validate()
    Errors getErrors()
}
