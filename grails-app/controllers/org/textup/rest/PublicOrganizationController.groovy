package org.textup.rest

import grails.converters.JSON
import org.restapidoc.annotation.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
@RestApi(name="[Public] Organization", description = "Listing and showing organizations. Accessible to all.")
@Secured("permitAll")
class PublicOrganizationController extends OrganizationController {

    static String namespace = "v1"
    //authService from superclass

    @Override
    protected String getNamespaceAsString() { namespace }

    @Override
    def update() { notAllowed() }
    @Override
    def delete() { notAllowed() }
}
