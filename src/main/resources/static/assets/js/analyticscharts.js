var options = {
    chart: { 
        height: 80, 
        type: "area", 
        toolbar: { show: !1 }, 
        sparkline: { enabled: !0 } 
    }, 
    markers: { 
        size: 6, 
        colors: "transparent", 
        strokeColors: "transparent", 
        strokeWidth: 4, 
        discrete: [
            { fillColor: '#fff', 
                seriesIndex: 0, 
                dataPointIndex: 6, 
                strokeColor: '#22c55e', 
                strokeWidth: 2, 
                size: 6, 
                radius: 8 
            }], 
            hover: { size: 7 } 
        }, 
        grid: { show: !1, padding: { right: 8 } }, 
        colors: ['#22c55e'], 
        fill: { type: "gradient", gradient: { shade: '#22c55e', shadeIntensity: 0.8, opacityFrom: 0.8, opacityTo: 0.25, stops: [0, 85, 100] } }, 
        dataLabels: { enabled: !1 }, 
        stroke: { width: 2, curve: "smooth" }, 
        series: [{ data: [180, 175, 275, 140, 205, 190, 290] }], 
        xaxis: { show: !1, lines: { show: !1 }, labels: { show: !1 }, stroke: { width: 0 }, axisBorder: { show: !1 } }, 
        yaxis: { stroke: { width: 0 }, show: !1 }
};
var chart = new ApexCharts(document.querySelector("#orderChart"), options);
chart.render();


var options = {
    chart: { type: "bar", height: 240, stacked: !0, toolbar: { show: !1 } },
      series: [
        { name: "PRODUCT A", data: [25, 29, 32, 35, 34, 18, 30, 35] },
        { name: "PRODUCT B", data: [75, 50, 55, 60, 48, 82, 59, 65] },
      ],
      plotOptions: { bar: { horizontal: !1, columnWidth: "40%", borderRadius: 8,  borderRadiusApplication: "around",  borderRadiusWhenStacked: "all" } },
      dataLabels: { enabled: !1 },
      stroke: { curve: "smooth", width: 6, lineCap: "round", colors: ["#fff"] },
      legend: { show: !1 },
      colors: ["#9ca3af", "#4f46e5"],
      fill: { opacity: 1 },
      grid: { show: !1, strokeDashArray: 7, padding: { top: -10, bottom: -12, left: 0, right: 0 } },
      xaxis: { categories: [""], labels: { show: !1, style: { colors: '#000', fontSize: "13px" } }, axisBorder: { show: !1 }, axisTicks: { show: !1 } },
      yaxis: { show: !1 },
      tooltip: {enabled: false},
      responsive: [
        { breakpoint: 1440, options: { plotOptions: { bar: { columnWidth: "40%" } } } },
        { breakpoint: 1300, options: { plotOptions: { bar: { columnWidth: "60%" } } } },
        { breakpoint: 1200, options: { plotOptions: { bar: { columnWidth: "60%" } } } },
        { breakpoint: 1040, options: { plotOptions: { bar: { columnWidth: "60%" } } } },
        { breakpoint: 992, options: { plotOptions: { bar: {  columnWidth: "50%" } } } },
        { breakpoint: 768, options: { plotOptions: { bar: { columnWidth: "40%" } } } },
        { breakpoint: 576, options: { plotOptions: { bar: { columnWidth: "50%" } } } },
        { breakpoint: 440, options: { plotOptions: { bar: { columnWidth: "60%" } } } },
        { breakpoint: 360, options: { plotOptions: { bar: { columnWidth: "70%" } } } },
      ],
      states: { hover: { filter: { type: "none" } }, active: { filter: { type: "none" } } },
};
var chart = new ApexCharts(document.querySelector("#totalearning"), options);
chart.render();


