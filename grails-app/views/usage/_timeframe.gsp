<g:form class="form-inline date-picker-form" action="updateTimeframe" params="[id: id]">
    <div class="form-group">
        <label class="sr-only" for="timeframe">Timeframe</label>
        <g:datePicker name="timeframe"
            value="${timeframe}"
            precision="month"
            years="${allowedYears}"/>
    </div>
    <g:submitButton class="btn btn-primary" name="update" value="Change timeframe" />
</g:form>
