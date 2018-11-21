<ul class="usage-context-list">
    <li class="usage-context-list__item">
        <strong>${numPhones}</strong> phones<g:if test="${numActivePhones != null}">,
            <strong>${numActivePhones}</strong> active
        </g:if>
    </li>
    <li class="usage-context-list__item">
        <strong>${numSegments}</strong> segments
    </li>
    <li class="usage-context-list__item">
        <strong>
            <g:formatNumber number="${numMinutes}"
                type="number"
                minFractionDigits="2"
                maxFractionDigits="2" />
        </strong>
        minutes
    </li>
</ul>
