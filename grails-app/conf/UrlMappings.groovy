class UrlMappings {

	static mappings = {
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        "/"(view:"/index")
        "500"(view:'/error')
        //login endpoint is /login (provided by Spring Security REST)

        // //password reset option. index and delete not allowed
        // "/reset"(controller:"passwordReset", action:"index", method:"GET") { format = "json" }
        // "/reset"(controller:"passwordReset", action:"delete", method:"DELETE") { format = "json" }
        // "/reset"(controller:"passwordReset", action:"resetPassword", method:"PUT") { format = "json" }
        // "/reset"(controller:"passwordReset", action:"requestReset", method:"POST") { format = "json" }

        group("/v1") {

	        ////////////////
        	// Public API //
	        ////////////////

	        "/public/organizations"(resources:"publicOrganization", namespace:"v1") {
                format = "json"
            }
            "/public/staff"(resources:"publicStaff", namespace:"v1") {
                format = "json"
            }
            "/public/records"(resources:"publicRecord", namespace:"v1") {
                format = "json"
            }
            // "/public/numbers"(resources:"publicNumber", namespace:"v1") {
            //     format = "json"
            // }

	        ////////////////////
	        // Restricted API //
	        ////////////////////

        	"/organizations"(resources:"organization", namespace:"v1") {
                format = "json"
            }
            "/staff"(resources:"staff", namespace:"v1") {
            	format = "json"
            }
            "/teams"(resources:"team", namespace:"v1") {
            	format = "json"
            }
            "/records"(resources:"record", namespace:"v1") {
            	format = "json"
            }
            "/contacts"(resources:"contact", namespace:"v1") {
            	format = "json"
            }
            "/tags"(resources:"tag", namespace:"v1") {
            	format = "json"
            }
        }
	}
}
