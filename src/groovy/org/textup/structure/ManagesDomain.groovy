package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
class ManagesDomain {

    interface Creater<T> {
        Result<T> tryCreate(Long id, TypeMap body)
    }

    interface Updater<T> {
        Result<T> tryUpdate(Long id, TypeMap body)
    }

    interface Deleter {
        Result<Void> tryDelete(Long id)
    }
}
