package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
interface ReadOnlyStaff {

    Long getId()
    String getName()
    String getUsername()
    String getEmail()
    StaffStatus getStatus()
    boolean hasPersonalNumber()
    PhoneNumber getPersonalNumber()
    ReadOnlyOrganization getReadOnlyOrg()
}
