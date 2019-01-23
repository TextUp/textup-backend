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
class MergeGroupItemJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { MergeGroupItem mItem ->
        [
            mergeBy: mItem.number,
            mergeWith: mItem.buildMergeWith()*.toInfo()
        ]
    }

    MergeGroupItemJsonMarshaller() {
        super(MergeGroupItem, marshalClosure)
    }
}
