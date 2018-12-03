<span class="margin-r">Visualize</span>
<div id="chartType"
    class="btn-group chart-type-selector"
    role="group"
    aria-label="Select which variable to visualize graphically"
    data-org-id="${id}">
    <button type="button"
        class="btn btn-default active"
        data-props='{
            "cost": "line",
            "textCost": "area",
            "callCost": "area"
        }'
        data-prop-groups='[["textCost", "callCost"]]'>
        Cost
    </button>
    <button type="button"
        class="btn btn-default"
        data-props='{ "numActivePhones": "line" }'>
        Phones
    </button>
    <button type="button"
        class="btn btn-default"
        data-props='{
            "numNotificationTexts": "area",
            "numOutgoingTexts": "area",
            "numIncomingTexts": "area"
        }'
        data-prop-groups='[["numNotificationTexts", "numOutgoingTexts", "numIncomingTexts"]]'>
        Texts
    </button>
    <button type="button"
        class="btn btn-default"
        data-props='{
            "numNotificationTexts": "area",
            "numOutgoingSegments": "area",
            "numIncomingSegments": "area"
        }'
        data-prop-groups='[["numNotificationTexts", "numOutgoingSegments", "numIncomingSegments"]]'>
        Segments
    </button>
    <button type="button"
        class="btn btn-default"
        data-props='{
            "numVoicemailMinutes": "area",
            "numOutgoingMinutes": "area",
            "numIncomingMinutes": "area"
        }'
        data-prop-groups='[["numVoicemailMinutes", "numOutgoingMinutes", "numIncomingMinutes"]]'>
        Call minutes
    </button>
    <button type="button"
        class="btn btn-default"
        data-props='{
            "numBillableVoicemailMinutes": "area",
            "numOutgoingBillableMinutes": "area",
            "numIncomingBillableMinutes": "area"
        }'
        data-prop-groups='[["numBillableVoicemailMinutes", "numOutgoingBillableMinutes", "numIncomingBillableMinutes"]]'>
        Billable call minutes
    </button>
</div>
