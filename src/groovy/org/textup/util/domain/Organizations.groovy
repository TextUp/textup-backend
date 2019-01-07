package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class Organizations {

    // TODO
    // static namedQueries = {
    //     ilikeForNameAndAddress { String query ->
    //         or {
    //             ilike("name", query)
    //             location { ilike("address", query) }
    //         }
    //         eq("status", OrgStatus.APPROVED)
    //     }
    // }

    // // Static finders
    // // --------------

    // static int countSearch(String query) {
    //     ilikeForNameAndAddress(query).count()
    // }
    // static List<Organization> search(String query, Map params=[:]) {
    //     ilikeForNameAndAddress(query).list(params)
    // }
}
