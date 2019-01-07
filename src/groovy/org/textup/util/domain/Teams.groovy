package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class Teams {

    // TODO from Team model
    // static namedQueries = {
    //     forStaffs { Collection<Staff> staffs ->
    //         eq("isDeleted", false)
    //         members { CriteriaUtils.inList(delegate, "id", staffs*.id) }
    //     }
    // }

    // static List<Team> listForStaffs(Collection<Staff> staffs, Map params=[:]) {
    //     forStaffs(staffs).list(params)
    // }

    // TODO from Staff model
    // int countTeams() {
    //     Team.forStaffs([this]).count()
    // }

    // List<Team> getTeams(Map params=[:]) {
    //     Team.forStaffs([this]).list(params)
    // }

    static DetachedCriteria<Team> forStaffId(Long staffId) {
        new DetachedCriteria(Team).build {

        }
    }
}
