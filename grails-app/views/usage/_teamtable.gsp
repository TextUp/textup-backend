<g:if test="${teams}">
    <div class="table-responsive">
        <table class="table"
            data-order='[[2, "desc"]]'
            data-buttons='[{
                "extend": "pdfHtml5",
                "text": "Download table data",
                "title": "${title}",
                "messageTop": "${messageTop}"
            }]'
            data-columns='[
                {
                    "className": "number-detail",
                    "orderable": false,
                    "data": null,
                    "defaultContent": ""
                },
                null,
                { "className": "text-right" },
                null,
                { "className": "text-right" },
                { "className": "text-right" },
                { "className": "text-right" },
                { "className": "text-right" }]'>
            <thead>
                <tr>
                    <th></th>
                    <th>Name</th>
                    <th># staff</th>
                    <th>TextUp phone</th>
                    <th># texts</th>
                    <th># billable segments</th>
                    <th># calls</th>
                    <th># billable minutes</th>
                </tr>
            </thead>
            <tbody>
                <g:each var="team" in="${teams}">
                    <tr>
                        <td data-number="${team.number}"></td>
                        <td>${team.name}</td>
                        <td>${team.numStaff}</td>
                        <td>${team.phoneNumber}</td>
                        <td>${team.activity?.numTexts}</td>
                        <td>${team.activity?.numBillableSegments}</td>
                        <td>${team.activity?.numCalls}</td>
                        <td>
                            <g:formatNumber number="${team.activity?.numBillableMinutes}"
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
    No team phones found for the specified time period
</g:else>
