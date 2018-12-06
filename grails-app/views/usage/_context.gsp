<ul class="usage-context-list">
    <li class="usage-context-list__item">
        <strong>
            $<g:formatNumber number="${usageAndCosts.totalCost}"
                type="number"
                minFractionDigits="2"
                maxFractionDigits="2" />
        </strong> invoice total
    </li>
    <li class="usage-context-list__item">
        <strong>
            $<g:formatNumber number="${usageAndCosts.usageCost}"
                type="number"
                minFractionDigits="2"
                maxFractionDigits="2" />
        </strong> fees for usage
    </li>
    <li class="usage-context-list__item">
        <strong>
            $<g:formatNumber number="${usageAndCosts.textCost}"
                type="number"
                minFractionDigits="2"
                maxFractionDigits="2" />
        </strong> billable segments
    </li>
    <li class="usage-context-list__item">
        <strong>
            $<g:formatNumber number="${usageAndCosts.callCost}"
                type="number"
                minFractionDigits="2"
                maxFractionDigits="2" />
        </strong> billable minutes
    </li>
</ul>
<ul class="usage-context-list">
    <li class="usage-context-list__item">
        <strong>${phoneCounts.numPhones}</strong> phones<g:if test="${phoneCounts.numActivePhones != null}">,
            <strong>${phoneCounts.numActivePhones}</strong> active
        </g:if>
    </li>
    <li class="usage-context-list__item">
        <strong>${usageAndCosts.numTexts}</strong> texts,
        <strong>${usageAndCosts.numSegments}</strong> segments
    </li>
    <li class="usage-context-list__item">
        <strong>${usageAndCosts.numCalls}</strong> calls,
        <strong>
            <g:formatNumber number="${usageAndCosts.numMinutes}"
                type="number"
                minFractionDigits="2"
                maxFractionDigits="2" />
        </strong>
        minutes,
        <strong>
            <g:formatNumber number="${usageAndCosts.numBillableMinutes}"
                type="number"
                minFractionDigits="0"
                maxFractionDigits="0" />
        </strong>
        billable minutes
    </li>
</ul>
