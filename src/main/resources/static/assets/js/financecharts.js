var options = {
    chart: { 
        height: 110, 
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
    colors: ['#bbf7d0', '#bbf7d0', '#bbf7d0', '#bbf7d0', '#22c55e', '#bbf7d0', '#bbf7d0'], 
    dataLabels: { enabled: !1 }, 
    series: [{ data: [10, 30, 50, 70, 90, 50, 40] }], 
    legend: { show: !1 }, 
    xaxis: { categories: ["M", "T", "W", "T", "F", "S", "S"], 
    axisBorder: { show: !1 }, axisTicks: { show: !1 }, labels: { style: { colors: '#000', fontSize: "13px" } } }, 
    yaxis: { labels: { show: !1 } } 
};
var chart = new ApexCharts(document.querySelector("#revenuegrowth"), options);
chart.render();


var options = {
    chart: { type: "bar", height: 390, stacked: !0, toolbar: { show: !1 } },
      series: [
        { name: "Apple", data: [75, 50, 55, 60, 48, 82, 59] },
        { name: "Microsoft", data: [25, 29, 32, 35, 34, 18, 30] },
      ],
      plotOptions: { bar: { horizontal: !1, columnWidth: "40%", borderRadius: 10, borderRadiusApplication: "around",  borderRadiusWhenStacked: "all" } },
      dataLabels: { enabled: !1 },
      stroke: { curve: "smooth", width: 6, lineCap: "round", colors: '#fff' },
      legend: { show: !1 },
      colors: ["#dc2626", "#475569"],
      fill: { opacity: 1 },
      grid: { show: !1, strokeDashArray: 7, padding: { top: -10, bottom: -12, left: 0, right: 0 } },
      xaxis: { categories: ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"], labels: { show: !0, style: { colors: '#9ca3af', fontSize: "13px" } }, axisBorder: { show: !1 }, axisTicks: { show: !1 } },
      yaxis: { show: !1 },
      responsive: [
        { breakpoint: 1440, options: { plotOptions: { bar: { borderRadius: 10, columnWidth: "50%" } } } },
        { breakpoint: 1300, options: { plotOptions: { bar: { borderRadius: 11, columnWidth: "55%" } } } },
        { breakpoint: 1200, options: { plotOptions: { bar: { borderRadius: 10, columnWidth: "45%" } } } },
        { breakpoint: 1040, options: { plotOptions: { bar: { borderRadius: 10, columnWidth: "50%" } } } },
        { breakpoint: 992, options: { plotOptions: { bar: { borderRadius: 12, columnWidth: "40%" } }, chart: { type: "bar", height: 320 } } },
        { breakpoint: 768, options: { plotOptions: { bar: { borderRadius: 11, columnWidth: "25%" } } } },
        { breakpoint: 576, options: { plotOptions: { bar: { borderRadius: 10, columnWidth: "35%" } } } },
        { breakpoint: 440, options: { plotOptions: { bar: { borderRadius: 10, columnWidth: "45%" } } } },
        { breakpoint: 360, options: { plotOptions: { bar: { borderRadius: 8, columnWidth: "50%" } } } },
      ],
      states: { hover: { filter: { type: "none" } }, active: { filter: { type: "none" } } },
};
var chart = new ApexCharts(document.querySelector("#salesactivitychart"), options);
chart.render();

var options = {
    chart: { height: 200, type: "bar", toolbar: { show: !1 } }, plotOptions: { bar: { barHeight: "60%", columnWidth: "60%", borderRadiusApplication: "around",  borderRadiusWhenStacked: "all", borderRadius: 4, distributed: !0 } }, grid: { show: !1, padding: { top: -20, bottom: 0, left: -10, right: -10 } }, colors: ["#ddd6fe", "#ddd6fe", "#ddd6fe", "#4f46e5", "#ddd6fe", "#ddd6fe", "#ddd6fe"], dataLabels: { enabled: !1 }, series: [{ data: [40, 95, 60, 45, 90, 50, 75] }], legend: { show: !1 }, xaxis: { categories: ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"], axisBorder: { show: !1 }, axisTicks: { show: !1 }, labels: { style: { fontSize: "13px" } } }, yaxis: { labels: { show: !1 } }
};
var chart = new ApexCharts(document.querySelector("#reportBarChart"), options);
chart.render();