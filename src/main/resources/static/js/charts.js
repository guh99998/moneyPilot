import { el, fmtMoney } from './ui.js';

/*
 * Gráficos em SVG puro, sem biblioteca. Cada gráfico desenha na largura real do
 * contêiner (ResizeObserver), então o texto mantém o tamanho em qualquer tela.
 * Especificação: barras ≤ 24px com topo arredondado de 4px, linhas de 2px,
 * pontos de 8px com anel na cor da superfície, grade em hairline sólida.
 */

const SVG_NS = 'http://www.w3.org/2000/svg';
const compactMoney = new Intl.NumberFormat('pt-BR', {
    style: 'currency', currency: 'BRL', notation: 'compact', maximumFractionDigits: 1
});

function svg(tag, attrs = {}, children = []) {
    const node = document.createElementNS(SVG_NS, tag);
    Object.entries(attrs).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== false) node.setAttribute(key, value);
    });
    (Array.isArray(children) ? children : [children]).filter(Boolean).forEach((child) => node.append(child));
    return node;
}

export function compactMoneyLabel(value) {
    return Number(value) === 0 ? 'R$ 0' : compactMoney.format(Number(value));
}

/** Escala "bonita": 0 / 1.000 / 2.000 em vez de 0 / 913 / 1.826. */
function niceTicks(min, max, count = 4) {
    if (min === max) {
        const pad = Math.abs(min) || 100;
        min -= pad;
        max += pad;
    }
    const rough = (max - min) / count;
    const magnitude = 10 ** Math.floor(Math.log10(rough));
    const residual = rough / magnitude;
    const step = (residual > 5 ? 10 : residual > 2 ? 5 : residual > 1 ? 2 : 1) * magnitude;
    const start = Math.floor(min / step) * step;
    const end = Math.ceil(max / step) * step;
    const ticks = [];
    for (let value = start; value <= end + step / 2; value += step) ticks.push(Math.round(value * 100) / 100);
    return ticks;
}

/** Contêiner comum: redesenha quando muda de largura e cuida do tooltip. */
function responsiveChart({ height, ariaLabel, draw }) {
    const container = el('div', { class: 'chart', role: 'img', 'aria-label': ariaLabel });
    const tooltip = el('div', { class: 'chart-tooltip', hidden: true });
    let lastWidth = 0;

    function showTooltip(x, y, title, rows) {
        tooltip.replaceChildren(
            el('strong', { text: title }),
            ...rows.map((row) => el('div', { class: 'row' }, [
                row.color ? el('i', { class: 'key', style: `background:${row.color}` }) : null,
                el('span', { text: row.text })
            ]))
        );
        tooltip.hidden = false;
        // Mantém o tooltip dentro do contêiner nas bordas.
        const half = Math.min(tooltip.offsetWidth / 2, lastWidth / 2);
        tooltip.style.left = `${Math.min(Math.max(x, half), lastWidth - half)}px`;
        tooltip.style.top = `${y}px`;
    }

    function hideTooltip() {
        tooltip.hidden = true;
    }

    function render(width) {
        if (!width || Math.abs(width - lastWidth) < 1) return;
        lastWidth = width;
        const root = svg('svg', { width, height, viewBox: `0 0 ${width} ${height}`, 'aria-hidden': 'true' });
        draw(root, width, { showTooltip, hideTooltip });
        container.replaceChildren(root, tooltip);
    }

    new ResizeObserver((entries) => render(Math.floor(entries[0].contentRect.width))).observe(container);
    container.addEventListener('mouseleave', hideTooltip);
    return container;
}

/**
 * Linha de um nível ao longo do tempo (ex.: saldo projetado).
 * points: [{ label, value, title, rows:[{text,color}] }]
 */
