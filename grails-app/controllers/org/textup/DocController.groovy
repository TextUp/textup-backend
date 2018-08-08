package org.textup

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
            // when running functional tests on CI server, this file isn't generated until deployment
            // therefore, just return an empty JSON string if no output file yet
            String stringData = input ? input.text : ""
            Helpers.toJson(stringData)
        }
        finally {
            input?.close()
        }
    }
}
