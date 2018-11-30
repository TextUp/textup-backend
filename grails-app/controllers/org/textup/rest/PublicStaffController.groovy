package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import org.restapidoc.annotation.*
import org.springframework.security.access.annotation.Secured
import org.textup.*

@GrailsTypeChecked
@RestApi(name="[Public] Staff", description = "Creating new staff members only. Accessible to all.")
@Secured("permitAll")
class PublicStaffController extends StaffController {

    static String namespace = "v1"
    //authService from superclass

    @Override
    protected String getNamespaceAsString() { namespace }

    @Override
    def index() { notAllowed() }
    @Override
    def show() { notAllowed() }

    //Only the save method is available

    @Override
    def update() { notAllowed() }
    @Override
    def delete() { notAllowed() }
}
