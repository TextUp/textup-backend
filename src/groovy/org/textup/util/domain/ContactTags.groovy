package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class ContactTags {

    static Result<ContactTag> create(Phone p1, String name) {
        ContactTag ct1 = new ContactTag(name: name)
        c1.context = new PhoneRecord(phone: p1)
        if (ct1.save()) {
            IOCUtils.resultFactory.success(ct1)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(ct1.errors) }
    }

    static DetachedCriteria<ContactTag> forPhoneIdAndOptions(Long phoneId, String name = null) {
        new DetachedCriteria(ContactTag)
            .build {
                eq("context.phone.id", phoneId)
                eq("isDeleted", false)
                if (name) {
                    eq("name", name)
                }
            }
    }

    static DetachedCriteria<ContactTag> forMemberIds(Collection<Long> cIds) {
        new DetachedCriteria(ContactTag)
            .build {
                members {
                    CriteriaUtils.inList(delegate, "id", cIds)
                    eq("isDeleted", false)
                }
                eq("isDeleted", false)
            }
    }

    static Closure buildForSort() {
        return { order("name", "desc") }
    }
}
