package org.textup.validator

import com.sendgrid.Email
import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.transform.TupleConstructor
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
@ToString
@TupleConstructor(includeFields = true)
@Validateable
class EmailEntity implements CanValidate {

    final String name
    final String email

    static constraints = {
    	name blank:false, nullable:false
    	email blank:false, nullable:false, email:true
    }

    static Result<EmailEntity> tryCreate(String name, String email) {
        EmailEntity emailEntity1 = new EmailEntity(name, email)
        DomainUtils.tryValidate(emailEntity1, ResultStatus.CREATED)
    }

    // Methods
    // -------

    Email toSendGridEmail() {
        new Email(email, name)
    }
}
