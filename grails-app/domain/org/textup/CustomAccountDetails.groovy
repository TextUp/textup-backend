package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class CustomAccountDetails implements WithId, CanSave<CustomAccountDetails> {

    String accountId
    String authToken

    static constraints = {
        accountId blank: false, unique: true
        authToken blank: false
    }
    static mapping = {
        cache true
    }
}
