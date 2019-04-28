package org.textup.constraint

import grails.compiler.GrailsTypeChecked
import groovy.transform.TypeCheckingMode
import java.util.regex.Pattern
import org.codehaus.groovy.grails.validation.AbstractVetoingConstraint
import org.codehaus.groovy.grails.validation.ConstrainedProperty
import org.springframework.validation.Errors

// Example constraint: https://github.com/grails/grails-core/blob/fa4a156a1a9da0a238a1a82e05a2a4880e0511a9/grails-validation/src/main/groovy/org/grails/validation/MatchesConstraint.java
// Example codes: https://github.com/grails/grails-core/blob/fa4a156a1a9da0a238a1a82e05a2a4880e0511a9/grails-validation/src/main/groovy/grails/validation/ConstrainedProperty.java

@GrailsTypeChecked
class PhoneNumberConstraint extends AbstractVetoingConstraint {

    static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile(/^(\d){10}$/)
    static final String DEFAULT_CODE = "default.invalid.phone.number.message"
    static final String NAME = "phoneNumber"
    static final String PARAM_ALLOW_BLANK = "allowBlank"

    @Override
    String getName() { NAME }

    @Override
    boolean supports(Class type) { String.isAssignableFrom(type) }

    @Override
    void setParameter(Object constraintParamValue) {
        if (constraintParamValue instanceof Boolean || constraintParamValue == PARAM_ALLOW_BLANK) {
            super.setParameter(constraintParamValue)
        }
        else {
            throw new IllegalArgumentException("""
                Parameter for constraint [$name] of property [$constraintPropertyName] of class
                [$constraintOwningClass] must be a Boolean or `${PARAM_ALLOW_BLANK}`
            """)
        }
    }

    @Override
    protected boolean skipNullValues() { true }

    @Override
    protected boolean processValidateWithVetoing(Object target, Object propertyValue, Errors errors) {
        boolean shouldVeto = false
        if (parameter == true || shouldValidateIfAllowingBlank(propertyValue)) {
            shouldVeto = !PHONE_NUMBER_PATTERN.matcher(propertyValue?.toString()).matches()
            if (shouldVeto) {
                rejectValue(target,
                    errors,
                    DEFAULT_CODE,
                    NAME + ConstrainedProperty.INVALID_SUFFIX,
                    [constraintPropertyName, constraintOwningClass, propertyValue] as Object[])
            }
        }
        shouldVeto
    }

    // If allowing blank, then we will allow nulls and empty strings. Any other string value
    // will undergo validation to see if it is a valid phone number
    protected boolean shouldValidateIfAllowingBlank(Object propertyValue) {
        parameter == PARAM_ALLOW_BLANK && propertyValue != "" && propertyValue != null
    }
}
