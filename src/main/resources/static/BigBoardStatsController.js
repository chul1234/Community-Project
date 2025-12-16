// 수정됨: 대용량 가로 막대 차트(사용자별/조회수) 우측 잘림 해결 - layout 우측 패딩 확대 + x축 ticks 회전 방지 + scales 병합(grace 포함)

app.controller('BigBoardStatsController', function ($scope, $http) {

    function getCommonOptions() {
        return {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    position: 'bottom',
                    labels: {
                        font: { size: 12 },
                        padding: 20
                    }
                }
            },
            layout: {
                padding: 10
            }
        };
    }

    function applyHorizontalBarFix(opt) {
        opt.layout = opt.layout || {};
        opt.layout.padding = opt.layout.padding || {};

        // ✅ 오른쪽/아래 여유를 충분히 줘서 축 숫자/막대 끝이 잘리지 않게 함
        opt.layout.padding.right = 80;
        opt.layout.padding.bottom = 18;

        opt.scales = opt.scales || {};
        opt.scales.x = opt.scales.x || {};
        opt.scales.x.grace = '10%';

        // ✅ Chart.js가 공간 부족하면 ticks를 회전시키는데, 회전되면 캔버스 끝에서 잘리기 쉬움
        opt.scales.x.ticks = opt.scales.x.ticks || {};
        opt.scales.x.ticks.maxRotation = 0;
        opt.scales.x.ticks.minRotation = 0;
        opt.scales.x.ticks.padding = 8;
        opt.scales.x.ticks.autoSkip = true;
        opt.scales.x.ticks.autoSkipPadding = 12;

        return opt;
    }

    $http.get('/api/stats/big-posts').then(function (res) {
        const data = res.data;

        // 1) 사용자별 게시글 수 (가로 막대)
        const userOpt = applyHorizontalBarFix(getCommonOptions());
        userOpt.indexAxis = 'y';

        new Chart(document.getElementById('bigUserChart'), {
            type: 'bar',
            data: {
                labels: data.topUsers.map(v => v.user_id),
                datasets: [{
                    label: '게시글 수',
                    data: data.topUsers.map(v => v.cnt),
                    backgroundColor: 'rgba(54, 162, 235, 0.7)',
                    borderColor: 'rgba(54, 162, 235, 1)',
                    borderWidth: 1,
                    barPercentage: 0.6
                }]
            },
            options: userOpt
        });

        // 2) 도넛
        const doughnutOpt = getCommonOptions();
        doughnutOpt.plugins.legend = {
            position: 'bottom',
            labels: {
                usePointStyle: true,
                boxWidth: 8,
                padding: 10,
                font: { size: 11 }
            }
        };
        doughnutOpt.cutout = '60%';

        new Chart(document.getElementById('bigUserDoughnut'), {
            type: 'doughnut',
            data: {
                labels: data.topUsers.map(v => v.user_id),
                datasets: [{
                    data: data.topUsers.map(v => v.cnt),
                    backgroundColor: [
                        '#FF6384', '#36A2EB', '#FFCE56', '#4BC0C0', '#9966FF',
                        '#FF9F40', '#E7E9ED', '#76A346', '#0E3D59', '#8854D0'
                    ],
                    hoverOffset: 4
                }]
            },
            options: doughnutOpt
        });

        // 3) 조회수 Top10 (가로 막대)
        const viewOpt = applyHorizontalBarFix(getCommonOptions());
        viewOpt.indexAxis = 'y';

        new Chart(document.getElementById('bigViewChart'), {
            type: 'bar',
            data: {
                labels: data.topViews.map(v => v.title),
                datasets: [{
                    label: '조회수',
                    data: data.topViews.map(v => v.view_count),
                    backgroundColor: 'rgba(75, 192, 192, 0.7)',
                    borderColor: 'rgba(75, 192, 192, 1)',
                    borderWidth: 1,
                    barPercentage: 0.6
                }]
            },
            options: viewOpt
        });

        // 4) Polar Area
        const polarOpt = getCommonOptions();
        polarOpt.plugins.legend = { display: false };
        polarOpt.scales = {
            r: {
                ticks: { backdropColor: 'transparent' },
                grid: { color: '#e5e5e5' }
            }
        };

        new Chart(document.getElementById('bigViewPolar'), {
            type: 'polarArea',
            data: {
                labels: data.topViews.map(v => v.title),
                datasets: [{
                    data: data.topViews.map(v => v.view_count),
                    backgroundColor: [
                        'rgba(255, 99, 132, 0.6)',
                        'rgba(54, 162, 235, 0.6)',
                        'rgba(255, 206, 86, 0.6)',
                        'rgba(75, 192, 192, 0.6)',
                        'rgba(153, 102, 255, 0.6)',
                        'rgba(255, 159, 64, 0.6)',
                        'rgba(201, 203, 207, 0.6)',
                        'rgba(118, 163, 70, 0.6)',
                        'rgba(14, 61, 89, 0.6)',
                        'rgba(136, 84, 208, 0.6)'
                    ]
                }]
            },
            options: polarOpt
        });

        // 5) 일별 등록 추이 (마지막 날짜 안 잘리게)
        const lineOpt = getCommonOptions();
        lineOpt.layout = { padding: { right: 60, left: 10, top: 10, bottom: 10 } };
        lineOpt.scales = {
            x: {
                offset: true,
                ticks: { autoSkip: true, maxRotation: 0, autoSkipPadding: 20 },
                grid: { display: false }
            },
            y: { beginAtZero: true }
        };
        lineOpt.elements = { line: { tension: 0.3 } };
        lineOpt.clip = false;

        new Chart(document.getElementById('bigDailyChart'), {
            type: 'line',
            data: {
                labels: data.daily.map(v => v.day),
                datasets: [{
                    label: '일별 게시글 수',
                    data: data.daily.map(v => v.cnt),
                    fill: true,
                    backgroundColor: 'rgba(54, 162, 235, 0.08)',
                    borderColor: '#4c6ef5',
                    borderWidth: 2,
                    pointRadius: 3,
                    pointHoverRadius: 6,
                    pointBackgroundColor: '#ffffff',
                    pointBorderColor: '#4c6ef5'
                }]
            },
            options: lineOpt
        });
    });

});

// 수정됨 끝
