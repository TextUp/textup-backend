package org.textup.validator

import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import grails.compiler.GrailsTypeChecked
import com.sendgrid.Email

@GrailsTypeChecked
@EqualsAndHashCode
@ToString
@Validateable
class EmailEntity implements Validateable {

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
