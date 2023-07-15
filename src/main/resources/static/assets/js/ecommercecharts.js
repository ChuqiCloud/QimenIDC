var options = {
    chart: { height: 120, width: 200, parentHeightOffset: 0, type: "bar", toolbar: { show: !1 } },
      plotOptions: { bar: { borderRadiusApplication: "around",  borderRadiusWhenStacked: "all", borderRadius: 8, distributed: !0 } },
      grid: { show: !1, padding: { top: -25, bottom: -12 } },
      colors: ['#c7d2fe', '#c7d2fe', '#c7d2fe', '#c7d2fe', '#c7d2fe', '#4f46e5', '#c7d2fe'],
      dataLabels: { enabled: !1 },
      series: [{ data: [40, 95, 60, 45, 90, 50, 75] }],
      legend: { show: !1 },
      xaxis: { categories: ["M", "T", "W", "T", "F", "S", "S"], axisBorder: { show: !1 }, axisTicks: { show: !1 }, labels: { style: { colors: '#9ca3af', fontSize: "13px" } } },
      yaxis: { labels: { show: !1 } },
};
var chart = new ApexCharts(document.querySelector("#visitorschart"), options);
chart.render();

var options = {
    chart: { 
        height: 120, 
        parentHeightOffset: 0, 
        toolbar: { show: !1 }, 
        type: "area" 
    }, 
    dataLabels: { enabled: !1 }, 
    stroke: { width: 2, curve: "smooth" }, 
    series: [{ data: [15, 20, 14, 22, 17, 40, 12, 35, 25] }], 
    colors: ['#16a34a'], 
    fill: { type: "gradient", gradient: { shade: '#16a34a', shadeIntensity: 0.8, opacityFrom: 0.8, opacityTo: 0.25, stops: [0, 85, 100] } }, 
    grid: { show: !1, padding: { top: -20, bottom: -8 } }, 
    legend: { show: !1 }, 
    xaxis: { categories: ["H1", "H2", "H3", "H4", "H5", "H6", "H7", "H8", "H9"], axisBorder: { show: !1 }, axisTicks: { show: !1 }, labels: { style: { fontSize: "13px", colors: '#9ca3af' } } }, 
    yaxis: { labels: { show: !1 } }
};
var chart = new ApexCharts(document.querySelector("#activitychart"), options);
chart.render();

var options = {
    series: [
        { data: [58, 28, 50, 80] }, 
        { data: [50, 22, 65, 72] }
    ], 
    chart: { 
        type: "bar", 
        height: 80, 
        toolbar: { tools: { download: !1 } } 
    }, 
    plotOptions: { 
        bar: { columnWidth: "65%", borderRadiusApplication: "around",  borderRadiusWhenStacked: "all", borderRadius: 3, dataLabels: { show: !1 } }
     }, 
     grid: { show: !1, padding: { top: -30, bottom: -12, left: -10, right: 0 } }, 
     colors: ['#22c55e', '#bbf7d0'], 
     dataLabels: { enabled: !1 }, 
     stroke: { show: !0, width: 5, colors: '#9ca3af' }, 
     legend: { show: !1 }, 
     xaxis: { categories: ["Jan", "Apr", "Jul", "Oct"], axisBorder: { show: !1 }, axisTicks: { show: !1 }, labels: { style: { colors: '#9ca3af', fontSize: "13px" } } }, 
     yaxis: { labels: { show: !1 } }
};
var chart = new ApexCharts(document.querySelector("#profitchart"), options);
chart.render();

var options = {
    chart: { 
        height: 130, 
        sparkline: { enabled: !0 }, 
        parentHeightOffset: 0, 
        type: "radialBar" 
    }, 
    colors: ['#4f46e5'], 
    series: [78], 
    plotOptions: { 
        radialBar: { 
            startAngle: -90, 
            endAngle: 90, 
            hollow: { size: "55%" }, 
            track: { background: '#f3f4f6' }, 
            dataLabels: { name: { show: !1 }, value: { fontSize: "22px", color: '#9ca3af', fontWeight: 500, offsetY: 0 } } 
        } 
    }, 
    grid: { show: !1, padding: { left: -10, right: -10, top: -10 } }, 
    stroke: { lineCap: "round" }, labels: ["Progress"] 
};
var chart = new ApexCharts(document.querySelector("#expenseschart"), options);
chart.render();

var options = {
    chart: { height: 250, type: "area", toolbar: !1, dropShadow: { enabled: !0, top: 14, left: 2, blur: 3, color: '#4f46e5', opacity: 0.15 } },
      series: [{ data: [3350, 3350, 4800, 4800, 2950, 2950, 1800, 1800, 3750, 3750, 5700, 5700] }],
      dataLabels: { enabled: !1 },
      stroke: { width: 3, curve: "straight" },
      colors: ['#4f46e5'],
      fill: { type: "gradient", gradient: { shade: '#4f46e5', shadeIntensity: 0.8, opacityFrom: 0.7, opacityTo: 0.25, stops: [0, 95, 100] } },
      grid: { show: !0, borderColor: '#f9fafb', padding: { top: -15, bottom: -10, left: 0, right: 0 } },
      xaxis: { categories: ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"], labels: { offsetX: 0, style: { colors: '#9ca3af', fontSize: "13px" } }, axisBorder: { show: !1 }, axisTicks: { show: !1 }, lines: { show: !1 } },
      yaxis: {
        labels: {
          offsetX: -15,
          formatter: function (o) {
            return "$" + parseInt(o / 1e3) + "k";
          },
          style: { fontSize: "13px", colors: '#9ca3af' },
        },
        min: 1e3,
        max: 6e3,
        tickAmount: 5,
      },
};
var chart = new ApexCharts(document.querySelector("#totalIncomeChart"), options);
chart.render();

var options = {
    series: [{ data: [137, 210, 160, 275, 205, 315] }], chart: { height: 250, parentHeightOffset: 0, parentWidthOffset: 0, type: "line", dropShadow: { enabled: !0, top: 10, left: 5, blur: 3, color: '#f59e0b', opacity: 0.15 }, toolbar: { show: !1 } }, dataLabels: { enabled: !1 }, stroke: { width: 3, curve: "smooth" }, legend: { show: !1 }, colors: ['#f59e0b'], markers: { size: 6, colors: "transparent", strokeColors: "transparent", strokeWidth: 4, discrete: [{ fillColor: '#fff', seriesIndex: 0, dataPointIndex: 5, strokeColor: '#f59e0b', strokeWidth: 8, size: 6, radius: 8 }], hover: { size: 7 } }, grid: { show: !1, padding: { top: -10, left: 0, right: 0, bottom: 10 } }, xaxis: { categories: ["Jan", "Feb", "Mar", "Apr", "May", "Jun"], axisBorder: { show: !1 }, axisTicks: { show: !1 }, labels: { show: !0, style: { fontSize: "13px", colors: '#94a3b8' } } }, yaxis: { labels: { show: !1 } }
};
var chart = new ApexCharts(document.querySelector("#totalBalanceChart"), options);
chart.render();