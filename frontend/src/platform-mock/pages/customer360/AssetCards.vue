<!-- 個人 360 C 資產 AuM、D 負債 LuM＋信用卡：示意資料，標記照搬 prototype/chatbot.html（platform-mock，整合時刪除） -->
<template>
  <section class="card dash-card c360-c" data-area="C" aria-label="資產 AuM">
    <header class="dash-card__head"><h2 class="dash-card__title">資產 AuM</h2><div class="seg" role="radiogroup" aria-label="比較對象">
        <input class="sr-only" type="radio" name="aum-cmp" id="aum-cmp-self" checked><label for="aum-cmp-self">顧客</label>
        <input class="sr-only" type="radio" name="aum-cmp" id="aum-cmp-group"><label for="aum-cmp-group">客群</label>
        <input class="sr-only" type="radio" name="aum-cmp" id="aum-cmp-top"><label for="aum-cmp-top">Top 10%</label>
      </div><span class="dash-card__meta">2026/10/19・萬元</span></header>
    <div class="dash-card__body">
      <div class="hero-num"><span class="hero-num__value">820</span><span class="hero-num__unit">萬元</span><span class="delta delta--up">較上季 +3.2%</span></div>
      <div class="aum">
        <div class="aum__chart"><svg class="chart chart--rings" viewBox="0 0 220 220" role="img" aria-label="資產配置：活存 14.6%、定存 36.6%、基金 30.5%、金融商品 9.8%、保險 6.1%、財金商品 2.4%"><path class="chart__track" d="M110,12 A98,98 0 1 1 12,110" stroke-width="10" pathLength="100"/><path class="chart__arc c1" d="M110,12 A98,98 0 1 1 12,110" stroke-width="10" pathLength="100" stroke-dasharray="36.5 100"><title>活存｜120 萬・14.6%</title></path><text class="chart__tick chart__tick--ring" x="104" y="16" text-anchor="end">活存</text><line class="chart__marker cmp cmp--group" x1="157.5" y1="187.6" x2="164.9" y2="199.5"><title>同客群平均｜活存 22%</title></line><line class="chart__marker cmp cmp--top" x1="194.1" y1="75.2" x2="207" y2="69.8"><title>客群 Top 10%｜活存 10%</title></line><path class="chart__track" d="M110,26 A84,84 0 1 1 26,110" stroke-width="10" pathLength="100"/><path class="chart__arc c2" d="M110,26 A84,84 0 1 1 26,110" stroke-width="10" pathLength="100" stroke-dasharray="91.5 100"><title>定存｜300 萬・36.6%</title></path><text class="chart__tick chart__tick--ring" x="104" y="30" text-anchor="end">定存</text><line class="chart__marker cmp cmp--group" x1="164.4" y1="164.4" x2="174.3" y2="174.3"><title>同客群平均｜定存 20%</title></line><line class="chart__marker cmp cmp--top" x1="185.5" y1="125" x2="199.3" y2="127.8"><title>客群 Top 10%｜定存 15%</title></line><path class="chart__track" d="M110,40 A70,70 0 1 1 40,110" stroke-width="10" pathLength="100"/><path class="chart__arc c3" d="M110,40 A70,70 0 1 1 40,110" stroke-width="10" pathLength="100" stroke-dasharray="76.3 100"><title>基金｜250 萬・30.5%</title></path><text class="chart__tick chart__tick--ring" x="104" y="44" text-anchor="end">基金</text><line class="chart__marker cmp cmp--group" x1="122.3" y1="171.8" x2="125" y2="185.5"><title>同客群平均｜基金 25%</title></line><line class="chart__marker cmp cmp--top" x1="85.9" y1="168.2" x2="80.5" y2="181.1"><title>客群 Top 10%｜基金 30%</title></line><path class="chart__track" d="M110,54 A56,56 0 1 1 54,110" stroke-width="10" pathLength="100"/><path class="chart__arc c4" d="M110,54 A56,56 0 1 1 54,110" stroke-width="10" pathLength="100" stroke-dasharray="24.5 100"><title>金融商品｜80 萬・9.8%</title></path><text class="chart__tick chart__tick--ring" x="104" y="58" text-anchor="end">金融商品</text><line class="chart__marker cmp cmp--group" x1="158.1" y1="119.6" x2="171.8" y2="122.3"><title>同客群平均｜金融商品 15%</title></line><line class="chart__marker cmp cmp--top" x1="119.6" y1="158.1" x2="122.3" y2="171.8"><title>客群 Top 10%｜金融商品 25%</title></line><path class="chart__track" d="M110,68 A42,42 0 1 1 68,110" stroke-width="10" pathLength="100"/><path class="chart__arc c5" d="M110,68 A42,42 0 1 1 68,110" stroke-width="10" pathLength="100" stroke-dasharray="15.3 100"><title>保險｜50 萬・6.1%</title></path><text class="chart__tick chart__tick--ring" x="104" y="72" text-anchor="end">保險</text><line class="chart__marker cmp cmp--group" x1="145" y1="108.6" x2="159" y2="108.1"><title>同客群平均｜保險 13%</title></line><line class="chart__marker cmp cmp--top" x1="144.6" y1="104.5" x2="158.4" y2="102.3"><title>客群 Top 10%｜保險 12%</title></line><path class="chart__track" d="M110,82 A28,28 0 1 1 82,110" stroke-width="10" pathLength="100"/><path class="chart__arc c6" d="M110,82 A28,28 0 1 1 82,110" stroke-width="10" pathLength="100" stroke-dasharray="6 100"><title>財金商品｜20 萬・2.4%</title></path><text class="chart__tick chart__tick--ring" x="104" y="86" text-anchor="end">財金商品</text><line class="chart__marker cmp cmp--group" x1="121.7" y1="92.5" x2="129.4" y2="80.9"><title>同客群平均｜財金商品 5%</title></line><line class="chart__marker cmp cmp--top" x1="127" y1="97.7" x2="138.3" y2="89.4"><title>客群 Top 10%｜財金商品 8%</title></line></svg>
          <p class="aum__scale">圓弧滿格 = 40%<span class="cmp cmp--group cmp--top">・<i class="aum__tick" aria-hidden="true"></i>比較基準</span></p>
        </div>
        <table class="aum-table">
          <thead><tr><th scope="col">項目</th><th scope="col">金額</th><th scope="col">佔比</th><th scope="col" class="cmp cmp--group">客群</th><th scope="col" class="cmp cmp--top">Top 10%</th></tr></thead>
          <tbody>
            <tr><th scope="row"><span class="legend__swatch c1"></span>活存</th><td>120</td><td>14.6%</td><td class="cmp cmp--group">22%<small>−7.4</small></td><td class="cmp cmp--top">10%<small>+4.6</small></td></tr>
            <tr><th scope="row"><span class="legend__swatch c2"></span>定存</th><td>300</td><td>36.6%</td><td class="cmp cmp--group">20%<small>+16.6</small></td><td class="cmp cmp--top">15%<small>+21.6</small></td></tr>
            <tr><th scope="row"><span class="legend__swatch c3"></span>基金</th><td>250</td><td>30.5%</td><td class="cmp cmp--group">25%<small>+5.5</small></td><td class="cmp cmp--top">30%<small>+0.5</small></td></tr>
            <tr><th scope="row"><span class="legend__swatch c4"></span>金融商品</th><td>80</td><td>9.8%</td><td class="cmp cmp--group">15%<small>−5.2</small></td><td class="cmp cmp--top">25%<small>−15.2</small></td></tr>
            <tr><th scope="row"><span class="legend__swatch c5"></span>保險</th><td>50</td><td>6.1%</td><td class="cmp cmp--group">13%<small>−6.9</small></td><td class="cmp cmp--top">12%<small>−5.9</small></td></tr>
            <tr><th scope="row"><span class="legend__swatch c6"></span>財金商品</th><td>20</td><td>2.4%</td><td class="cmp cmp--group">5%<small>−2.6</small></td><td class="cmp cmp--top">8%<small>−5.6</small></td></tr>
          </tbody>
          <tfoot><tr><th scope="row">合計</th><td>820</td><td>100%</td><td class="cmp cmp--group">100%</td><td class="cmp cmp--top">100%</td></tr></tfoot>
        </table>
      </div></div>
  </section>
  <section class="card dash-card c360-d" data-area="D" aria-label="負債 LuM＋信用卡">
    <header class="dash-card__head"><h2 class="dash-card__title">負債 LuM＋信用卡</h2><span class="dash-card__meta">2026/10/19・萬元</span></header>
    <div class="dash-card__body">
      <div class="hero-num"><span class="hero-num__value">682</span><span class="hero-num__unit">萬元</span><span class="delta">負債／資產 0.83</span></div>
      <div class="stack" role="img" aria-label="房貸・購置 76.2%、房貸・周轉 8.8%、副擔保 5.9%、信貸 5.1%、金融資產融資 2.2%、信用卡已使用 1.8%">
        <span class="stack__seg c1" style="flex-basis:76.2%" title="房貸・購置 76.2%｜520 萬"></span><span class="stack__seg c2" style="flex-basis:8.8%" title="房貸・周轉 8.8%｜60 萬"></span><span class="stack__seg c3" style="flex-basis:5.9%" title="副擔保 5.9%｜40 萬"></span><span class="stack__seg c4" style="flex-basis:5.1%" title="信貸 5.1%｜35 萬"></span><span class="stack__seg c5" style="flex-basis:2.2%" title="金融資產融資 2.2%｜15 萬"></span><span class="stack__seg c6" style="flex-basis:1.8%" title="信用卡已使用 1.8%｜12 萬"></span>
      </div>
      <ul class="legend legend--table">
        <li><span class="legend__swatch c1"></span>房貸・購置<span class="legend__pct">76.2%</span><strong>520 萬</strong></li>
        <li><span class="legend__swatch c2"></span>房貸・周轉<span class="legend__pct">8.8%</span><strong>60 萬</strong></li>
        <li><span class="legend__swatch c3"></span>副擔保<span class="legend__pct">5.9%</span><strong>40 萬</strong></li>
        <li><span class="legend__swatch c4"></span>信貸<span class="legend__pct">5.1%</span><strong>35 萬</strong></li>
        <li><span class="legend__swatch c5"></span>金融資產融資<span class="legend__pct">2.2%</span><strong>15 萬</strong></li>
        <li><span class="legend__swatch c6"></span>信用卡已使用<span class="legend__pct">1.8%</span><strong>12 萬</strong></li>
      </ul>
      <div class="meter" title="信用卡已使用 12 萬／額度 30 萬">
        <div class="meter__head"><span>信用卡額度使用</span><strong>12 / 30 萬・40%</strong></div>
        <span class="meter__track"><span class="meter__bar" style="width:40%"></span></span>
      </div></div>
  </section>
</template>
