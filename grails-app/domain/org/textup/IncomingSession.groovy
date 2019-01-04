package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*
import org.textup.type.AuthorType
import org.textup.validator.Author
import org.textup.validator.BasePhoneNumber
import org.textup.validator.PhoneNumber

@GrailsTypeChecked
@EqualsAndHashCode
class IncomingSession implements WithId, WithPhone {

    Phone phone
    String numberAsString

    @RestApiObjectField(
        description    = "Date this session was created",
        allowedType    = "DateTime",
        useForCreation = false)
    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
    // initialize last sent in past so that we send instructions when the user
    // first tests in, if needed
    @RestApiObjectField(
        description    = "When instructions were last sent via text",
        allowedType    = "DateTime",
        useForCreation = false)
    DateTime lastSentInstructions = DateTime.now(DateTimeZone.UTC).minusDays(2)
    @RestApiObjectField(
        description    = "If this session is subscribed to texts",
        allowedType    = "Boolean",
        useForCreation = true)
    Boolean isSubscribedToText = false
    @RestApiObjectField(
        description    = "If this session is subscribed to calls",
        allowedType    = "Boolean",
        useForCreation = true)
    Boolean isSubscribedToCall = false

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName= "number",
            description = "Number of this session",
            allowedType =  "String",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName= "shouldSendInstructions",
            description = "if we should send text instructions next time this \
                number texts in",
            allowedType =  "Boolean",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName= "staff",
            description = "Id of the staff that this session belongs to, if any",
            allowedType =  "Number",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName= "team",
            description = "Id of the team that this session belongs to, if any",
            allowedType =  "Number",
            useForCreation = false)
    ])
    static transients = ["number"]
    static mapping = {
        whenCreated type: PersistentDateTime
        lastSentInstructions type: PersistentDateTime
    }

    // Instructions
    // ------------

    void updateLastSentInstructions() {
        lastSentInstructions = DateTime.now(DateTimeZone.UTC)
    }

    // Property Access
    // ---------------

    Author toAuthor() {
        new Author(id: id, type:AuthorType.SESSION, name: numberAsString)
    }

    boolean getShouldSendInstructions() {
        lastSentInstructions.isBefore(DateTime.now().withTimeAtStartOfDay())
    }

    void setNumber(BasePhoneNumber pNum) {
        numberAsString = pNum?.number
    }

    PhoneNumber getNumber() {
        PhoneNumber.create(numberAsString)
    }
}
