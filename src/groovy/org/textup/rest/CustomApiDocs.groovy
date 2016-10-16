package org.textup.rest

import org.restapidoc.annotation.*

class CustomResponseDoc {

    @RestApiObjectField(description = "Request a password reset")
    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName = "username",
            description = "Username of the account to send password request for",
            allowedType = "String",
            useForCreation = true),
    ])
    static def passwordResetRequest

    @RestApiObjectField(description = "Request a password reset")
    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName = "token",
            description = "Reset token string",
            allowedType = "String",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName = "password",
            description = "New password for the staff",
            allowedType = "String",
            useForCreation = true),
    ])
    static def newPasswordRequest

    @RestApiObjectField(description = "Modify a contact with relative to a tag")
    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName = "id",
            description = "Id of the contact to modify",
            allowedType = "Number",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName = "action",
            description = "Action to take. Allowed: ADD, REMOVE",
            allowedType = "String",
            useForCreation = true)
    ])
    static def tagAction

    @RestApiObjectField(description = "Modify a staff with relative to a team")
    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName = "id",
            description = "Id of the staff to modify",
            allowedType = "Number",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName = "action",
            description = "Action to take. Allowed: ADD, REMOVE",
            allowedType = "String",
            useForCreation = true)
    ])
    static def teamAction

    @RestApiObjectField(description = "Share or stop sharing a contact with another staff member")
    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName = "id",
            description = "Id of the phone that contact is shared with",
            allowedType = "Number",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName = "action",
            description = "Action to take. Allowed: MERGE, STOP",
            allowedType = "String",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName = "permission",
            description = "Level of permission to share with. Allowed: DELEGATE, VIEW",
            allowedType = "String",
            useForCreation = true)
    ])
    static def shareAction

    @RestApiObjectField(description = "Add, update, or delete a contact's phone numbers")
    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName = "number",
            description = "Phone number",
            allowedType = "String",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "preference",
            description    = "Level of preference to use this number. \
                Smaller number is higher preference.",
            allowedType    = "Number",
            mandatory      = false,
            defaultValue   = "0",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName = "action",
            description = "Action to take. Allowed: MERGE, DELETE",
            allowedType = "String",
            useForCreation = true)
    ])
    static def numberAction

    @RestApiObjectField(description = "Deactivate or transfer phone")
    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "action",
            description    = "Action to take. Allowed: DEACTIVATE, TRANSFER, NUMBYNUM, NUMBYID",
            allowedType    = "String",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "id",
            description    = "TRANSFER: Id of the entity to transfer to",
            allowedType    = "Number",
            mandatory      = false,
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "type",
            description    = "TRANSFER: Type of the entity. Allowed: GROUP, INDIVIDUAL",
            allowedType    = "String",
            mandatory      = false,
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "number",
            description    = "NUMBYNUM: New phone number to update phone with",
            allowedType    = "String",
            mandatory      = false,
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "numberId",
            description    = "NUMBYID: Id of new phone number to update phone with",
            allowedType    = "String",
            mandatory      = false,
            useForCreation = true)
    ])
    static def phoneAction

    @RestApiObjectField(description = "Add or remove an image from a note in a record.")
    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "mimeType",
            description    = "ADD: content type of the image",
            allowedType    = "String",
            mandatory      = false,
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "sizeInBytes",
            description    = "ADD: number of bytes of the image to upload",
            allowedType    = "String",
            mandatory      = false,
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "key",
            description    = "REMOVE: key of the image to remove",
            allowedType    = "String",
            mandatory      = false,
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName = "action",
            description = "Action to take. Allowed: ADD, REMOVE",
            allowedType = "String",
            useForCreation = true)
    ])
    static def noteImageAction
}
