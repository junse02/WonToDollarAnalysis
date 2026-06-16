// 환율 추이 차트
let rateChartInstance = null;

function buildRateChart(labels, data) {
    const canvas = document.getElementById('rateChart');
    if (!canvas) return null;
    const ctx = canvas.getContext('2d');

    const gradient = ctx.createLinearGradient(0, 0, 0, 300);
    gradient.addColorStop(0, 'rgba(37, 99, 235, 0.25)');
    gradient.addColorStop(1, 'rgba(37, 99, 235, 0.01)');

    return new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [{
                label: 'USD/KRW',
                data: data,
                borderColor: '#2563eb',
                backgroundColor: gradient,
                borderWidth: 2.5,
                pointRadius: data.length > 45 ? 2 : 4,
                pointBackgroundColor: '#2563eb',
                pointHoverRadius: 6,
                fill: true,
                tension: 0.3
            }]
        },
        options: {
            responsive: true,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label: ctx => `  ${ctx.parsed.y.toLocaleString('ko-KR', { minimumFractionDigits: 2 })} ₩`
                    }
                }
            },
            scales: {
                x: {
                    grid: { color: '#f1f5f9' },
                    ticks: { color: '#64748b', maxTicksLimit: 8 }
                },
                y: {
                    grid: { color: '#f1f5f9' },
                    ticks: {
                        color: '#64748b',
                        callback: val => val.toLocaleString('ko-KR') + ' ₩'
                    }
                }
            }
        }
    });
}

if (CHART_DATA.length > 0) {
    rateChartInstance = buildRateChart(CHART_LABELS, CHART_DATA);
}

// 기간 버튼: 페이지 리로드 없이 차트만 갱신
function changePeriod(days, btn) {
    document.querySelectorAll('.period-btn').forEach(b => b.classList.remove('active'));
    if (btn) btn.classList.add('active');

    fetch('/api/rate/history?days=' + days)
        .then(r => r.json())
        .then(d => {
            if (!d.data || d.data.length === 0) return;
            if (rateChartInstance) {
                rateChartInstance.data.labels = d.labels;
                rateChartInstance.data.datasets[0].data = d.data;
                rateChartInstance.data.datasets[0].pointRadius = d.data.length > 45 ? 2 : 4;
                rateChartInstance.update();
            } else {
                rateChartInstance = buildRateChart(d.labels, d.data);
            }
        })
        .catch(err => console.error('기간 데이터 로드 실패:', err));
}

// 키워드 분석 차트
if (KW_LABELS.length > 0) {
    const kwCtx = document.getElementById('keywordChart').getContext('2d');

    // 색상: 달러 강세 관련 → 빨강, 완화 → 초록, 기타 → 파랑
    const colors = KW_LABELS.map(label => {
        if (['연준 긴축·금리 인상', '달러 강세', '경기침체 우려', '지정학 리스크', '무역·수입 압박'].includes(label)) {
            return 'rgba(239, 68, 68, 0.75)';
        } else if (['연준 완화·금리 인하', '달러 약세', '무역·수출 호조', '외국인 자금 유입'].includes(label)) {
            return 'rgba(34, 197, 94, 0.75)';
        }
        return 'rgba(99, 102, 241, 0.75)';
    });

    new Chart(kwCtx, {
        type: 'bar',
        data: {
            labels: KW_LABELS,
            datasets: [{
                label: '언급 횟수',
                data: KW_VALUES,
                backgroundColor: colors,
                borderRadius: 6,
                borderSkipped: false
            }]
        },
        options: {
            indexAxis: 'y',
            responsive: true,
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label: ctx => `  ${ctx.parsed.x}건 언급`
                    }
                }
            },
            scales: {
                x: {
                    grid: { color: '#f1f5f9' },
                    ticks: { color: '#64748b', precision: 0 }
                },
                y: {
                    grid: { display: false },
                    ticks: { color: '#1e293b', font: { size: 11 } }
                }
            }
        }
    });
}

// 데이터 새로고침
function refreshData() {
    const btn = document.querySelector('[onclick="refreshData()"]');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>로딩 중...';
    window.location.reload();
}