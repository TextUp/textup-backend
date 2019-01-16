package org.textup.interface

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
interface Rehydratable<T extends Dehydratable> {
    Result<T> tryRehydrate()
}
