<!DOCTYPE html>
<html>
    <head>
        <title>${org.name} Usage</title>
        <meta name="layout" content="main">
        <asset:stylesheet src="usage.css"/>
    </head>
    <body>
        <div class="usage-header usage-header--main">
            <div class="usage-header__item usage-breadcrumbs">
                <h3 class="usage-breadcrumbs__item">
                    <g:link action="index">${monthString} Overview</g:link>
                </h3>
                <h3 class="usage-breadcrumbs__item">${org.name} Usage</h3>
            </div>
            <div class="usage-header__item usage-header__item--no-shrink">
                <tmpl:timeframe timeframe="${timeframe}" 
                    allowedYears="${allowedYears}" 
                    id="${org.id}" />
            </div>
        </div>
        <div class="panel panel-default">
            <div class="panel-heading usage-header">
                <div class="usage-header__item">
                    <h4>Staff</h4>
                </div>
                <div class="usage-header__item">
                    <tmpl:context numPhones="${numStaffPhones}"
                        numSegments="${numStaffSegments}"
                        numMinutes="${numStaffMinutes}" />
                </div>
            </div>
            <div class="panel-body">
                <tmpl:stafftable staffs="${staffs}"
                    title="${org.name} staff - ${monthString}"
                    messageTop="Exported ${currentTime}" />
            </div>
        </div>
        <div class="panel panel-default">
            <div class="panel-heading usage-header">
                <div class="usage-header__item">
                    <h4>Teams</h4>
                </div>
                <div class="usage-header__item">
                    <tmpl:context numPhones="${numTeamPhones}"
                        numSegments="${numTeamSegments}"
                        numMinutes="${numTeamMinutes}" />
                </div>
            </div>
            <div class="panel-body">
                <tmpl:teamtable teams="${teams}"
                    title="${org.name} teams - ${monthString}"
                    messageTop="Exported ${currentTime}" />
            </div>
        </div>
    </body>
</html>
