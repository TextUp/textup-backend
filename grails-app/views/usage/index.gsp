<!DOCTYPE html>
<html>
    <head>
        <title>Usage Overview</title>
        <meta name="layout" content="usage">
    </head>
    <body>
        <div class="usage-header">
            <div class="usage-header__item usage-breadcrumbs">
                <h3 class="usage-breadcrumbs__item">${monthString} Overview</h3>
            </div>
        </div>
        <div class="usage-header usage-header--sticky">
            <div class="usage-header__item usage-header__item--no-shrink">
                <tmpl:charttype/>
            </div>
            <div class="usage-header__item usage-header__item--no-shrink">
                <tmpl:timeframe monthString="${monthString}"
                    availableMonthStrings="${availableMonthStrings}" />
            </div>
        </div>
        <div class="panel panel-default">
            <div class="panel-heading usage-header">
                <div class="usage-header__item">
                    <h4>Staff Overview</h4>
                </div>
                <div class="usage-header__item">
                    <tmpl:context numPhones="${numStaffPhones}"
                        numActivePhones="${numActiveStaffPhones}"
                        numSegments="${numStaffSegments}"
                        numMinutes="${numStaffMinutes}" />
                </div>
            </div>
            <div class="panel-body">
                <div id="staff-chart"></div>
            </div>
            <div class="panel-body">
                <tmpl:orgtable orgs="${staffOrgs}"
                    title="All staff by organization - ${monthString}"
                    messageTop="Exported ${currentTime}" />
            </div>
        </div>
        <div class="panel panel-default">
            <div class="panel-heading usage-header">
                <div class="usage-header__item">
                    <h4>Teams Overview</h4>
                </div>
                <div class="usage-header__item">
                    <tmpl:context numPhones="${numTeamPhones}"
                        numActivePhones="${numActiveTeamPhones}"
                        numSegments="${numTeamSegments}"
                        numMinutes="${numTeamMinutes}" />
                </div>
            </div>
            <div class="panel-body">
                <div id="team-chart"></div>
            </div>
            <div class="panel-body">
                <tmpl:orgtable orgs="${teamOrgs}"
                    title="All teams by organization - ${monthString}"
                    messageTop="Exported ${currentTime}" />
            </div>
        </div>
    </body>
</html>
