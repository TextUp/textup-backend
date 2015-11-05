package org.textup.rest 

import org.restapidoc.annotation.*

class CustomResponseDoc {
    @RestApiObjectField(description = "Modify a contact with relative to a tag")
    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName = "id",
            description = "Id of the contact to modify",
            allowedType = "Number",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName = "action",
            description = "Action to take. Allowed: add, remove, subscribe, unsubscribe",
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
            description = "Action to take. Allowed: add, remove",
            allowedType = "String",
            useForCreation = true)
    ])
    static def teamAction

    @RestApiObjectField(description = "Share or stop sharing a contact with another staff member")
    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName = "id",
            description = "Id of the staff member",
            allowedType = "Number",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName = "action",
            description = "Action to take. Allowed: merge, stop",
            allowedType = "String",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName = "permission",
            description = "Level of permission to share with. Allowed: delegate, view",
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
            description    = "Level of preference to use this number. Smaller number is higher preference.",
            allowedType    = "Number",
            mandatory      = false,
            defaultValue   = "0",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName = "action",
            description = "Action to take. Allowed: merge, delete",
            allowedType = "String",
            useForCreation = true)
    ])
    static def numberAction
}