<!-- 個人 360 E 會員等級、F 財富潛力、G 產品結構：示意資料，標記照搬 prototype/chatbot.html（platform-mock，整合時刪除） -->
<template>
  <section class="card dash-card c360-e" data-area="E" aria-label="會員等級">
    <header class="dash-card__head"><h2 class="dash-card__title">會員等級</h2><span class="dash-card__meta">依 AuM 判定</span></header>
    <div class="dash-card__body">
      <ol class="tier" aria-label="會員等級：目前為菁英">
        <li class="tier__step is-done"><span class="tier__dot" aria-hidden="true"></span><strong>無</strong><span>—</span></li>
        <li class="tier__step is-done"><span class="tier__dot" aria-hidden="true"></span><strong>新富</strong><span>100 萬</span></li>
        <li class="tier__step is-current" aria-current="step"><span class="tier__dot" aria-hidden="true"></span><strong>菁英</strong><span>300 萬</span></li>
        <li class="tier__step"><span class="tier__dot" aria-hidden="true"></span><strong>登峰</strong><span>1,000 萬</span></li>
        <li class="tier__step"><span class="tier__dot" aria-hidden="true"></span><strong>私銀</strong><span>3,000 萬</span></li>
        <span class="tier__fill" style="--tier-progress:68.5%" aria-hidden="true"></span>
      </ol>
      <p class="tier__next">距 <strong>登峰</strong> 尚差 AuM <strong>180 萬</strong>（門檻 1,000 萬，目前 820 萬）</p>
      <div class="perks">
        <div><h3>目前權益・菁英</h3><ul><li>專屬理專服務</li><li>跨行轉帳免費 10 次／月</li><li>理財講座優先報名</li></ul></div>
        <div><h3>升等後・登峰</h3><ul><li>基金申購手續費 5 折</li><li>機場貴賓室 4 次／年</li><li>年度健康檢查</li></ul></div>
      </div></div>
  </section>
  <section class="card dash-card c360-f" data-area="F" aria-label="財富潛力">
    <header class="dash-card__head"><h2 class="dash-card__title">財富潛力</h2><span class="dash-card__meta">PR 值・同客群比較</span></header>
    <div class="dash-card__body"><svg class="chart chart--radar" viewBox="0 0 340 250" role="img" aria-label="財富潛力：本顧客 與 同客群平均"><polygon class="chart__grid" points="170,107.5 186.9,117.3 186.9,136.8 170,146.5 153.1,136.8 153.1,117.3"/><polygon class="chart__grid" points="170,88 203.8,107.5 203.8,146.5 170,166 136.2,146.5 136.2,107.5"/><polygon class="chart__grid" points="170,68.5 220.7,97.8 220.7,156.3 170,185.5 119.3,156.3 119.3,97.8"/><polygon class="chart__grid" points="170,49 237.5,88 237.5,166 170,205 102.5,166 102.5,88"/><line class="chart__grid" x1="170" y1="127" x2="170" y2="49"/><text class="chart__tick" x="170" y="35" text-anchor="middle">年收入</text><line class="chart__grid" x1="170" y1="127" x2="237.5" y2="88"/><text class="chart__tick" x="247.9" y="86" text-anchor="start">綜所稅</text><line class="chart__grid" x1="170" y1="127" x2="237.5" y2="166"/><text class="chart__tick" x="247.9" y="176" text-anchor="start">信用卡額度</text><line class="chart__grid" x1="170" y1="127" x2="170" y2="205"/><text class="chart__tick" x="170" y="227" text-anchor="middle">年度刷卡金額</text><line class="chart__grid" x1="170" y1="127" x2="102.5" y2="166"/><text class="chart__tick" x="92.1" y="176" text-anchor="end">不動產估值</text><line class="chart__grid" x1="170" y1="127" x2="102.5" y2="88"/><text class="chart__tick" x="92.1" y="80" text-anchor="end">股利</text><g class="chart__series s2"><polygon class="chart__area" points="170,84.1 203.8,107.5 203.8,146.5 170,166 129.5,150.4 139.6,109.5"/><circle class="chart__dot" cx="170" cy="84.1" r="4"><title>同客群平均｜年收入：55</title></circle><circle class="chart__dot" cx="203.8" cy="107.5" r="4"><title>同客群平均｜綜所稅：50</title></circle><circle class="chart__dot" cx="203.8" cy="146.5" r="4"><title>同客群平均｜信用卡額度：50</title></circle><circle class="chart__dot" cx="170" cy="166" r="4"><title>同客群平均｜年度刷卡金額：50</title></circle><circle class="chart__dot" cx="129.5" cy="150.4" r="4"><title>同客群平均｜不動產估值：60</title></circle><circle class="chart__dot" cx="139.6" cy="109.5" r="4"><title>同客群平均｜股利：45</title></circle></g><g class="chart__series s1"><polygon class="chart__area" points="170,70.8 213.9,101.7 207.2,148.5 170,158.2 112.6,160.1 129.5,103.6"/><circle class="chart__dot" cx="170" cy="70.8" r="4"><title>本顧客｜年收入：72（180 萬）</title></circle><circle class="chart__dot" cx="213.9" cy="101.7" r="4"><title>本顧客｜綜所稅：65（12 萬）</title></circle><circle class="chart__dot" cx="207.2" cy="148.5" r="4"><title>本顧客｜信用卡額度：55（30 萬）</title></circle><circle class="chart__dot" cx="170" cy="158.2" r="4"><title>本顧客｜年度刷卡金額：40（36 萬）</title></circle><circle class="chart__dot" cx="112.6" cy="160.1" r="4"><title>本顧客｜不動產估值：85（2,400 萬）</title></circle><circle class="chart__dot" cx="129.5" cy="103.6" r="4"><title>本顧客｜股利：60（8 萬）</title></circle></g></svg><ul class="legend"><li><span class="legend__swatch s1"></span>本顧客</li><li><span class="legend__swatch s2"></span>同客群平均</li></ul></div>
  </section>
  <section class="card dash-card c360-g" data-area="G" aria-label="產品結構">
    <header class="dash-card__head"><h2 class="dash-card__title">產品結構</h2><span class="dash-card__meta">本顧客 vs 客群</span></header>
    <div class="dash-card__body">
      <div class="kpi">
        <span class="kpi__label">理財顧客存量收益率</span>
        <span class="kpi__value">1.86%</span>
        <span class="kpi__cmp">客群 1.42%・<strong>+0.44 pp</strong></span>
      </div>
      <ul class="gbars" aria-label="存貸比、活存比、理財 AuM 比：本顧客與客群">
        <li><span class="gbars__label">存貸比</span><span class="gbars__bars"><span class="gbars__row" title="本顧客｜存貸比 62%"><span class="gbars__bar s1" style="width:62%"></span><span class="gbars__value">62%</span></span><span class="gbars__row" title="客群｜存貸比 48%"><span class="gbars__bar s2" style="width:48%"></span><span class="gbars__value">48%</span></span></span></li>
        <li><span class="gbars__label">活存比</span><span class="gbars__bars"><span class="gbars__row" title="本顧客｜活存比 29%"><span class="gbars__bar s1" style="width:29%"></span><span class="gbars__value">29%</span></span><span class="gbars__row" title="客群｜活存比 45%"><span class="gbars__bar s2" style="width:45%"></span><span class="gbars__value">45%</span></span></span></li>
        <li><span class="gbars__label">理財 AuM 比</span><span class="gbars__bars"><span class="gbars__row" title="本顧客｜理財 AuM 比 49%"><span class="gbars__bar s1" style="width:49%"></span><span class="gbars__value">49%</span></span><span class="gbars__row" title="客群｜理財 AuM 比 38%"><span class="gbars__bar s2" style="width:38%"></span><span class="gbars__value">38%</span></span></span></li>
      </ul>
      <ul class="legend"><li><span class="legend__swatch s1"></span>本顧客</li><li><span class="legend__swatch s2"></span>客群</li></ul></div>
  </section>
</template>
