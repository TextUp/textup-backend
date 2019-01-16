package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.joda.time.*

@Sortable
@EqualsAndHashCode
@GrailsTypeChecked
@Validateable
class LocalDay implements Validateable {

    private final TreeSet<LocalInterval> intervals = new TreeSet<>()

    static constraints = {
        intervals cascadeValidation: true
    }

    // Methods
    // -------

    def tryAddInterval(LocalTime start, LocalTime end) {
        LocalInterval.tryCreate(start, end)
            .then { LocalInterval lInt1 ->
                intervals << lInt1
                tryUnifyIntervals() // TODO
                DomainUtils.tryValidate()
            }
    }

    // Helpers
    // -------

    protected def tryUnifyIntervals() {

    }
}
