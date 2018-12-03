<ul class="usage-context-list">
    <li class="usage-context-list__item">
        <strong>
            $<g:formatNumber number="${totalCost}"
                type="number"
                minFractionDigits="2"
                maxFractionDigits="2" />
        </strong> invoice total
    </li>
    <li class="usage-context-list__item">
        <strong>
            $<g:formatNumber number="${usageCost}"
                type="number"
                minFractionDigits="2"
                maxFractionDigits="2" />
        </strong> fees for usage
    </li>
    <li class="usage-context-list__item">
        <strong>
            $<g:formatNumber number="${textCost}"
                type="number"
                minFractionDigits="2"
                maxFractionDigits="2" />
        </strong> billable segments
    </li>
    <li class="usage-context-list__item">
        <strong>
            $<g:formatNumber number="${callCost}"
                type="number"
                minFractionDigits="2"
                maxFractionDigits="2" />
        </strong> billable minutes
    </li>
</ul>
<ul class="usage-context-list">
    <li class="usage-context-list__item">
        <strong>${numPhones}</strong> phones<g:if test="${numActivePhones != null}">,
            <strong>${numActivePhones}</strong> active
        </g:if>
    </li>
    <li class="usage-context-list__item">
        <strong>${numTexts}</strong> texts,
        <strong>${numSegments}</strong> segments
    </li>
    <li class="usage-context-list__item">
        <strong>${numCalls}</strong> calls,
        <strong>
            <g:formatNumber number="${numMinutes}"
                type="number"
                minFractionDigits="2"
                maxFractionDigits="2" />
        </strong>
        minutes,
        <strong>
            <g:formatNumber number="${numBillableMinutes}"
                type="number"
                minFractionDigits="0"
                maxFractionDigits="0" />
        </strong>
        billable minutes
    </li>
</ul>
