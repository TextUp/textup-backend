package org.textup.validator

import com.sendgrid.Email
import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
@ToString
@Validateable
class EmailEntity implements CanValidate {

    String name
    String email

    static constraints = {
    	name blank:false, nullable:false
    	email blank:false, nullable:false, email:true
    }

    static Result<EmailEntity> tryCreate(String name, String email) {
        EmailEntity ee1 = new EmailEntity(name: name, email: email)
        DomainUtils.tryValidate(ee1, ResultStatus.CREATED)
    }

    // Methods
    // -------

    Email toSendGridEmail() {
        new Email(email, name)
    }
}
