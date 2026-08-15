import React, { useMemo } from 'react';
import {
  Area as RechartsArea,
  AreaChart as RechartsAreaChart,
  Bar as RechartsBar,
  BarChart as RechartsBarChart,
  CartesianGrid,
  ComposedChart as RechartsComposedChart,
  Line as RechartsLine,
  ResponsiveContainer,
  Tooltip as RechartsTooltip,
  XAxis,
  YAxis,
} from 'recharts';

// These roots follow the local HeroUI Pro chart wrappers in:
// HeroUIPro/herouipro-v3/src/components/{area-chart,composed-chart,bar-chart,chart-tooltip}.
// They are kept local so the HR app remains independently buildable.
export function AreaChartRoot({ data, children, height = 300, margin = { top: 8, right: 8, bottom: 0, left: 0 }, className = '' }) {
  return <div className={`hero-pro-chart-root ${className}`} data-slot="area-chart"><ResponsiveContainer width="100%" height={height}><RechartsAreaChart data={data} margin={margin}>{children}</RechartsAreaChart></ResponsiveContainer></div>;
}

export const AreaChart = Object.assign(AreaChartRoot, {
  Area: RechartsArea,
  Grid: CartesianGrid,
  Tooltip: RechartsTooltip,
  XAxis,
  YAxis,
});

export function ComposedChartRoot({ data, children, height = 300, margin = { top: 8, right: 10, bottom: 0, left: 0 }, className = '' }) {
  return <div className={`hero-pro-chart-root ${className}`} data-slot="composed-chart"><ResponsiveContainer width="100%" height={height}><RechartsComposedChart data={data} margin={margin}>{children}</RechartsComposedChart></ResponsiveContainer></div>;
}

export const ComposedChart = Object.assign(ComposedChartRoot, {
  Area: RechartsArea,
  Bar: RechartsBar,
  Grid: CartesianGrid,
  Line: RechartsLine,
  Tooltip: RechartsTooltip,
  XAxis,
  YAxis,
});

export function BarChartRoot({ data, children, height = 300, layout = 'horizontal', margin = { top: 8, right: 10, bottom: 0, left: 0 } }) {
  return <div className="hero-pro-chart-root" data-slot="bar-chart"><ResponsiveContainer width="100%" height={height}><RechartsBarChart data={data} layout={layout} margin={margin}>{children}</RechartsBarChart></ResponsiveContainer></div>;
}

export const BarChart = Object.assign(BarChartRoot, {
  Bar: RechartsBar,
  Grid: CartesianGrid,
  Tooltip: RechartsTooltip,
  XAxis,
  YAxis,
});

function tooltipColor(entry) {
  return entry?.stroke || entry?.color || entry?.fill || '#4f6ef7';
}

export function ChartTooltipContent({ active, payload, label, labelFormatter, valueFormatter = (value) => value }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="recruit-pro-tooltip" data-slot="chart-tooltip">
      <div className="recruit-pro-tooltip-label">{labelFormatter ? labelFormatter(label) : label}</div>
      {payload.map((entry, index) => (
        <div className="recruit-pro-tooltip-row" key={`${entry.dataKey || entry.name}-${index}`}>
          <span className="recruit-pro-tooltip-key"><i style={{ backgroundColor: tooltipColor(entry) }} />{entry.name || entry.dataKey}</span>
          <strong>{valueFormatter(entry.value)}</strong>
        </div>
      ))}
    </div>
  );
}

export const ChartTooltip = {
  Content: ChartTooltipContent,
};

export function chartColors() {
  return useMemo(() => ({
    blue: '#315fea',
    teal: '#139b9a',
    amber: '#d88916',
    green: '#16845a',
    red: '#d6414d',
    ink: '#172033',
    muted: '#7d899c',
    grid: '#e7ecf3',
  }), []);
}
