package org.textup.util

import grails.compiler.GrailsTypeChecked
import java.util.regex.*
import org.joda.time.*
import org.joda.time.format.*
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*


@GrailsTypeChecked
class PasswordValidationUtils {

    static final Pattern ONE_UPPER_CASE = Pattern.compile(/[A-Z]/)
    static final Pattern ONE_LOWER_CASE = Pattern.compile(/[a-z]/)
    static final Pattern ONE_NUMBER = Pattern.compile(/\d/)
    static final Pattern ONE_NON_ALPHANUMERIC = Pattern.compile(/[^(a-z)(A-Z)\d]/)

    static boolean checkAtLeastOneUpperCase(String inputString) {
        if (inputString != null) {
            ONE_UPPER_CASE.matcher(inputString).find()
        } else {
            false
        }
    }

    static boolean checkAtLeastOneLowerCase(String inputString) {
        if (inputString != null) {
            ONE_LOWER_CASE.matcher(inputString).find()
        } else {
            false
        }
    }

    static boolean checkAtLeastOneNumber(String inputString) {
        if (inputString != null) {
            ONE_NUMBER.matcher(inputString).find()
        } else {
            false
        }
    }

    static boolean checkAtLeastOneNonAlphanumeric(String inputString) {
        if (inputString != null) {
            ONE_NON_ALPHANUMERIC.matcher(inputString).find()
        } else {
            false
        }
    }

    static boolean checkPasswordLength(String inputString, int minimumLength) {
        if (inputString != null && minimumLength != null) {
            inputString.length() >= minimumLength
        } else {
            false
        }
    }

}
