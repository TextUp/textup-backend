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
}
