package org.textup.validator

import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
@EqualsAndHashCode
@ToString
@Validateable
class ImageInfo {

    String key
    String link

    static constraints = {
        key blank:false, nullable:false
        link blank:false, nullable:false, url:true
    }
}