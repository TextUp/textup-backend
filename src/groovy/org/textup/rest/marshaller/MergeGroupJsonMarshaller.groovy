package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*
import org.textup.validator.MergeGroup
import org.textup.validator.MergeGroupItem

@GrailsCompileStatic
class MergeGroupJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { String namespace, GrailsApplication grailsApplication,
        LinkGenerator linkGenerator, MergeGroup mGroup ->

        Contact c1 = mGroup.targetContact

        Map json = [:]
    	if (c1) {
    		doMarshalContact(json, c1)
    	}
    	json.merges = mGroup.possibleMerges.collect { MergeGroupItem item1 ->
            Collection<Map<String,?>> mergeWithList = []
            item1.mergeWith.each { Contact c2 ->
                Map<String,?> mergeWith = [:]
                doMarshalContact(mergeWith, c2)
                mergeWithList << mergeWith
            }
    		[mergeBy:item1.number.prettyPhoneNumber, mergeWith:mergeWithList]
    	}
    	json.links = [:] << [self:linkGenerator.link(namespace:namespace,
            resource:"contact", action:"show", id:mGroup.targetContactId, absolute:false)]
    	json
	}
	static final Closure<Void> doMarshalContact = { Map json, Contact c1 ->
        json.id = c1.id
		if (c1.name) {
            json.name = c1.name
        }
        if (c1.note) {
            json.note = c1.note
        }
        json.numbers = c1.sortedNumbers.collect { ContactNumber num -> [number:num.prettyPhoneNumber] }
        return
	}

	MergeGroupJsonMarshaller() {
		super(MergeGroup, marshalClosure)
	}
}
