<g:if test="${staffs}">
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
                null,
                null,
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
                    <th>Username</th>
                    <th>Email</th>
                    <th>TextUp phone number(s)</th>

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
                <g:each var="staff" in="${staffs}">
                    <tr>
                        <td data-phone-id="${staff.phoneId}"></td>
                        <td>${staff.name}</td>
                        <td>
                            $<g:formatNumber number="${staff.totalCost}"
                                type="number"
                                minFractionDigits="2"
                                maxFractionDigits="2" />
                        </td>
                        <td>
                            $<g:formatNumber number="${staff.activity?.cost}"
                                type="number"
                                minFractionDigits="2"
                                maxFractionDigits="2" />
                        </td>
                        <td>${staff.username}</td>
                        <td>${staff.email}</td>
                        <td>${staff.numbers}</td>

                        <td>
                            $<g:formatNumber number="${staff.activity?.textCost}"
                                type="number"
                                minFractionDigits="2"
                                maxFractionDigits="2" />
                        </td>
                        <td>${staff.activity?.numNotificationTexts}</td>
                        <td>${staff.activity?.numTexts}</td>
                        <td>${staff.activity?.numSegments}</td>

                        <td>
                            $<g:formatNumber number="${staff.activity?.callCost}"
                                type="number"
                                minFractionDigits="2"
                                maxFractionDigits="2" />
                        </td>
                        <td>
                            <g:formatNumber number="${staff.activity?.numVoicemailMinutes}"
                                type="number"
                                minFractionDigits="2"
                                maxFractionDigits="2" />
                        </td>
                        <td>${staff.activity?.numCalls}</td>
                        <td>
                            <g:formatNumber number="${staff.activity?.numMinutes}"
                                type="number"
                                minFractionDigits="2"
                                maxFractionDigits="2" />
                        </td>

                        <td>${staff.name}</td>
                    </tr>
                </g:each>
            </tbody>
        </table>
    </div>
</g:if>
<g:else>
    No individual phones found for the specified time period
</g:else>