export function lineChart({ points, height = 240, ariaLabel, lineColor = 'var(--chart-line)', negativeColor = 'var(--critical)' }) {
    return responsiveChart({
        height,
        ariaLabel,
        draw(root, width, tip) {
            const values = points.map((point) => Number(point.value) || 0);
            const ticks = niceTicks(Math.min(0, ...values), Math.max(0, ...values));
            const yMin = ticks[0];
            const yMax = ticks[ticks.length - 1];

            const left = 62;
            const right = 16;
            const top = 22;
            const bottom = 28;
            const plotW = width - left - right;
            const plotH = height - top - bottom;
            const y = (value) => top + plotH - ((value - yMin) / (yMax - yMin)) * plotH;
            const step = points.length > 1 ? plotW / (points.length - 1) : 0;
            const x = (index) => left + (points.length > 1 ? index * step : plotW / 2);

            // grade + eixo y
            ticks.forEach((tick) => {
                root.append(
                    svg('line', { class: tick === 0 ? 'zero-line' : 'grid-line', x1: left, x2: width - right, y1: y(tick), y2: y(tick) }),
                    svg('text', { class: 'axis-label', x: left - 8, y: y(tick) + 4, 'text-anchor': 'end' }, document.createTextNode(compactMoneyLabel(tick)))
                );
            });

            // eixo x: o último rótulo sempre aparece; os outros só se não encostarem nele nem no anterior
            const MIN_GAP = 52;
            const lastX = x(points.length - 1);
            let previousX = -Infinity;
            points.forEach((point, index) => {
                const isLast = index === points.length - 1;
                if (!isLast && (x(index) - previousX < MIN_GAP || lastX - x(index) < MIN_GAP)) return;
                previousX = x(index);
                root.append(svg('text', {
                    class: 'axis-label', x: x(index), y: height - 8,
                    'text-anchor': index === 0 ? 'start' : isLast ? 'end' : 'middle'
                }, document.createTextNode(point.label)));
            });

            // área (lavagem de 10%) até a linha do zero
            const zeroY = y(Math.max(yMin, Math.min(0, yMax)));
            const linePath = values.map((value, index) => `${index ? 'L' : 'M'}${x(index)},${y(value)}`).join(' ');
            root.append(
                svg('path', { d: `${linePath} L${x(values.length - 1)},${zeroY} L${x(0)},${zeroY} Z`, fill: lineColor, 'fill-opacity': '.1' }),
                svg('path', { d: linePath, fill: 'none', stroke: lineColor, 'stroke-width': 2, 'stroke-linejoin': 'round', 'stroke-linecap': 'round' })
            );

            // pontos (anel de 2px na cor da superfície)
            values.forEach((value, index) => {
                root.append(svg('circle', {
                    cx: x(index), cy: y(value), r: 4.5,
                    fill: value < 0 ? negativeColor : lineColor, stroke: 'var(--surface)', 'stroke-width': 2
                }));
            });

            // rótulos diretos só no fim e no ponto mais baixo (quando negativo)
            const lastIndex = values.length - 1;
            const minIndex = values.indexOf(Math.min(...values));
            const labelAt = (index) => {
                const value = values[index];
                const anchor = index === lastIndex ? 'end' : index === 0 ? 'start' : 'middle';
                const above = y(value) - 10 > top - 8;
                root.append(svg('text', {
                    class: `value-label ${value < 0 ? 'neg' : ''}`,
                    x: x(index), y: above ? y(value) - 10 : y(value) + 18, 'text-anchor': anchor
                }, document.createTextNode(fmtMoney(value))));
            };
            labelAt(lastIndex);
            if (values[minIndex] < 0 && minIndex !== lastIndex) labelAt(minIndex);

            // camada de hover: faixa inteira de cada ponto, maior que a marca
            const crosshair = svg('line', { class: 'crosshair', y1: top, y2: top + plotH, visibility: 'hidden' });
            root.append(crosshair);
            points.forEach((point, index) => {
                const band = step || plotW;
                const hit = svg('rect', {
                    class: 'hit', x: x(index) - band / 2, y: top, width: band, height: plotH
                });
                const onEnter = () => {
                    crosshair.setAttribute('x1', x(index));
                    crosshair.setAttribute('x2', x(index));
                    crosshair.setAttribute('visibility', 'visible');
                    tip.showTooltip(x(index), y(values[index]), point.title, point.rows);
                };
                hit.addEventListener('mouseenter', onEnter);
                hit.addEventListener('touchstart', onEnter, { passive: true });
                hit.addEventListener('mouseleave', () => { crosshair.setAttribute('visibility', 'hidden'); tip.hideTooltip(); });
                root.append(hit);
            });
        }
    });
}

