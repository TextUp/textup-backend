package org.textup

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class CustomAccountDetails implements WithId, Saveable<CustomAccountDetails> {

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
