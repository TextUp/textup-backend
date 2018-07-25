package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import groovy.util.logging.Log4j
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*
import org.textup.type.ContactStatus

@GrailsCompileStatic
@Log4j
class ContactableJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace, GrailsApplication grailsApplication,
        LinkGenerator linkGenerator, Contactable c1 ->

        Map json = [:]

        Result<ReadOnlyRecord> res = c1.tryGetReadOnlyRecord().logFail("ContactableJsonMarshaller")
        if (!res.success) { return json }
        ReadOnlyRecord rec1 = res.payload
        // add general Contactable fields
        json.id = c1.contactId
        json.lastRecordActivity = rec1.lastRecordActivity
        if (c1.name) {
            json.name = c1.name
        }
        if (c1.note) {
            json.note = c1.note
        }
        json.numbers = c1.sortedNumbers.collect { ContactNumber num -> [number:num.prettyPhoneNumber] }
        json.futureMessages = rec1.getFutureMessages()
        json.notificationStatuses = c1.getNotificationStatuses()
        json.language = rec1.getLanguage()?.toString()
        json.status = c1.status?.toString()
        // when manually marking as unread, a contact that is unread may not
        // have any unread counts to report
        if (c1.status == ContactStatus.UNREAD && rec1.countSince(c1.lastTouched) > 0) {
            Map<String, Integer> unreadInfo = [
                numTexts: rec1.countSince(c1.lastTouched, [RecordText]),
                numCalls: rec1.countCallsSince(c1.lastTouched, false),
                numVoicemails: rec1.countCallsSince(c1.lastTouched, true)
            ]
            json.unreadInfo = unreadInfo
        }
        // add fields specific to Contacts or SharedContacts
        if (c1.instanceOf(Contact)) {
            Contact contact = c1 as Contact
            json.tags = contact.getTags()
            json.sessions = contact.getSessions()
            json.sharedWith = contact.sharedContacts.collect { SharedContact sc ->
                [
                    id:sc.id,
                    whenCreated:sc.whenCreated,
                    permission:sc.permission.toString(),
                    sharedWith:sc.sharedWith.id // use the PHONE's id
                ]
            }
            json.phone = contact.phone.id
        }
        else if (c1.instanceOf(SharedContact)) {
            SharedContact sc = c1 as SharedContact
            json.permission = sc.permission.toString()
            json.startedSharing = sc.whenCreated
            json.sharedBy = sc.sharedBy.name
            json.sharedById = sc.sharedBy.id
            json.phone = sc.sharedWith.id
        }
        else {
            log.error("ContactableJsonMarshaller: passed in Contactable $c1 is \
                not an instance of either Contact or SharedContact")
        }
        // add links
        json.links = [:] << [self:linkGenerator.link(namespace:namespace,
            resource:"contact", action:"show", id:c1.contactId, absolute:false)]
        json
    }

    ContactableJsonMarshaller() {
        super(Contactable, marshalClosure)
    }
}
