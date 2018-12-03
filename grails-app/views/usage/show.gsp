<!DOCTYPE html>
<html>
    <head>
        <title>${org.name} Usage</title>
        <meta name="layout" content="usage">
    </head>
    <body>
        <div class="usage-header">
            <div class="usage-header__item usage-breadcrumbs">
                <h3 class="usage-breadcrumbs__item">
                    <g:link action="index">${monthString} Overview</g:link>
                </h3>
                <h3 class="usage-breadcrumbs__item">${org.name}</h3>
            </div>
        </div>
        <div class="usage-header usage-header--sticky">
            <div class="usage-header__item usage-header__item--no-shrink">
                <tmpl:charttype id="${org.id}" />
            </div>
            <div class="usage-header__item usage-header__item--no-shrink">
                <tmpl:timeframe monthString="${monthString}"
                    availableMonthStrings="${availableMonthStrings}"
                    id="${org.id}" />
            </div>
        </div>
        <div class="panel panel-default">
            <div class="panel-heading usage-header">
                <div class="usage-header__item">
                    <h4>${org.name} Staff</h4>
                </div>
                <div class="usage-header__item">
                    <tmpl:context
                        totalCost="${staffTotalCost}"
                        usageCost="${staffUsageCost}"
                        callCost="${staffCallCost}"
                        textCost="${staffTextCost}"
                        numPhones="${numStaffPhones}"
                        numActivePhones="${numActiveStaffPhones}"
                        numTexts="${numStaffTexts}"
                        numSegments="${numStaffSegments}"
                        numCalls="${numStaffCalls}"
                        numMinutes="${numStaffMinutes}"
                        numBillableMinutes="${numStaffBillableMinutes}" />
                </div>
            </div>
            <div class="panel-body">
                <div id="staff-chart"></div>
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
                    <h4>${org.name} Teams</h4>
                </div>
                <div class="usage-header__item">
                    <tmpl:context
                        totalCost="${teamTotalCost}"
                        usageCost="${teamUsageCost}"
                        callCost="${teamCallCost}"
                        textCost="${teamTextCost}"
                        numPhones="${numTeamPhones}"
                        numActivePhones="${numActiveTeamPhones}"
                        numTexts="${numTeamTexts}"
                        numSegments="${numTeamSegments}"
                        numCalls="${numTeamCalls}"
                        numMinutes="${numTeamMinutes}"
                        numBillableMinutes="${numTeamBillableMinutes}" />
                </div>
            </div>
            <div class="panel-body">
                <div id="team-chart"></div>
            </div>
            <div class="panel-body">
                <tmpl:teamtable teams="${teams}"
                    title="${org.name} teams - ${monthString}"
                    messageTop="Exported ${currentTime}" />
            </div>
        </div>
    </body>
</html>
