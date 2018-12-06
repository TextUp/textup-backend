class UrlMappings {

    /*
    NOTE: any mapping updates here need to be reflected in the `resolveClassToResourceName`
    method in BaseController to ensure working url link rendering in the API output
     */

	static mappings = {
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        //login endpoint is /login (provided by Spring Security REST)
        "/"(controller:"doc", action:"displayDoc")
        "500"(view:'/error')

        // validate both login credentials and lock codes
        "/validate"(resources:"validate") { format = "json" }

        //password reset option. index and delete not allowed
        "/reset"(controller:"passwordReset", action:"index", method:"GET") { format = "json" }
        "/reset"(controller:"passwordReset", action:"delete", method:"DELETE") { format = "json" }
        "/reset"(controller:"passwordReset", action:"resetPassword", method:"PUT") { format = "json" }
        "/reset"(controller:"passwordReset", action:"requestReset", method:"POST") { format = "json" }

        group("/v1") {

	        ////////////////
        	// Public API //
	        ////////////////

	        "/public/organizations"(resources:"publicOrganization", namespace:"v1") { format = "json" }
            "/public/staff"(resources:"publicStaff", namespace:"v1") { format = "json" }
            //for webhook requests from Twilio
            "/public/records"(resources:"publicRecord", namespace:"v1") { format = "json" }
            // GET for looking up details about a notification
            "/public/notifications"(resources:"notify", namespace:"v1") { format = "json" }

            ////////////////////////////
            // Restricted Utility API //
            ////////////////////////////

            //authenticating private channels with Pusher
            "/sockets"(resources:"socket", namespace:"v1") { format = "json" }
            //GET (index) for looking up available TextUp numbers from twilio
            //GET (show) for determining validity of provided phone number
            //POST for validating ownership of phone numbers
            "/numbers"(resources:"number", namespace:"v1") { format = "json" }

	        ////////////////////
	        // Restricted API //
	        ////////////////////

        	"/organizations"(resources:"organization", namespace:"v1") { format = "json" }
            "/staff"(resources:"staff", namespace:"v1") { format = "json" }
            "/teams"(resources:"team", namespace:"v1") { format = "json" }
            // took out the json format closure because we need to support both json and pdf here
            "/records"(resources:"record", namespace:"v1")
            "/future-messages"(resources:"futureMessage", namespace:"v1") { format = "json" }
            "/contacts"(resources:"contact", namespace:"v1") { format = "json" }
            "/tags"(resources:"tag", namespace:"v1") { format = "json" }
            "/sessions"(resources:"session", namespace:"v1") { format = "json" }
            "/announcements"(resources:"announcement", namespace:"v1") { format = "json" }
        }
	}
}
