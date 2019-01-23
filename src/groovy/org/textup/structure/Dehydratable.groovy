package org.textup.structure

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
interface Dehydratable<T extends Rehydratable> {
    T dehydrate()
}
