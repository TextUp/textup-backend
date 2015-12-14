<!DOCTYPE html>
<!--[if lt IE 7 ]> <html lang="en" class="no-js ie6"> <![endif]-->
<!--[if IE 7 ]>    <html lang="en" class="no-js ie7"> <![endif]-->
<!--[if IE 8 ]>    <html lang="en" class="no-js ie8"> <![endif]-->
<!--[if IE 9 ]>    <html lang="en" class="no-js ie9"> <![endif]-->
<!--[if (gt IE 9)|!(IE)]><!--> <html lang="en" class="no-js"><!--<![endif]-->
<head>
    <title><g:layoutTitle default="TextUp API"/></title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta charset="UTF-8">
    <asset:stylesheet src="application.css"/>
    <g:layoutHead/>
</head>
<body>
	<nav class="navbar navbar-default">
	    <div class="navbar-header">
            <a class="navbar-brand" href="http://www.textup.org" target="_blank">
                <g:img dir="images" file="logo.png" alt="TextUp" height="40"/>
            </a>
            <sec:ifAllGranted roles="ROLE_ADMIN">
                <g:link controller="super" action="index" class="navbar-text"><b>Super Dashboard</b></g:link>
                <g:link controller="super" action="rejected" class="navbar-text"><b>Rejected</b></g:link>
                <g:link controller="super" action="approved" class="navbar-text"><b>Approved</b></g:link>
            </sec:ifAllGranted>
            <sec:ifNotGranted roles="ROLE_ADMIN">
                <a class="navbar-text" href="/">
                    <b>API Documentation</b>
                </a>
            </sec:ifNotGranted>
	    </div>
	    <div class="navbar-right">
            <sec:ifAllGranted roles="ROLE_ADMIN">
                <g:link controller="super" action="settings" class="btn btn-link navbar-btn">Settings</g:link>
                <g:link controller="super" action="logout" class="btn btn-link navbar-btn">Logout</g:link>
            </sec:ifAllGranted>
            <sec:ifNotGranted roles="ROLE_ADMIN">
                <g:link controller="super" class="btn btn-link navbar-btn">Super</g:link>
                <a href="https://app.textup.org" target="_blank" class="btn btn-success navbar-btn">Launch</a>
            </sec:ifNotGranted>
		</div>
	</nav>
	<main>
		<g:layoutBody/>
	</main>
    <asset:javascript src="application.js"/>
</body>
</html>
