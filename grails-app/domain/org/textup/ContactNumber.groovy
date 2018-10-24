package org.textup

import groovy.transform.EqualsAndHashCode
import org.textup.validator.BasePhoneNumber
import grails.compiler.GrailsCompileStatic
import org.hibernate.Session
import groovy.transform.TypeCheckingMode

@GrailsCompileStatic
@EqualsAndHashCode(callSuper=true, includes=["number", "preference"])
class ContactNumber extends BasePhoneNumber implements WithId {

	Integer preference

    static belongsTo = [owner:Contact]
    static constraints = {
        number validator:{ String val ->
            if (!(val?.toString() ==~ /^(\d){10}$/)) { ["format"] }
        }
    }

    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    static Map<String,List<Contact>> getContactsForPhoneAndNumbers(Phone p1,
        Collection<String> nums) {
        List<ContactNumber> cNums = ContactNumber.createCriteria().list {
            createAlias("owner", "c1")
            eq("c1.phone", p1)
            eq("c1.isDeleted", false)
            if (nums) { "in"("number", nums) }
            else { eq("number", null) }
            order("number")
        } as List
        HashSet<String> numsRemaining = new HashSet<String>(nums)
        Map<String,List<Contact>> numAsStringToContacts = [:]
        cNums.each { ContactNumber cn ->
            numsRemaining.remove(cn.number)
            if (numAsStringToContacts.containsKey(cn.number)) {
                numAsStringToContacts[cn.number] << cn.owner
            }
            else {
                numAsStringToContacts[cn.number] = [cn.owner]
            }
        }
        numsRemaining.each { String numAsString ->
            numAsStringToContacts[numAsString] = []
        }
        numAsStringToContacts
    }
}
