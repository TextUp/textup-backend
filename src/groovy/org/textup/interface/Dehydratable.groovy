package org.textup.interface

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
interface Dehydratable<T extends Rehydratable> {
    T dehydrate()
}
