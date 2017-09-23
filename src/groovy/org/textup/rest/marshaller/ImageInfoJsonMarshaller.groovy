package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import grails.plugin.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*
import org.textup.validator.ImageInfo

@GrailsCompileStatic
class ImageInfoJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, ImageInfo image ->

        Map json = [:]
        json.with {
            key = image.key
            link = image.link
        }
        json
    }

    ImageInfoJsonMarshaller() {
        super(ImageInfo, marshalClosure)
    }
}
