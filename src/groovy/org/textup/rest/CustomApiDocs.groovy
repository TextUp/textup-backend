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

    @RestApiObjectField(description = "A message notification sent to a staff")
    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName = "ownerType",
            description = "whether the owner is a 'staff' or 'team'",
            allowedType = "String"),
        @RestApiObjectField(
            apiFieldName = "ownerId",
            description = "identifier of the owner, username if staff, name if team",
            allowedType = "String"),
        @RestApiObjectField(
            apiFieldName = "ownerName",
            description = "name of the owner",
            allowedType = "String"),
        @RestApiObjectField(
            apiFieldName = "ownerNumber",
            description = "TextUp number of the owner",
            allowedType = "String"),
        @RestApiObjectField(
            apiFieldName = "contents",
            description = "contents of the message",
            allowedType = "String"),
        @RestApiObjectField(
            apiFieldName = "outgoing",
            description = "whether or not this message is outgoing",
            allowedType = "Boolean"),
        @RestApiObjectField(
            apiFieldName = "otherType",
            description = "whether the other party is a 'contact' or 'tag'",
            allowedType = "String"),
        @RestApiObjectField(
            apiFieldName = "otherId",
            description = "identifier of other party, id if contact and name if tag",
            allowedType = "String"),
        @RestApiObjectField(
            apiFieldName = "otherName",
            description = "name of the other party",
            allowedType = "String")
    ])
    static def notification

    @RestApiObjectField(description = "A message notification sent to a staff")
    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "id",
            description    = "Id of the staff member this status pertains to",
            allowedType    = "Number",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "name",
            description    = "Name of the staff member this status pertains to",
            allowedType    = "String",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "username",
            description    = "Username of the staff member this status pertains to",
            allowedType    = "String",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "number",
            description    = "TextUp phone number of the staff member this status pertains to",
            allowedType    = "String",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "canNotify",
            description    = "Whether or not this staff member will receive notifications if they are available",
            allowedType    = "Boolean",
            useForCreation = true),
    ])
    static def notificationStatus

    @RestApiObjectField(description = "A message notification sent to a staff")
    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "isAvailableNow",
            description    = "READ ONLY, use this value to determine whether or not the staff member is available \
                regardless of other availability settings",
            allowedType    = "boolean",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "useStaffAvailability",
            description    = "whether to use the staff's default availability or use phone-specific \
                availability settings",
            allowedType    = "boolean",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "manualSchedule",
            description    = "if using phone-specific availability settings, whether or not availability \
                is manually specific instead of on a schedule",
            allowedType    = "boolean",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "isAvailable",
            description    = "if using phone-specific availability settings AND manually specifying \
                availability, whether or not this staff member is available to receive notifications \
                from this TextUp phone",
            allowedType    = "boolean",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "schedule",
            description    = "if using phone-specific availability settings AND specifying \
                availability according to a schedule, this is the schedule that will be used",
            allowedType    = "Schedule",
            useForCreation = false)
    ])
    static def staffPolicyAvailability

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
            description = "MERGE: Level of permission to share with. Allowed: DELEGATE, VIEW",
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

    @RestApiObjectField(description = "Merge other contacts in the specified contact")
    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "mergeIds",
            description    = "List of the other ids to merge into this id",
            allowedType    = "List",
            mandatory      = true,
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "nameId",
            description    = "RECONCILE: Id of the contact to keep the name from",
            allowedType    = "Number",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "noteId",
            description    = "RECONCILE: Id of the contact to keep the note from",
            allowedType    = "Number",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "action",
            description    = "Action to take. Allowed: DEFAULT, RECONCILE",
            allowedType    = "String",
            mandatory      = false,
            useForCreation = true)
    ])
    static def mergeAction

    @RestApiObjectField(description = "Enable or disable notifications for a specific contact and staff member")
    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName = "action",
            description = "Action to take. Allowed: CHANGEDEFAULT, ENABLE, DISABLE",
            allowedType = "String",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "id",
            description    = "Id of staff member we are customizing notifications for",
            allowedType    = "Number",
            mandatory      = true,
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "level",
            description    = "CHANGEDEFAULT: Default notification level if unspecified. Allowed: ALL, NONE",
            allowedType    = "String",
            mandatory      = false,
            useForCreation = true)
    ])
    static def notificationAction

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
            apiFieldName   = "action",
            description    = "Action to take. Allowed: ADD, REMOVE",
            allowedType    = "String",
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "mimeType",
            description    = "ADD: Content type",
            allowedType    = "String",
            mandatory      = true,
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "data",
            description    = "ADD: String representation of the data to upload",
            allowedType    = "String",
            mandatory      = true,
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "checksum",
            description    = "ADD: md5 of the data for an integrity check",
            allowedType    = "String",
            mandatory      = true,
            useForCreation = true),
        @RestApiObjectField(
            apiFieldName   = "key",
            description    = "REMOVE: key of the image to remove",
            allowedType    = "String",
            mandatory      = false,
            useForCreation = true)
    ])
    static def noteImageAction
}
