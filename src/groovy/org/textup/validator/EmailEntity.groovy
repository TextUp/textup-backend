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
class EmailEntity {

    String name
    String email

    static constraints = {
    	name blank:false, nullable:false
    	email blank:false, nullable:false, email:true
    }

    // Methods
    // -------

    Email toEmail() {
        new Email(email, name)
    }
}
