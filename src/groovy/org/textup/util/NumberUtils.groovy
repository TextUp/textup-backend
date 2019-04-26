package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class NumberUtils {

    static boolean nearlyEqual(BigDecimal num1, BigDecimal num2, int precision) {
        round(num1, precision) == round(num2, precision)
    }

    // Helpers
    // -------

    // Our version of Groovy does not implement a `round` method on `BigDecimal` yet
    protected static BigDecimal round(BigDecimal num, int precision) {
        num?.doubleValue()?.round(precision) as BigDecimal
    }
}