var options = {
    series: [
        { name: "2023", data: [18, 7, 15, 29, 18, 12, 9] },
        { name: "2022", data: [-13, -18, -9, -14, -15, -17, -15] },
      ],
      chart: { height: 300, stacked: !0, type: "bar", toolbar: { show: !1 } },
      plotOptions: { bar: { horizontal: false, columnWidth: "33%", borderRadius: 8, borderRadiusApplication: "around",  borderRadiusWhenStacked: "all" } },
      colors: ['#4f46e5', '#22d3ee'],
      dataLabels: { enabled: false },
      stroke: { curve: "smooth", width: 6, lineCap: "round", colors: ['#fff'] },
      legend: { show: false, horizontalAlign: "left", position: "top", markers: { height: 8, width: 8, radius: 12, offsetX: 0 }, labels: { colors: '#9ca3af' }, itemMargin: { horizontal: 10 } },
      xaxis: { categories: ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul"], labels: { style: { fontSize: "13px", colors: '#9ca3af' } }, axisTicks: { show: !1 }, axisBorder: { show: !1 } },
      yaxis: { labels: { style: { fontSize: "13px", colors: '#9ca3af' } } },
      states: { hover: { filter: { type: "none" } }, active: { filter: { type: "none" } } },
      responsive: [
        { breakpoint: 1440, options: { plotOptions: { bar: { columnWidth: "40%" } } } },
        { breakpoint: 1300, options: { plotOptions: { bar: { columnWidth: "60%" } } } },
        { breakpoint: 1200, options: { plotOptions: { bar: { columnWidth: "60%" } } } },
        { breakpoint: 1040, options: { plotOptions: { bar: { columnWidth: "60%" } } } },
        { breakpoint: 992, options: { plotOptions: { bar: {  columnWidth: "50%" } } } },
        { breakpoint: 768, options: { plotOptions: { bar: { columnWidth: "40%" } } } },
        { breakpoint: 576, options: { plotOptions: { bar: { columnWidth: "50%" } } } },
        { breakpoint: 440, options: { plotOptions: { bar: { columnWidth: "60%" } } } },
        { breakpoint: 360, options: { plotOptions: { bar: { columnWidth: "70%" } } } },
      ],
      tooltip: { enabled: true,  onDatasetHover: { highlightDataSeries: false}},
};
var chart = new ApexCharts(document.querySelector("#revenuechart"), options);
chart.render();


var options = {
    series: [80], 
    labels: ["Growth"], 
    chart: { height: 240, type: "radialBar" }, 
    plotOptions: { 
        radialBar: { 
            size: 150, 
            offsetY: 10, 
            startAngle: -150, 
            endAngle: 150, 
            hollow: { size: "55%" }, 
            track: { background: '#fff', strokeWidth: "100%" }, 
            dataLabels: { name: { offsetY: 15, color: '#475569', fontSize: "15px", fontWeight: "600", fontFamily: "Public Sans" }, 
            value: { offsetY: -25, color: '#475569', fontSize: "22px", fontWeight: "500", fontFamily: "Public Sans" } } } 
        }, 
        colors: ['#4f46e5'], 
        fill: { type: "gradient", gradient: { shade: "dark", shadeIntensity: 0.5, gradientToColors: ['#4f46e5'], inverseColors: !0, opacityFrom: 1, opacityTo: 0.6, stops: [30, 70, 100] } }, 
        stroke: { dashArray: 5 }, 
        grid: { padding: { top: -35, bottom: -10 } }, 
        states: { hover: { filter: { type: "none" } }, active: { filter: { type: "none" } } } 
};
var chart = new ApexCharts(document.querySelector("#growthChart"), options);
chart.render();


var options = {
    chart: { 
        height: 70, 
        type: "bar", 
        toolbar: { show: !1 } 
    }, 
    plotOptions: { 
        bar: { 
            barHeight: "80%", 
            columnWidth: "75%", 
            startingShape: "rounded", 
            endingShape: "rounded", 
            borderRadius: 2, 
            distributed: !0 
        } 
    }, 
    grid: { show: !1, padding: { top: -20, bottom: -12, left: -10, right: 0 } }, 
    colors: ['#e0e7ff', '#e0e7ff', '#e0e7ff', '#e0e7ff', '#4f46e5', '#e0e7ff', '#e0e7ff'], 
    dataLabels: { enabled: !1 }, 
    series: [{ data: [40, 95, 60, 45, 90, 50, 75] }], 
    legend: { show: !1 }, 
    xaxis: { categories: ["M", "T", "W", "T", "F", "S", "S"], 
    axisBorder: { show: !1 }, axisTicks: { show: !1 }, labels: { style: { colors: '#000', fontSize: "13px" } } }, 
    yaxis: { labels: { show: !1 } } 
};
var chart = new ApexCharts(document.querySelector("#revenueChart"), options);
chart.render();


var options = {
    chart: { 
        height: 120, 
        type: "line", 
        toolbar: { show: !1 }, 
        dropShadow: { enabled: !0, top: 10, left: 5, blur: 3, color: '#eab308', opacity: 0.15 }, 
        sparkline: { enabled: !0 } }, 
        grid: { show: !1, padding: { right: 8 } }, 
        colors: ['#eab308'], 
        dataLabels: { enabled: !1 }, 
        stroke: { width: 5, curve: "smooth" }, 
        series: [{ data: [110, 270, 145, 245, 205, 285] }], 
        xaxis: { show: !1, lines: { show: !1 }, labels: { show: !1 }, axisBorder: { show: !1 } }, 
        yaxis: { show: !1 }
};
var chart = new ApexCharts(document.querySelector("#profileReportChart"), options);
chart.render();