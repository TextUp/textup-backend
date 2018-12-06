package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*
import org.textup.validator.MergeGroup
import org.textup.validator.MergeGroupItem

@GrailsTypeChecked
class MergeGroupJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { String namespace, GrailsApplication grailsApplication,
        LinkGenerator linkGenerator, MergeGroup mGroup ->

        Contact c1 = mGroup.targetContact

        Map json = [:]
    	if (c1) {
    		json.putAll(MergeGroupJsonMarshaller.marshalSimpleContact(c1))
    	}
    	json.merges = mGroup.possibleMerges.collect { MergeGroupItem item1 ->
            Collection<Map<String,?>> mergeWithList = []
            item1.mergeWith.each { Contact c2 ->
                mergeWithList << MergeGroupJsonMarshaller.marshalSimpleContact(c2)
            }
    		[mergeBy:item1.number.prettyPhoneNumber, mergeWith:mergeWithList]
    	}
    	json.links = [:] << [self:linkGenerator.link(namespace:namespace,
            resource:"contact", action:"show", id:mGroup.targetContactId, absolute:false)]
    	json
	}
	static Map marshalSimpleContact(Contact c1) {
        Map json = [:]
        json.id = c1.id
		if (c1.name) {
            json.name = c1.name
        }
        if (c1.note) {
            json.note = c1.note
        }
        json.numbers = c1.sortedNumbers.collect { ContactNumber num -> [number:num.prettyPhoneNumber] }
        return json
	}

	MergeGroupJsonMarshaller() {
		super(MergeGroup, marshalClosure)
	}
}
