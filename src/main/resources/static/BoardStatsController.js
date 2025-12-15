app.controller('BoardStatsController', function ($scope, $http) {

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

    $http.get('/api/stats/posts').then(function (res) {
        const data = res.data;

        // 1. 사용자별 게시글 수 (가로 막대)
        const userChartOpt = getCommonOptions();
        userChartOpt.indexAxis = 'y';

        new Chart(document.getElementById('userChart'), {
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
            options: userChartOpt
        });

        // 2. 사용자별 활동 점유율 (도넛)
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

        new Chart(document.getElementById('userDoughnut'), {
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

        // 3. 인기 게시글 (조회수 Top10) - 가로 막대
        const viewOpt = getCommonOptions();
        viewOpt.indexAxis = 'y';

        new Chart(document.getElementById('viewChart'), {
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

        // 4. 조회수 분포 (Polar Area)
        const polarOpt = getCommonOptions();
        polarOpt.plugins.legend = { display: false };
        polarOpt.scales = {
            r: {
                ticks: { backdropColor: 'transparent' },
                grid: { color: '#e5e5e5' }
            }
        };

        new Chart(document.getElementById('viewPolar'), {
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

        // 5. 일별 게시글 등록 추이 (라인)
        const lineOpt = getCommonOptions();

        lineOpt.layout = {
            padding: { right: 60, left: 10, top: 10, bottom: 10 }
        };

        lineOpt.scales = {
            x: {
                offset: true,
                ticks: {
                    autoSkip: true,
                    maxRotation: 0,
                    autoSkipPadding: 20
                },
                grid: { display: false }
            },
            y: {
                beginAtZero: true,
                ticks: { stepSize: 1 }
            }
        };

        lineOpt.elements = {
            line: { tension: 0.3 }
        };

        lineOpt.plugins = {
            ...lineOpt.plugins,
            tooltip: {
                intersect: false,
                mode: 'index'
            }
        };

        lineOpt.clip = false;

        new Chart(document.getElementById('dailyChart'), {
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
