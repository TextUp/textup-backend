package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class FutureMessages {

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static DetachedCriteria<FutureMessage> forRecords(Collection<Record> records) {
        new DetachedCriteria(FutureMessage)
            .build { CriteriaUtils.inList(delegate, "record", records) }
    }
}
