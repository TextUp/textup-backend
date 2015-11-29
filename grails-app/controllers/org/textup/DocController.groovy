package org.textup

import groovy.json.JsonSlurper
import org.springframework.security.access.annotation.Secured

@Secured("permitAll")
class DocController {

	def grailsApplication

    def displayDoc() {
    	InputStream input
        try {
            input = servletContext.getResourceAsStream(grailsApplication.config.grails.plugins.restapidoc.outputFileReading)
           	JsonSlurper jsonSlurper = new JsonSlurper()
            jsonSlurper.parseText(input.text)
        }
        finally {
            input.close()
        }
    }
}
