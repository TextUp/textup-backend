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
                <g:link controller="super" class="navbar-text"><b>Pending Orgs</b></g:link>
                <g:link controller="super" action="rejected" class="navbar-text"><b>Rejected Orgs</b></g:link>
                <g:link controller="super" action="approved" class="navbar-text"><b>Approved Orgs</b></g:link>
                <g:link controller="usage" class="navbar-text"><b>Usage &amp; Costs</b></g:link>
            </sec:ifAllGranted>
	    </div>
	    <div class="navbar-right">
            <sec:ifAllGranted roles="ROLE_ADMIN">
                <g:link controller="super" action="settings" class="btn btn-link navbar-btn">Settings</g:link>
                <g:link controller="super" action="logout" class="btn btn-link navbar-btn">Logout</g:link>
            </sec:ifAllGranted>
            <sec:ifNotGranted roles="ROLE_ADMIN">
                <a href="https://app.textup.org" target="_blank" class="btn btn-success navbar-btn">Launch</a>
            </sec:ifNotGranted>
		</div>
	</nav>
	<main>
		<g:layoutBody/>
	</main>
    <script src="https://code.jquery.com/jquery-3.3.1.min.js"
        integrity="sha256-FgpCb/KJQlLNfOu91ta32o/NMZxltwRo8QtmkMRdAu8="
        crossorigin="anonymous"></script>
    <asset:javascript src="application.js"/>
    <g:pageProperty name="page.assets"/>
</body>
</html>
