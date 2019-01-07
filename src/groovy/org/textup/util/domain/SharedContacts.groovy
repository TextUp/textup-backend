package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class SharedContacts {

    static DetachedCriteria<SharedContact> forContactsNoJoins(Collection<Contact> contacts) {
        // do not exclude deleted contacts here becuase this detached criteria is used for
        // bulk operations. For bulk operations, joins are not allowed, so we cannot join the
        // SharedContact table with the Contact table to screen out deleted contacts
        new DetachedCriteria(SharedContact)
            .build { CriteriaUtils.inList(delegate, "contact", contacts) }
    }

    static DetachedCriteria<SharedContact> forRecordIdsWithOptions(Collection<Long> recordIds) {
        new DetachedCriteria(SharedContact)
            .build { CriteriaUtils.inList(delegate, "contact.context.record.id", recordIds) }
            .build(SharedContacts.buildForActive())
    }

    static DetachedCriteria<SharedContact> forOptions(Collection<Long> contactIds = null,
        Long sharedById = null, Long sharedWithId = null,
        Collection<ContactStatus> statuses = ContactStatus.VISIBLE_STATUSES) {

        new DetachedCriteria(SharedContact)
            .build {
                contact {
                    CriteriaUtils.inList(delegate, "id", contactIds, true)
                    CriteriaUtils.inList(delegate, "status", ContactStatus.VISIBLE_STATUSES)
                    eq("isDeleted", false)
                }
                CriteriaUtils.inList(delegate, "status", statuses)
            }
            .build(SharedContacts.buildForOptionalSharedBy(sharedById))
            .build(SharedContacts.buildForOptionalSharedWith(sharedWithId))
            .build(SharedContacts.buildForActive())
    }

    static Closure buildForSharedWithId() {
        return {
            projections {
                property("sharedWith.id")
            }
        }
    }

    // Helpers
    // -------

    protected Closure buildForOptionalSharedBy(Long sharedById) {
        return {
            sharedBy {
                isNotNull("numberAsString")
                if (sharedById) {
                    idEq(sharedById)
                }
            }
        }
    }

    protected Closure buildForOptionalSharedWith(Long sharedWithId) {
        return {
            if (sharedWithId) {
                sharedWith { idEq(sharedWithId) }
            }
        }
    }

    protected Closure buildForActive() {
        return {
            or {
                isNull("dateExpired") // not expired if null
                gt("dateExpired", DateTime.now(DateTimeZone.UTC))
            }
        }
    }
}
