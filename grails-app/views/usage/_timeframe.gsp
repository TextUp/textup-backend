<g:form class="form-inline" action="updateTimeframe" params="[id: id]">
    <span class="margin-r">Select chart points to change timeframe or</span>
    <div class="form-group">
        <label class="sr-only" for="timeframe">Timeframe</label>
        <g:select class="form-control"
            name="timeframe"
            value="${monthString}"
            from="${availableMonthStrings}" />
    </div>
    <g:submitButton class="btn btn-primary" name="update" value="Change timeframe" />
</g:form>
