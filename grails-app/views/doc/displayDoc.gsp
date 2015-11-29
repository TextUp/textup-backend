<!DOCTYPE html>
<html>
	<head>
		<title>TextUp API</title>
		<meta name="layout" content="main">
	</head>
	<body>
		${basePath}

		<div class="doc-sidebar">
			<h3>Endpoints</h3>
			<ul>
				<g:each var="e" in="${apis}">
					<li class="sidebar-${e.jsondocId}">${e.name}</li>
				</g:each>
			</ul>
			<h3>Models</h3>
			<ul>
				<g:each var="o" in="${objects}">
					<li class="sidebar-${o.jsondocId}">${o.name}</li>
				</g:each>
			</ul>
		</div>
		<g:each var="e" in="${apis}">
			<div class="doc-detail detail-${e.jsondocId}">
				<li class="sidebar-${e.jsondocId}">${e.name}</li>
				
			</div>
		</g:each>
	</body>
</html>
