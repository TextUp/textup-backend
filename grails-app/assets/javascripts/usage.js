$(document).ready(function() {
  var HEX_COLOR_1 = "#1f77b4",
    HEX_COLOR_2 = "#ff7600",
    charts = [],
    $chartSelector = $(".chart-type-selector"),
    orgId = $chartSelector.data("orgId");
  // tables
  if ($("table").DataTable) {
    $("table").DataTable({
      pageLength: 10,
      dom:
        "<'row'<'col-sm-6'B><'col-sm-6 flex flex--align-center flex-justify-end'<'margin-r'f>l>>" +
        "<'row'<'col-sm-12'tr>>" +
        "<'row'<'col-sm-5'i><'col-sm-7'p>>",
      lengthMenu: [[10, 25, 50, -1], [10, 25, 50, "All"]]
    });
  }
  // charts
  $(".number-detail").on("click", toggleChildChart);
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
        // force redraw after opening to ensure that chart is appropriately resized
        var chartObj = retrieveChartObjOnTableCell($target);
        if (chartObj) {
          chartObj.flush();
        }
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

  // Helpers
  // -------

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
    var propsToDisplay = getCurrentPropsToDisplay();
    chartObj.load({
      json: retrieveDataOnChart(chartObj),
      keys: { value: propsToDisplay },
      unload: true
    });
  }

  function generateChart(bindTo, currentMonthIndex, chartData) {
    var propsToDisplay = getCurrentPropsToDisplay(),
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
        keys: { value: propsToDisplay },
        colors: {
          numActivePhones: HEX_COLOR_1,
          numTexts: HEX_COLOR_1,
          numBillableSegments: HEX_COLOR_2,
          numCalls: HEX_COLOR_1,
          numBillableMinutes: HEX_COLOR_2
        },
        names: {
          numActivePhones: "# active phones",
          numTexts: "# texts",
          numBillableSegments: "# billable segments",
          numCalls: "# calls",
          numBillableMinutes: "# billable minutes"
        }
      },
      grid: gridOptions,
      regions: regionOptions,
      axis: axisOptions
    });
    storeDataOnChart(chartObj, chartData);
    return chartObj;
  }

  function getCurrentPropsToDisplay() {
    return $(".chart-type-selector .active").data("props") || [];
  }
});
