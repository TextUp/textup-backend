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
    <link rel="stylesheet" type="text/css" href="https://cdn.datatables.net/v/bs/dt-1.10.18/b-1.5.4/b-html5-1.5.4/datatables.min.css"/>
     <g:layoutHead/>
</head>
<body>
	<nav class="navbar navbar-default">
	    <div class="navbar-header">
            <a class="navbar-brand" href="http://www.textup.org" target="_blank">
                <g:img dir="images" file="logo.png" alt="TextUp" height="40"/>
            </a>
            <sec:ifAllGranted roles="ROLE_ADMIN">
                <g:link controller="super" class="navbar-text"><b>Super Dashboard</b></g:link>
                <g:link controller="super" action="rejected" class="navbar-text"><b>Rejected</b></g:link>
                <g:link controller="super" action="approved" class="navbar-text"><b>Approved</b></g:link>
                <g:link controller="usage" class="navbar-text"><b>Usage</b></g:link>
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
	<main class="container">
		<g:layoutBody/>
	</main>
    <script src="https://code.jquery.com/jquery-3.3.1.min.js"
        integrity="sha256-FgpCb/KJQlLNfOu91ta32o/NMZxltwRo8QtmkMRdAu8="
        crossorigin="anonymous"></script>
    <script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.1.36/pdfmake.min.js"></script>
    <script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.1.36/vfs_fonts.js"></script>
    <script type="text/javascript" src="https://cdn.datatables.net/v/bs/dt-1.10.18/b-1.5.4/b-html5-1.5.4/datatables.min.js"></script>
    <asset:javascript src="application.js"/>
</body>
</html>
