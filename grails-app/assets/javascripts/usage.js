$(document).ready(function() {
  var HEX_COLOR_1 = "#1f77b4",
    HEX_COLOR_2 = "#ff7600",
    HEX_COLOR_3 = "#2ecc71",
    HEX_COLOR_4 = "#9b59b6",
    HEX_COLOR_5 = "#1abc9c",
    charts = [],
    $chartSelector = $(".chart-type-selector"),
    orgId = $chartSelector.data("orgId"),
    cellToggleSelector = "td.number-detail";
  // tables
  if ($("table").DataTable) {
    $("table")
      .DataTable({
        pageLength: 10,
        scrollX: true,
        dom:
          "<'row'<'col-sm-6'B><'col-sm-6 flex flex--align-center flex-justify-end'<'margin-r'f>l>>" +
          "<'row'<'col-sm-12'tr>>" +
          "<'row'<'col-sm-5'i><'col-sm-7'p>>",
        lengthMenu: [[10, 25, 50, -1], [10, 25, 50, "All"]]
      })
      .on("draw", onTableDraw);
  }
  // charts
  $("table").on("click", cellToggleSelector, toggleChildChart);
  if ($chartSelector.length) {
    $chartSelector.on("click", changeChartDisplay);
    fetchDataAndBuildChart(buildTopLevelCharts, { orgId: orgId });
  }

  // Handlers
  // --------

  function fetchDataAndBuildChart(buildChartFn, opts) {
    $.get("/usage/ajaxGetActivity", opts)
      .fail(fetchChartDataError)
      .done(buildChartFn);
  }
  function fetchChartDataError() {
    console.error("Could not load usage data over time", arguments);
  }

  function buildTopLevelCharts(chartData) {
    if (chartData && chartData.currentMonthIndex && chartData.staffData && chartData.teamData) {
      charts.push(generateChart("#staff-chart", chartData.currentMonthIndex, chartData.staffData));
      charts.push(generateChart("#team-chart", chartData.currentMonthIndex, chartData.teamData));
    }
  }
  function buildNumberChart($cell, bindTo, chartData) {
    if (chartData && chartData.currentMonthIndex && chartData.numberData) {
      var chartObj = generateChart(bindTo, chartData.currentMonthIndex, chartData.numberData);
      charts.push(chartObj);
      storeChartObjOnTableCell($cell, chartObj);
    }
  }

  function toggleChildChart(event) {
    var $target = $(event.target),
      $row = $target.closest("tr"),
      $table = $target.closest("table"),
      tableObj = $table.DataTable({ retrieve: true }),
      rowObj = tableObj.row($row),
      number = $target.data("number");
    if (rowObj.child.isShown()) {
      rowObj.child.hide();
      $row.removeClass("number-detail-expanded");
    } else {
      if (rowObj.child() && rowObj.child().length) {
        rowObj.child.show();
        redrawNumbersChart($target);
      } else {
        var divEl = document.createElement("div");
        rowObj.child(divEl).show();
        fetchDataAndBuildChart(buildNumberChart.bind(null, $target, divEl), { number: number });
      }
      $row.addClass("number-detail-expanded");
    }
  }

  function changeChartDisplay(event) {
    var $target = $(event.target);
    $target
      .parent()
      .children()
      .removeClass("active");
    $target.addClass("active");
    if (charts) {
      charts.forEach(refreshChartData);
    }
  }

  function changeMonthFromChartClick(data) {
    var index = data.index,
      $timeframe = $("#timeframe");
    if (index > -1 && $timeframe.length) {
      var newMonth = $timeframe
        .children()
        .eq(index)
        .val();
      $timeframe.val(newMonth);
      $timeframe.closest("form").submit();
    }
  }

  function onTableDraw(event) {
    setTimeout(function() {
      var $table = $(event.target);
      $table.find(cellToggleSelector).each(function() {
        redrawNumbersChart($(this));
      });
    }, 100);
  }

  // Helpers
  // -------

  function redrawNumbersChart($cell) {
    var chartObj = retrieveChartObjOnTableCell($cell);
    if (chartObj) {
      chartObj.flush();
    }
  }

  function storeChartObjOnTableCell($cell, chartObj) {
    $cell.data("c3-chart", chartObj);
  }
  function retrieveChartObjOnTableCell($cell) {
    return $cell.data("c3-chart");
  }

  function storeDataOnChart(chartObj, data) {
    chartObj._data = data;
  }
  function retrieveDataOnChart(chartObj) {
    return chartObj._data;
  }

  function refreshChartData(chartObj) {
    var propsAndTypes = getCurrentPropsAndTypesToDisplay(),
      propGroupsToDisplay = getCurrentPropGroupsToDisplay();
    chartObj.load({
      json: retrieveDataOnChart(chartObj),
      keys: { value: Object.keys(propsAndTypes) },
      types: propsAndTypes,
      unload: true
    });
    chartObj.groups(propGroupsToDisplay);
  }

  function generateChart(bindTo, currentMonthIndex, chartData) {
    var propsAndTypes = getCurrentPropsAndTypesToDisplay(),
      propGroupsToDisplay = getCurrentPropGroupsToDisplay(),
      months = chartData.map(function(month) {
        return month.monthString;
      }),
      gridOptions = { y: { show: true } },
      regionOptions = [
        {
          axis: "x",
          start: currentMonthIndex,
          end: currentMonthIndex,
          class: "chart-current-month"
        }
      ],
      axisOptions = {
        x: {
          type: "category",
          categories: months,
          tick: {
            culling: {
              max: 20
            },
            multiline: false,
            rotate: -45
          }
        }
      };
    var chartObj = c3.generate({
      bindto: bindTo,
      data: {
        onclick: changeMonthFromChartClick,
        json: chartData,
        keys: { value: Object.keys(propsAndTypes) },
        types: propsAndTypes,
        groups: propGroupsToDisplay,
        colors: {
          cost: HEX_COLOR_1,
          textCost: HEX_COLOR_4,
          callCost: HEX_COLOR_5,
          numActivePhones: HEX_COLOR_1,
          numNotificationTexts: HEX_COLOR_3,
          numOutgoingTexts: HEX_COLOR_1,
          numIncomingTexts: HEX_COLOR_2,
          numOutgoingSegments: HEX_COLOR_1,
          numIncomingSegments: HEX_COLOR_2,
          numVoicemailMinutes: HEX_COLOR_3,
          numOutgoingMinutes: HEX_COLOR_1,
          numIncomingMinutes: HEX_COLOR_2,
          numBillableVoicemailMinutes: HEX_COLOR_3,
          numOutgoingBillableMinutes: HEX_COLOR_1,
          numIncomingBillableMinutes: HEX_COLOR_2
        },
        names: {
          cost: "$ fees for usage overall",
          textCost: "$ fees for texts",
          callCost: "$ fees for calls",
          numActivePhones: "# active phones",
          numNotificationTexts: "# notification texts",
          numOutgoingTexts: "# outgoing texts",
          numIncomingTexts: "# incoming texts",
          numOutgoingSegments: "# outgoing text segments",
          numIncomingSegments: "# incoming text segments",
          numVoicemailMinutes: "# voicemail minutes",
          numOutgoingMinutes: "# outgoing call minutes",
          numIncomingMinutes: "# incoming call minutes",
          numBillableVoicemailMinutes: "# voicemail billable minutes",
          numOutgoingBillableMinutes: "# outgoing call billable minutes",
          numIncomingBillableMinutes: "# incoming call billable minutes"
        }
      },
      grid: gridOptions,
      regions: regionOptions,
      axis: axisOptions
    });
    storeDataOnChart(chartObj, chartData);
    return chartObj;
  }

  function getCurrentPropsAndTypesToDisplay() {
    var $active = $(".chart-type-selector .active");
    $active.removeData("props"); // force jQuery to re-read HTML data attribute
    return $active.data("props") || {};
  }

  function getCurrentPropGroupsToDisplay() {
    var $active = $(".chart-type-selector .active");
    $active.removeData("propGroups"); // force jQuery to re-read HTML data attribute
    return $active.data("propGroups") || [];
  }
});
