<!DOCTYPE html>
<html>
    <head>
        <title>TextUp Super</title>
        <meta name="layout" content="main">
        <asset:stylesheet src="super.css"/>
    </head>
    <body>
        <div class="super-container">
            <p class="super-container-title">Unverified Organizations</p>
            <g:if test="${unverifiedOrgs}">
                <g:each var="o" in="${unverifiedOrgs}">
                    <div class="unverified-org">
                        <div class="org-details">
                            <span class="name">${o.name}</span>
                            <span class="address">${o.location.address}</span>
                        </div>
                        <div class="org-admin">
                            <span class="name">${o.admins[0]?.name}</span>
                            <span class="email">${o.admins[0].email}</span>
                        </div>
                        <div class="org-controls">
                            <button class="btn btn-danger">Reject</button>
                            <button class="btn btn-success">Approve</button>
                        </div>
                    </div>
                </g:each>
            </g:if>
            <g:else>
                <p class="super-container-none">No unverified organizations! Hooray!</p>
            </g:else>
        </div>
    </body>
</html>
