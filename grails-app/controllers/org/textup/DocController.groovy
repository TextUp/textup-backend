package org.textup

class DocController {

	def grailsApplication

    def displayDoc() {
    	InputStream input
        try {
            input = servletContext.getResourceAsStream(grailsApplication.mergedConfig.grails.plugins.restapidoc.outputFileReading)
           	JsonSlurper jsonSlurper = new JsonSlurper()
            jsonSlurper.parseText(input.text)
        }
        finally {
            input.close()
        }
    }
}
