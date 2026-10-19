// Trang báo cáo: vẽ biểu đồ Chart.js từ window.reportChart (do ReportCharts đưa vào Model, Thymeleaf in vào trang).
// Dạng {type, labels, datasets: [{label, data}], percent}; màu lấy từ CSS variables của app.css.
(function () {
    var spec = window.reportChart;
    var canvas = document.getElementById('reportChart');
    if (!spec || !canvas || typeof Chart === 'undefined') {
        return;
    }
    var css = getComputedStyle(document.documentElement);
    var palette = [
        css.getPropertyValue('--color-orange').trim() || '#F97316',
        css.getPropertyValue('--color-navy').trim() || '#0A1930',
        '#0EA5E9',
        '#DC2626',
        '#16A34A',
        '#6B7280'
    ];
    var doughnut = spec.type === 'doughnut';
    var datasets = spec.datasets.map(function (dataset, index) {
        return {
            label: dataset.label,
            data: dataset.data,
            // Biểu đồ tròn: mỗi phần một màu; biểu đồ cột: mỗi dãy một màu.
            backgroundColor: doughnut ? palette.slice(0, dataset.data.length) : palette[index % palette.length],
            borderWidth: doughnut ? 1 : 0
        };
    });
    var options = {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {legend: {display: doughnut || datasets.length > 1}}
    };
    if (!doughnut) {
        options.scales = {
            y: {beginAtZero: true, ticks: {precision: 0}}
        };
        if (spec.percent) {
            options.scales.y.max = 100;
            options.scales.y.ticks.callback = function (value) {
                return value + '%';
            };
        }
    }
    new Chart(canvas, {type: spec.type, data: {labels: spec.labels, datasets: datasets}, options: options});
})();
