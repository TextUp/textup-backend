<g:if test="${staffs}">
    <div class="table-responsive">
        <table class="table"
            data-buttons='[{ "extend": "pdfHtml5", "title": "${title}", "messageTop": "${messageTop}" }]'
            data-columns='[
                null,
                null,
                null,
                null,
                { "className": "text-right" },
                { "className": "text-right" },
                { "className": "text-right" },
                { "className": "text-right" }]'>
            <thead>
                <tr>
                    <th>Name</th>
                    <th>Username</th>
                    <th>Email</th>
                    <th>TextUp phone</th>
                    <th># texts</th>
                    <th># billable segments</th>
                    <th># calls</th>
                    <th># billable minutes</th>
                </tr>
            </thead>
            <tbody>
                <g:each var="staff" in="${staffs}">
                    <tr>
                        <td>${staff.name}</td>
                        <td>${staff.username}</td>
                        <td>${staff.email}</td>
                        <td>${staff.phoneNumber}</td>
                        <td>${staff.activity?.numTexts}</td>
                        <td>${staff.activity?.numBillableSegments}</td>
                        <td>${staff.activity?.numCalls}</td>
                        <td>
                            <g:formatNumber number="${staff.activity?.numBillableMinutes}"
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
    No individual phones found for the specified time period
</g:else>
