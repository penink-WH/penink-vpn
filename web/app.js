// penink VPN — 下載頁互動
document.addEventListener('DOMContentLoaded', () => {
    const btn = document.getElementById('downloadBtn');
    const versionText = document.getElementById('versionText');

    // 檢查 APK 是否存在（靜態託管無法用 HEAD 跨域時瀏覽器直接下載即可）
    if (btn) {
        btn.addEventListener('error', () => {
            versionText.textContent = 'APK 尚未上傳，請先 build 後放入 web/ 目錄';
        });
    }

    // 滾動時導航列背景加深
    const nav = document.querySelector('.nav');
    window.addEventListener('scroll', () => {
        if (window.scrollY > 60) {
            nav.style.background = 'rgba(10,10,24,0.92)';
        } else {
            nav.style.background = 'rgba(10,10,24,0.6)';
        }
    });

    // 平滑滾動到各區塊
    document.querySelectorAll('a[href^="#"]').forEach(a => {
        a.addEventListener('click', e => {
            e.preventDefault();
            const target = document.querySelector(a.getAttribute('href'));
            if (target) target.scrollIntoView({ behavior: 'smooth', block: 'start' });
        });
    });
});