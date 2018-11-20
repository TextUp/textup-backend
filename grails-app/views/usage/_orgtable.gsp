<g:if test="${orgs}">
    <div class="table-responsive">
        <table class="table"
            data-order='[[2, "desc"]]'
            data-buttons='[{ "extend": "pdfHtml5", "title": "${title}", "messageTop": "${messageTop}" }]'
            data-columns='[
                null,
                { "className": "text-right" },
                { "className": "text-right" },
                { "className": "text-right" },
                { "className": "text-right" },
                { "className": "text-right" },
                { "className": "text-right" }]'>
            <thead>
                <tr>
                    <th>Org name</th>
                    <th># phones</th>
                    <th># active phones</th>
                    <th># texts</th>
                    <th># billable segments</th>
                    <th># calls</th>
                    <th># billable minutes</th>
                </tr>
            </thead>
            <tbody>
                <g:each var="org" in="${orgs}">
                    <tr>
                        <td>
                            <g:link action="show" id="${org.id}">
                                ${org.name}
                            </g:link>
                        </td>
                        <td>${org.totalNumPhones}</td>
                        <td>${org.activity?.numActivePhones}</td>
                        <td>${org.activity?.numTexts}</td>
                        <td>${org.activity?.numBillableSegments}</td>
                        <td>${org.activity?.numCalls}</td>
                        <td>
                            <g:formatNumber number="${org.activity?.numBillableMinutes}"
                                type="number"
                                minFractionDigits="2"
                                maxFractionDigits="2" />
                        </td>
                    </tr>
                </g:each>
            </tbody>
        </table>
    </div>
</g:if>
<g:else>
    No organizations found for the specified time period
</g:else>
