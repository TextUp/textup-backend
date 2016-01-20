package org.textup

import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime

@EqualsAndHashCode(callSuper=true)
class TeamPhone extends Phone {

    //grailsApplication from superclass
    //resultFactory from superclass

	//NOTE: authorId on the individual record items
    //correspond to Staff

    private Map<Long,HashSet<Long>> _contactIdToTagIds = [:]
    private Map<Long,RecordItem> _contactableIdToRecordItem = [:]
    private List<ContactTag> _parsedTags = []

    static transients = ["_contactIdToTagIds", "_contactableIdToRecordItem", "_parsedTags"]
    static constraints = {
    }
    static namedQueries = {
        forTeamNumber { TransientPhoneNumber num ->
            //embedded properties must be accessed with dot notation
            eq("number.number", num?.number)
        }
    }

    /*
	Has many:
		Contact (from superclass)
		ContactTag (from superclass)
		TeamContactTag
	*/

    ////////////
    // Events //
    ////////////

    def beforeDelete() {
        TeamPhone.withNewSession {
            def tags = ContactTag.where { phone == this }
            def contacts = Contact.where { phone == this }
            //delete tag memberships, must come before
            //deleting ContactTag and Contact
            new DetachedCriteria(TagMembership).build {
                def res = tags.list()
                if (res) { "in"("tag", res) }
                else { eq("tag", null) }
            }.deleteAll()
            //must be before we delete our contacts FOR RECORD DELETION
            def associatedRecordIds = new DetachedCriteria(Contact).build {
                projections { property("record.id") }
                eq("phone", this)
            }.list()
            def tagRecordIds = new DetachedCriteria(TeamContactTag).build {
                projections { property("record.id") }
                eq("phone", this)
            }.list()
            //delete contacts' numbers
            new DetachedCriteria(ContactNumber).build {
                def res = contacts.list()
                if (res) { "in"("contact", res) }
                else { eq("contact", null) }
            }.deleteAll()
            //delete contact and contact tags
            contacts.deleteAll()
            tags.deleteAll()
            //delete records associated with contacts and tags, must
            //come after contacts are deleted
            new DetachedCriteria(Record).build {
                def res = associatedRecordIds + tagRecordIds
                if (res) { "in"("id", res) }
                else { eq("id", null) }
            }.deleteAll()
        }
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    /*
    Phone capabilities
     */
    @Override
    Result<Map> text(String message, List<String> numbers,
        List<Long> contactableIds, List<Long> tagIds) {
        _contactableIdToRecordItem = [:]
        _contactIdToTagIds = [:]
        _parsedTags = []
        Result<RecordResult> superRes = super.text(message, numbers, contactableIds, tagIds)
        if (superRes.success) {
            //Now we need to populate the appropriate TeamContactTags' records
            //for each tag that is a TeamContactTag, create a record item
            Map<Long,RecordItem> tagIdToRecordItem = [:]
            _parsedTags.each { ContactTag tag ->
                if (tag.instanceOf(TeamContactTag)) {
                    Result<RecordText> tagRes = tag.addTextToRecord(contents:message)
                    if (tagRes.success) { tagIdToRecordItem[tag.id] = tagRes.payload }
                    else { log.error("TeamPhone.text: can't add text to record: ${tagRes.payload}") }
                }
            }
            //add recordItemReceipt to tag's record if tag is a TeamContactTag
            _contactableIdToRecordItem.each { long contactableId, RecordItem contactableRecordItem ->
                //some contactables are NOT our contacts, and so we need the same dereference
                //operator in these cases to skip over these
                _contactIdToTagIds[contactableId]?.each { long tagId ->
                    RecordItem tagItem = tagIdToRecordItem[tagId]
                    if (tagItem) {
                        contactableRecordItem.receipts.each { RecordItemReceipt r ->
                           RecordItemReceipt newR = new RecordItemReceipt(status:r.status, apiId:r.apiId)
                            newR.receivedByAsString = r.receivedBy.number
                            tagItem.addToReceipts(newR)
                            newR.save()
                        }
                    }
                    else { log.error("TeamPhone.text: no RecordItem found for tag ${tagId}") }
                }
            }
        }
        //now we return the results from the super call
        superRes
    }
    @Override
    protected void afterSendTextTo(Contactable c, Result<RecordResult> res) {
        if (res.success) {
            _contactableIdToRecordItem.put(c.id, res.payload.newItems[0])
        }
    }
    @Override
    protected void afterParsingTags(List<ContactTag> parsedTags, List<Long> invalid) {
        _parsedTags = parsedTags
        parsedTags.each { ContactTag tag ->
            tag.subscribers.each { TagMembership m ->
                Contact s = m.contact
                if (!_contactIdToTagIds.containsKey(s.id)) {
                    _contactIdToTagIds[s.id] = new HashSet<Long>()
                }
                _contactIdToTagIds[s.id].add(tag.id)
            }
        }
    }


    @Override
    Result<Map> scheduleText(String message, DateTime sendAt,
        List<String> numbers, List<Long> contactableIds, List<Long> tagIds) {

        //TODO implement me

    }

    /*
    Tags
     */
    @Override
    Result<ContactTag> createTag(Map params) {
        TeamContactTag teamTag = new TeamContactTag()
        teamTag.with {
            phone = this
            name = params.name
            if (params.hexColor) hexColor = params.hexColor
        }
        teamTag.phone = this
        if (teamTag.save()) { resultFactory.success(teamTag) }
        else { resultFactory.failWithValidationErrors(teamTag.errors) }
    }

    /////////////////////
    // Property Access //
    /////////////////////

    List<FeaturedAnnouncement> getCurrentFeatures() {
        FeaturedAnnouncement.notExpiredForTeamPhone(this).list(max:3)
    }

    @Override
    List<ContactTag> getTags(Map params=[:]) {
        TeamContactTag.findAllByPhone(this, params + [sort: "name", order: "desc"])
    }
}
