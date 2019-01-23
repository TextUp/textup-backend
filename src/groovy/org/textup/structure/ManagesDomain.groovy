package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
class ManagesDomain {

    interface Creater<T> {
        Result<T> create(Long id, TypeMap body)
    }

    interface Updater {
        Result<T> create(Long id, TypeMap body)
    }

    interface Deleter {
        Result<Void> create(Long id)
    }
}
