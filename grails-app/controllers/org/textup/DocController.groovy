package org.textup

import groovy.json.JsonSlurper
import org.springframework.security.access.annotation.Secured
import grails.compiler.GrailsCompileStatic
import org.codehaus.groovy.grails.commons.GrailsApplication

@GrailsCompileStatic
@Secured("permitAll")
class DocController {

	GrailsApplication grailsApplication

    def displayDoc() {
        String outputFile = grailsApplication
            .flatConfig["grails.plugins.restapidoc.outputFileReading"]
    	InputStream input
        try {
            input = servletContext.getResourceAsStream(outputFile)
           	JsonSlurper jsonSlurper = new JsonSlurper()
            jsonSlurper.parseText(input.text)
        }
        finally {
            input.close()
        }
    }
}
