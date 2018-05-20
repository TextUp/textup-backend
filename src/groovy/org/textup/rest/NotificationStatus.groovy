package org.textup.rest

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.ToString
import org.textup.Staff

// Container class to enable customized json output from the API endpoint
// Custom marshalled JSON documented as [notificationStatus] in CustomApiDocs.groovy

@GrailsCompileStatic
@ToString
@Validateable
class NotificationStatus {
	Staff staff
	Boolean canNotify

    // only for internal tracking of if notification should happen in PhoneOwnership
    Boolean isAvailableNow

    static constraints = {
        staff nullable: false
        canNotify nullable: false
        isAvailableNow nullable: false
    }
}
