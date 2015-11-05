package org.textup.rest

import grails.converters.JSON
import org.restapidoc.annotation.*
import org.springframework.security.access.annotation.Secured
import org.textup.*

@RestApi(name="[Public] Staff", description = "Creating new staff members only. Accessible to all.")
@Secured("permitAll")
class PublicStaffController extends StaffController {

    static namespace = "v1"
    //authService from superclass

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
