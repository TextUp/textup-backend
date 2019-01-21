class UrlMappings {

    // [NOTE]: any mapping updates here need to be reflected in the `resolveClassToResourceName`
    // method in BaseController to ensure working url link rendering in the API output

	static mappings = {
        "/$controller/$action?/$id?(.$format)?"{
            constraints { }
        }

        //login endpoint is `/login` (provided by Spring Security REST)
        "/"(controller: "super")
        "500"(view: "/error")

        group("/v1") {

        	// Public
	        // ------

            "/public/notifications"(resources: "notify")
            "/public/organizations"(resources: "organization")
            "/public/records"(resources: "publicRecord")
            "/public/reset"(resources: "passwordReset")
            "/public/staff"(resources: "staff")

            // Restricted utilities
            // --------------------

            "/numbers"(resources: "number")
            "/sockets"(resources: "socket")
            "/validate"(resources: "validate")

            // Restricted
            // ----------

            "/announcements"(resources: "announcement")
            "/contacts"(resources: "contact")
            "/future-messages"(resources: "futureMessage")
            "/organizations"(resources: "organization")
            "/records"(resources: "record")
            "/sessions"(resources: "session")
            "/staff"(resources: "staff")
            "/tags"(resources: "tag")
            "/teams"(resources: "team")
        }
	}
}
