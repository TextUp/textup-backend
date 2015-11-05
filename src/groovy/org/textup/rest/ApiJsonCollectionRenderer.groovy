package org.textup.rest

import grails.rest.render.ContainerRenderer 
import org.codehaus.groovy.grails.web.mime.MimeType

class ApiJsonCollectionRenderer extends ApiJsonRenderer implements ContainerRenderer {
    final Class componentType 

    public ApiJsonCollectionRenderer(Class componentType) {
        super(Collection)
        this.componentType = componentType
    }

    public ApiJsonCollectionRenderer(Class componentType, MimeType... mimeTypes) {
        super(Collection, mimeTypes)
        this.componentType = componentType
    }
}