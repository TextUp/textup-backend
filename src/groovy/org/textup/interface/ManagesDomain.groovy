package org.textup.interface

import grails.compiler.GrailsTypeChecked

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
