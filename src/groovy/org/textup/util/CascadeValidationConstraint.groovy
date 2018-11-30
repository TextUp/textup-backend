package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.transform.TypeCheckingMode
import org.codehaus.groovy.grails.validation.AbstractVetoingConstraint
import org.springframework.validation.BindingResult
import org.springframework.validation.Errors
import org.springframework.validation.FieldError

// For cascading validation
// Original blog post --> https://asoftwareguy.com/2013/07/01/grails-cascade-validation-for-pogos/
// Plugin adapted from blog post --> https://github.com/Grails-Plugin-Consortium/grails-cascade-validation
// AbstractVetoingConstraint --> https://github.com/grails/grails-core/blob/fa4a156a1a9da0a238a1a82e05a2a4880e0511a9/grails-validation/src/main/groovy/grails/validation/AbstractVetoingConstraint.java

@GrailsTypeChecked
class CascadeValidationConstraint extends AbstractVetoingConstraint {

    static final String NAME = "cascadeValidation"

    @Override
    String getName() { NAME }

    @Override
    boolean supports(Class type) {
        // is either a collection or a object that is directly validateable
        Collection.isAssignableFrom(type) || type.metaClass.respondsTo(type, "validate")
    }

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
        boolean shouldVetoSubsequent = false
        if (propertyValue instanceof Collection) {
            (propertyValue as Collection).eachWithIndex { Object obj, int index ->
                shouldVetoSubsequent =
                    doValidate(target, obj, errors, index) || shouldVetoSubsequent
            }
        }
        else {
            shouldVetoSubsequent = doValidate(target, propertyValue, errors)
        }
        shouldVetoSubsequent
    }

    // Helpers
    // -------

    protected boolean doValidate(Object target, Object propertyValue, Errors errors, Integer index = null) {
        if (!doValidateHelper(propertyValue)) { return false }
        // if not valid, copy the child's field errors into the parent's errors object
        String objectName = getErrorsHelper(target).objectName
        BindingResult bindingResult = errors as BindingResult
        getErrorsHelper(propertyValue).fieldErrors.each { FieldError fError ->
            String field = (index != null) ?
                "${propertyName}.${index}.${fError.field}" :
                "${propertyName}.${fError.field}"
            bindingResult.addError(new FieldError(objectName,
                field,
                fError.rejectedValue,
                fError.isBindingFailure(),
                fError.codes,
                fError.arguments,
                fError.defaultMessage))
        }
        true // true = should veto because we have validation errors here
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected Errors getErrorsHelper(Object obj) { obj.errors }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected boolean doValidateHelper(Object propertyValue) {
        if (!propertyValue.respondsTo('validate')) {
            throw new NoSuchMethodException("""Error validating property [$constraintPropertyName].
                Unable to apply `cascade` constraint to [${propertyValue.class}] because the object
                does not have a validate() method""")
        }
        if (propertyValue.validate()) {
            return false // false = should not veto
        }
        else { return true } // true = should veto and add in errors
    }
}
