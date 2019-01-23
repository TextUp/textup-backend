<g:if test="${teams}">
    <div class="table-responsive table--horizontal-scroll">
        <table class="table table-hover"
            data-order='[[2, "desc"]]'
            data-buttons='[{
                "extend": "pdfHtml5",
                "text": "Download table data",
                "title": "${title}",
                "messageTop": "${messageTop}",
                "orientation": "landscape",
                "pageSize": "LEGAL"
            }]'
            data-columns='[
                {
                    "className": "number-detail",
                    "orderable": false,
                    "data": null,
                    "defaultContent": ""
                },
                { "className": "text--bold" },
                { "className": "text-right table-cell__highlight" },
                { "className": "text-right table-cell__highlight" },
                { "className": "text-right" },
                null,
                { "className": "text-right table-cell__highlight" },
                { "className": "text-right" },
                { "className": "text-right" },
                { "className": "text-right" },
                { "className": "text-right table-cell__highlight" },
                { "className": "text-right" },
                { "className": "text-right" },
                { "className": "text-right" },
                { "className": "text--bold" }]'>
            <thead>
                <tr>
                    <th></th>
                    <th>Name</th>
                    <th>Invoice total</th>
                    <th>$ fees for usage</th>
                    <th># staff</th>
                    <th>TextUp phone</th>

                    <th>$ billable segments</th>
                    <th># notification texts</th>
                    <th># texts</th>
                    <th># segments</th>

                    <th>$ billable minutes</th>
                    <th># voicemail minutes</th>
                    <th># calls</th>
                    <th># minutes</th>

                    <th>Name</th>
                </tr>
            </thead>
            <tbody>
                <g:each var="team" in="${teams}">
                    <tr>
                        <td data-number="${team.number}"></td>
                        <td>${team.name}</td>
                        <td>
                            $<g:formatNumber number="${team.totalCost}"
                                type="number"
                                minFractionDigits="2"
                                maxFractionDigits="2" />
                        </td>
                        <td>
                            $<g:formatNumber number="${team.activity?.cost}"
                                type="number"
                                minFractionDigits="2"
                                maxFractionDigits="2" />
                        </td>
                        <td>${team.numStaff}</td>
                        <td>${team.numbers}</td>

                        <td>
                            $<g:formatNumber number="${team.activity?.textCost}"
                                type="number"
                                minFractionDigits="2"
                                maxFractionDigits="2" />
                        </td>
                        <td>${team.activity?.numNotificationTexts}</td>
                        <td>${team.activity?.numTexts}</td>
                        <td>${team.activity?.numSegments}</td>

                        <td>
                            $<g:formatNumber number="${team.activity?.callCost}"
                                type="number"
                                minFractionDigits="2"
                                maxFractionDigits="2" />
                        </td>
                        <td>
                            <g:formatNumber number="${team.activity?.numVoicemailMinutes}"
                                type="number"
                                minFractionDigits="2"
                                maxFractionDigits="2" />
                        </td>
                        <td>${team.activity?.numCalls}</td>
                        <td>
                            <g:formatNumber number="${team.activity?.numMinutes}"
                                type="number"
                                minFractionDigits="2"
                                maxFractionDigits="2" />
                        </td>

                        <td>${team.name}</td>
                    </tr>
                </g:each>
            </tbody>
        </table>
    </div>
</g:if>
<g:else>
    No team phones found for the specified time period
</g:else>
