package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode(includes = ["id"])
@GrailsTypeChecked
class StaffRole implements Serializable, CanSave<StaffRole> {

	// Need to declare id for it to be considered in equality operator
    // see: https://stokito.wordpress.com/2014/12/19/equalsandhashcode-on-grails-domains/
    Long id

	private static final long serialVersionUID = 1

	Staff staff
	Role role

	static mapping = {
		id composite: ["role", "staff"]
		version false
	}
	static constraints = {
		role validator: { Role val, StaffRole obj ->
			if (obj.staff && Utils.<Boolean>doWithoutFlush {
					StaffRole.countByStaffAndRole(obj.staff, val) > 0
				}) {
				return ["staffRole.role.exists", obj.staff.id, val?.authority]
			}
		}
	}

	static Result<StaffRole> tryCreate(Staff staff, Role role) {
		DomainUtils.trySave(new StaffRole(staff: staff, role: role), ResultStatus.CREATED)
    }
}
