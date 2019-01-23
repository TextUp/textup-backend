package org.textup.infrastructure

import grails.compiler.GrailsTypeChecked
import groovy.transform.TypeCheckingMode
import org.codehaus.groovy.grails.validation.AbstractVetoingConstraint
import grails.validation.ConstrainedProperty
import org.springframework.validation.Errors

// Example constraint: https://github.com/grails/grails-core/blob/fa4a156a1a9da0a238a1a82e05a2a4880e0511a9/grails-validation/src/main/groovy/org/grails/validation/MatchesConstraint.java
// Example codes: https://github.com/grails/grails-core/blob/fa4a156a1a9da0a238a1a82e05a2a4880e0511a9/grails-validation/src/main/groovy/grails/validation/ConstrainedProperty.java

@GrailsTypeChecked
class PhoneNumberConstraint extends AbstractVetoingConstraint {

    static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile(/^(\d){10}$/)
    static final String DEFAULT_CODE = "default.invalid.phone.number.message"
    static final String NAME = "phoneNumber"

    @Override
    String getName() { NAME }

    @Override
    boolean supports(Class type) { String.isAssignableFrom(type) }

    @Override
    void setParameter(Object constraintParamValue) {
        if (!(constraintParamValue instanceof Boolean)) {
            throw new IllegalArgumentException("""Parameter for constraint [$name] of property
                [$constraintPropertyName] of class [$constraintOwningClass] must be a Boolean.""")
        }
        super.setParameter(constraintParamValue)
    }

    @Override
    protected boolean skipNullValues() { true }

    @Override
    protected boolean processValidateWithVetoing(Object target, Object propertyValue, Errors errors) {
        boolean shouldVeto = !PHONE_NUMBER_PATTERN.matcher(propertyValue?.toString()).matches()
        if (shouldVeto) {
            rejectValue(target,
                errors,
                DEFAULT_CODE,
                NAME + ConstrainedProperty.INVALID_SUFFIX,
                [constraintPropertyName, constraintOwningClass, propertyValue] as Object[])
        }
        shouldVeto
    }
}
