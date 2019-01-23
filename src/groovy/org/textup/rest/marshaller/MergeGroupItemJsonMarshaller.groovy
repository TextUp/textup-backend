package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*

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
