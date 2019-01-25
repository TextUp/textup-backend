package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
class ManagesDomain {

    interface Creater<T> {
        Result<T> create(Long id, TypeMap body)
    }

    interface Updater<T> {
        Result<T> update(Long id, TypeMap body)
    }

    interface Deleter {
        Result<Void> delete(Long id)
    }
}
