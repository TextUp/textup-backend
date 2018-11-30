package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime
import org.textup.*

@EqualsAndHashCode
@GrailsTypeChecked
@Validateable
class RecordItemRequest {

    Phone phone
    Collection<Class<? extends RecordItem>> types
    DateTime start
    DateTime end
    boolean groupByEntity = false

    Recipients<Long, Contact> contacts
    Recipients<Long, SharedContact> sharedContacts
    Recipients<Long, ContactTag> tags

    static constraints = { // default nullable: false
        types nullable: true
        start nullable: true
        end nullable: true
        contacts cascadeValidation: true
        sharedContacts cascadeValidation: true
        tags cascadeValidation: true
    }

    // Methods
    // -------

    boolean hasAnyRecipients() {
        if (hasErrors()) {
            return false
        }
        [contacts, sharedContacts, tags].any { Recipients r1 -> r1.recipients.isEmpty() == false }
    }

    int countRecordItems() {
        if (hasErrors()) {
            return 0
        }
        getCriteria().count() as Integer
    }

    List<RecordItem> getRecordItems(Map params = [:], boolean recentFirst = true) {
        if (hasErrors()) {
            return []
        }
        List<Integer> normalized = Utils.normalizePagination(params.offset, params.max)
        params.offset = normalized[0]
        params.max = normalized[1]
        getCriteria(recentFirst).list(params)
    }

    List<RecordItemRequestSection> getSections(Map params = [:]) {
        if (hasErrors()) {
            return []
        }
        String phoneName = phone.owner.name
        // when exporting, we want the oldest records first instead of most recent first
        List<RecordItem> rItems = getRecordItems(params, false)
        // group by entity only makes sense if we have entities and haven't fallen back
        // to getting record items for the phone overall
        if (!hasAnyRecipients() && groupByEntity) {
            Map<Long, Collection<RecordItem>> recordIdToItems = MapUtils
                .<Long, RecordItem>buildManyObjectsMap({ RecordItem i1 -> i1.record.id }, rItems)
            buildSectionsByEntity(phoneName, recordIdToItems)
        }
        else {
            [
                new RecordItemRequestSection(phoneName: phoneName,
                    contactNames: contacts.recipients.collect { it.nameOrNumber },
                    tagNames: tags.recipients.collect { it.name },
                    sharedContactNames: sharedContacts.recipients.collect { it.name },
                    recordItems: rItems)
            ]
        }
    }

    // Helpers
    // -------

    protected DetachedCriteria<RecordItem> getCriteria(boolean recentFirst = true) {
        !hasAnyRecipients()
            ? RecordItem.forPhoneIdWithOptions(recentFirst, phone.id, start, end, types)
            : RecordItem.forRecordIdsWithOptions(recentFirst, getRecordIds(), start, end, types)
    }

    protected Collection<Long> getRecordIds() {
        ResultGroup<ReadOnlyRecord> resGroup = new ResultGroup<>()
        resGroup << contacts.recipients.collect { it.tryGetReadOnlyRecord() }
        resGroup << sharedContacts.recipients.collect { it.tryGetReadOnlyRecord() }
        resGroup << tags.recipients.collect { it.tryGetReadOnlyRecord() }

        resGroup.logFail("RecordItemRequest.getRecordIds")
        resGroup.payload*.id
    }

    protected List<RecordItemRequestSection> buildSectionsByEntity(String pName,
        Map<Long, Collection<RecordItem>> rIdToItems) {

        ResultGroup<RecordItemRequestSection> resGroup = new ResultGroup<>()
        resGroup << contacts.recipients.collect { addSectionForEntity(it, pName, rIdToItems) }
        resGroup << sharedContacts.recipients.collect { addSectionForEntity(it, pName, rIdToItems) }
        resGroup << tags.recipients.collect { addSectionForEntity(it, pName, rIdToItems) }

        resGroup.logFail("RecordItemRequest.buildSectionsByEntity")
        resGroup.payload
    }

    protected Result<RecordItemRequestSection> addSectionForEntity(WithRecord recordOwner,
        String phoneName, Map<Long, Collection<RecordItem>> recordIdToItems) {

        recordOwner.tryGetReadOnlyRecord()
            .then { ReadOnlyRecord rec1 ->
                RecordItemRequestSection rSec = new RecordItemRequestSection(phoneName: phoneName,
                    recordItems: recordIdToItems[rec1.id])
                if (recordOwner instanceof Contact) {
                    rSec.contactNames << recordOwner.nameOrNumber
                }
                else if (recordOwner instanceof SharedContact) {
                    rSec.sharedContactNames << recordOwner.name
                }
                else if (recordOwner instanceof ContactTag) {
                    rSec.tagNames << recordOwner.name
                }
                IOCUtils.resultFactory.success(rSec)
            }
    }
}
