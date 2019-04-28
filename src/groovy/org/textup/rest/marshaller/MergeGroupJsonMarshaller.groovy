package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class MergeGroupJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { MergeGroup mGroup ->
        mGroup.buildTarget().toInfo().properties + [merges: mGroup.possibleMerges]
	}

	MergeGroupJsonMarshaller() {
		super(MergeGroup, marshalClosure)
	}
}
