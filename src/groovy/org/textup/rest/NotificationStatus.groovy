package org.textup.rest

import grails.compiler.GrailsCompileStatic
import groovy.transform.ToString
import org.textup.Staff

// Container class to enable customized json output from the API endpoint
// Custom marshalled JSON documented as [notificationStatus] in CustomApiDocs.groovy

@GrailsCompileStatic
@ToString
class NotificationStatus {
	Staff staff
	boolean canNotify
}