<!DOCTYPE html>
<html>
	<head>
		<title>TextUp API</title>
		<meta name="layout" content="main">
		<asset:stylesheet src="docs.css"/>
	</head>
	<body>
		<section class="doc-sidebar">
			<div class="doc-sidebar-scroll custom-scroll">
				<h4 class="scroll-title">Endpoints</h4>
			    <ul class="scroll-list">
			        <g:each var="e" in="${apis}">
			        	<li><a href="#" id="${e.jsondocId}" class="sidebar-item" rel="endpoint">${e.name}</a></li>
					</g:each>
			    </ul>
			    <h4 class="scroll-title">Models</h4>
			    <ul class="scroll-list">
			        <g:each var="o" in="${objects}">
			        	<li><a href="#" id="${o.jsondocId}" class="sidebar-item" rel="model">${o.name}</a></li>
					</g:each>
			    </ul>
			</div>
		</section>
		<section class="doc-detail">
			<g:each var="e" in="${apis}">
				<div class="detail-item detail-${e.jsondocId}">
					<p class="detail-title">
						<span>Endpoint</span> ${e.name}
					</p>
					<p class="detail-description">${e.description}</p>
					<g:each var="m" in="${e.methods}">
						<div class="detail-method">
							<div class="detail-method-title">
								<span class="verb ${m.verb.toLowerCase()}">${m.verb}</span>
								<span class="methodName">${m.methodName}</span>
								<span class="path">${m.path}</span>
							</div>
							<div class="detail-method-body">
								<p class="description">${m.description}</p>
								<table class="table table-bordered">
									<tr>
										<g:if test="${m.bodyobject}">
											<td><b>INPUT</b></td>
											<td>${m.bodyobject.object}</td>
										</g:if>
										<g:if test="${m.response}">
											<td><b>OUTPUT</b></td>
											<td>${m.response.object}</td>
										</g:if>
									</tr>
								</table>
								<g:if test="${m.queryparameters}">
									<p class="section-title">Query Parameters</p>
									<table class="table table-condensed">
										<tr>
											<th>Name</th>
											<th>Type</th>
											<th>Required</th>
											<th>Description</th>
										</tr>
										<g:each var="q" in="${m.queryparameters}">
											<tr class="${q.required == 'true' ? 'info' : ''}">
												<td>${q.name}</td>
												<td>${q.type}</td>
												<td><span class="glyphicon ${q.required == 'true' ? 'glyphicon-ok-sign' : ''}"></span></td>
												<td>${q.description}</td>
											</tr>
										</g:each>
									</table>
								</g:if>
								<g:if test="${m.pathparameters}">
									<p class="section-title">Request Parameters</p>
									<table class="table table-condensed">
										<tr>
											<th>Name</th>
											<th>Type</th>
											<th>Required</th>
											<th>Description</th>
										</tr>
										<g:each var="p" in="${m.pathparameters}">
											<tr class="${p.required == 'true' ? 'info' : ''}">
												<td>${p.name}</td>
												<td>${p.type}</td>
												<td><span class="glyphicon ${p.required == 'true' ? 'glyphicon-ok-sign' : ''}"></span></td>
												<td>${p.description}</td>
											</tr>
										</g:each>
									</table>
								</g:if>
								<g:if test="${m.apierrors}">
									<p class="section-title">Errors</p>
									<table class="table table-condensed">
										<tr>
											<th>Code</th>
											<th>Description</th>
										</tr>
										<g:each var="a" in="${m.apierrors}">
											<tr>
												<td><b>${a.code}</b></td>
												<td>${a.description}</td>
											</tr>
										</g:each>
									</table>
								</g:if>
							</div>
						</div>
					</g:each>
				</div>
			</g:each>
			<g:each var="o" in="${objects}">
				<div class="detail-item detail-${o.jsondocId}">
					<p class="detail-title">
						<span>Model</span> ${o.name}
					</p>
					<p class="detail-description">${o.description}</p>
					<table class="table table-condensed">
						<tr>
							<th>Name</th>
							<th>Type</th>
							<th>Required</th>
							<th>Present In Response</th>
						</tr>
						<g:each var="f" in="${o.fields}">
							<tr class="${f.useForCreation ? 'info' : ''}">
								<td><b>${f.name.toUpperCase()}</b></td>
								<td>${f.type}</td>
								<td><span class="glyphicon ${f.useForCreation ? 'glyphicon-ok-sign' : ''}"></span></td>
								<td><span class="glyphicon ${f.presentInResponse ? 'glyphicon-ok-sign' : ''}"></span></td>
							</tr>
							<tr>
								<td class="field-description" colspan="4">${f.description}</td>
							</tr>
						</g:each>
					</table>
				</div>
			</g:each>
		</section>
	</body>
</html>
