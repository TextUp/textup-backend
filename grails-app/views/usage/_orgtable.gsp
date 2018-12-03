<g:if test="${orgs}">
    <div class="table-responsive table--horizontal-scroll">
        <table class="table"
            data-order='[[1, "desc"]]'
            data-buttons='[{
                "extend": "pdfHtml5",
                "text": "Download table data",
                "title": "${title}",
                "messageTop": "${messageTop}",
                "orientation": "landscape",
                "pageSize": "LEGAL"
            }]'
            data-columns='[
                { "className": "text--bold" },
                { "className": "text-right table-cell__highlight" },
                { "className": "text-right table-cell__highlight" },
                { "className": "text-right" },
                { "className": "text-right" },
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
                    <th>Org name</th>
                    <th>Invoice total</th>
                    <th>$ fees for usage</th>
                    <th># phones</th>
                    <th># active phones</th>

                    <th>$ billable segments</th>
                    <th># notification texts</th>
                    <th># texts</th>
                    <th># segments</th>

                    <th>$ billable minutes</th>
                    <th># voicemail minutes</th>
                    <th># calls</th>
                    <th># minutes</th>

                    <th>Org name</th>
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
                        <td>
                            $<g:formatNumber number="${org.totalCost}"
                                type="number"
                                minFractionDigits="2"
                                maxFractionDigits="2" />
                        </td>
                        <td>
                            $<g:formatNumber number="${org.activity?.cost}"
                                type="number"
                                minFractionDigits="2"
                                maxFractionDigits="2" />
                        </td>
                        <td>${org.totalNumPhones}</td>
                        <td>${org.activity?.numActivePhones}</td>

                        <td>
                            $<g:formatNumber number="${org.activity?.textCost}"
                                type="number"
                                minFractionDigits="2"
                                maxFractionDigits="2" />
                        </td>
                        <td>${org.activity?.numNotificationTexts}</td>
                        <td>${org.activity?.numTexts}</td>
                        <td>${org.activity?.numSegments}</td>

                        <td>
                            $<g:formatNumber number="${org.activity?.callCost}"
                                type="number"
                                minFractionDigits="2"
                                maxFractionDigits="2" />
                        </td>
                        <td>
                            <g:formatNumber number="${org.activity?.numVoicemailMinutes}"
                                type="number"
                                minFractionDigits="2"
                                maxFractionDigits="2" />
                        </td>
                        <td>${org.activity?.numCalls}</td>
                        <td>
                            <g:formatNumber number="${org.activity?.numMinutes}"
                                type="number"
                                minFractionDigits="2"
                                maxFractionDigits="2" />
                        </td>

                        <td>
                            <g:link action="show" id="${org.id}">
                                ${org.name}
                            </g:link>
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
