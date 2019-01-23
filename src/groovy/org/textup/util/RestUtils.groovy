package org.textup.util

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class RestUtils {

    static final String ACTION_GET_LIST = "index"
    static final String ACTION_GET_SINGLE = "show"

    // Used in URLMappings to associate url routes with controllers
    static final String RESOURCE_ANNOUNCEMENT = "announcement"
    static final String RESOURCE_CONTACT = "contact"
    static final String RESOURCE_FUTURE_MESSAGE = "futureMessage"
    static final String RESOURCE_ORGANIZATION = "organization"
    static final String RESOURCE_RECORD_ITEM = "record"
    static final String RESOURCE_SESSION = "session"
    static final String RESOURCE_STAFF = "staff"
    static final String RESOURCE_TAG = "tag"
    static final String RESOURCE_TEAM = "team"
}