function roundedTopBar(x, yTop, width, yBase, radius = 4) {
    const h = yBase - yTop;
    if (h <= 0) return '';
    const r = Math.min(radius, h, width / 2);
    return `M${x},${yBase} V${yTop + r} Q${x},${yTop} ${x + r},${yTop} H${x + width - r} Q${x + width},${yTop} ${x + width},${yTop + r} V${yBase} Z`;
}

/**
 * Colunas agrupadas (ex.: receitas x despesas por mês).
 * groups: [{ label, title, values:[...] }]   series: [{ name, color }]
 */
export function groupedColumns({ groups, series, height = 240, ariaLabel }) {
    return responsiveChart({
        height,
        ariaLabel,
        draw(root, width, tip) {
            const all = groups.flatMap((group) => group.values.map(Number));
            const ticks = niceTicks(0, Math.max(0, ...all));
            const yMax = ticks[ticks.length - 1] || 1;

            const left = 62;
            const right = 8;
            const top = 12;
            const bottom = 28;
            const plotW = width - left - right;
            const plotH = height - top - bottom;
            const y = (value) => top + plotH - (value / yMax) * plotH;
            const band = plotW / groups.length;
            const gap = 2;
            const barW = Math.max(4, Math.min(24, (band * 0.7 - gap * (series.length - 1)) / series.length));
            const groupW = barW * series.length + gap * (series.length - 1);

            ticks.forEach((tick) => {
                root.append(
                    svg('line', { class: tick === 0 ? 'zero-line' : 'grid-line', x1: left, x2: width - right, y1: y(tick), y2: y(tick) }),
                    svg('text', { class: 'axis-label', x: left - 8, y: y(tick) + 4, 'text-anchor': 'end' }, document.createTextNode(compactMoneyLabel(tick)))
                );
            });

            groups.forEach((group, groupIndex) => {
                const center = left + band * groupIndex + band / 2;
                const start = center - groupW / 2;
                group.values.forEach((value, seriesIndex) => {
                    const d = roundedTopBar(start + seriesIndex * (barW + gap), y(Number(value) || 0), barW, y(0));
                    if (d) root.append(svg('path', { d, fill: series[seriesIndex].color }));
                });
                root.append(svg('text', { class: 'axis-label', x: center, y: height - 8, 'text-anchor': 'middle' }, document.createTextNode(group.label)));

                const hit = svg('rect', { class: 'hit', x: left + band * groupIndex, y: top, width: band, height: plotH });
                const onEnter = () => tip.showTooltip(center, y(Math.max(...group.values.map(Number))), group.title,
                    series.map((item, index) => ({ color: item.color, text: `${item.name}: ${fmtMoney(group.values[index])}` })));
                hit.addEventListener('mouseenter', onEnter);
                hit.addEventListener('touchstart', onEnter, { passive: true });
                hit.addEventListener('mouseleave', tip.hideTooltip);
                root.append(hit);
            });
        }
    });
}

/** Legenda: sempre presente com duas séries ou mais; o texto fica na cor de texto. */
export function legend(series) {
    return el('div', { class: 'legend' }, series.map((item) => el('span', {}, [
        el('i', { style: `background:${item.color}` }),
        el('span', { text: item.name })
    ])));
}

/** Versão em tabela de qualquer gráfico, recolhida por padrão. */
export function tableToggle(headers, rows) {
    return el('details', { class: 'table-toggle' }, [
        el('summary', { text: 'Ver em tabela' }),
        el('div', { class: 'table-wrap' }, el('table', {}, [
            el('thead', {}, el('tr', {}, headers.map((header, index) => el('th', { class: index ? 'num' : '', text: header })))),
            el('tbody', {}, rows.map((row) => el('tr', {}, row.map((cell, index) => el('td', { class: index ? 'num' : '', text: cell })))))
        ]))
    ]);
}
