package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
interface ReadOnlyOrganization {

    Long getId()
    String getName()
    String getAwayMessageSuffix()
    OrgStatus getStatus()
    int getTimeout()
    ReadOnlyLocation getReadOnlyLocation()
}
