import React, { useMemo } from 'react';
import { Legend, Tooltip, XAxis, YAxis } from 'recharts';
import { AreaChart, BarChart, ChartTooltipContent, ComposedChart, chartColors } from './hero-ui-pro-primitives.jsx';
import HeroAgenda from './hero-ui-pro-agenda.jsx';

const STATUS_LABELS = { draft: '草稿', approval: '审批中', open: '招聘中', rejected: '已驳回', closed: '已关闭', cancelled: '已取消' };
const STATUS_ORDER = { approval: 0, open: 1, draft: 2, rejected: 3, closed: 4, cancelled: 5 };

function number(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : 0;
}

function formatNumber(value) {
  return number(value).toLocaleString('zh-CN');
}

function formatPercent(value) {
  return `${number(value).toFixed(0)}%`;
}

function normalizeMonths(items) {
  return (Array.isArray(items) ? items : []).map((item, index) => ({
    month: item?.label || `${index + 1}月`,
    resumes: number(item?.resumes),
    interviews: number(item?.interviews),
    hires: number(item?.hires),
  }));
}

function normalizeDemands(items) {
  return (Array.isArray(items) ? items : []).map((item) => {
    const hc = number(item?.hc || item?.planHeadcount);
    const linkedCount = number(item?.linkedCount || item?.candidateCount);
    const candidateCover = Math.min(linkedCount, hc);
    return {
      id: item?.id || item?.demandNo || '',
      position: item?.position || '岗位待定',
      dept: item?.dept || '部门待定',
      hc,
      linkedCount,
      candidateCover,
      coverageGap: Math.max(hc - candidateCover, 0),
      coverageRate: hc ? Math.round((candidateCover / hc) * 100) : 0,
      status: item?.status || 'draft',
      statusLabel: item?.statusLabel || STATUS_LABELS[item?.status] || '草稿',
      urgency: item?.urgency || 'normal',
      urgencyLabel: item?.urgencyLabel || '普通',
      date: item?.date || '',
    };
  });
}

function buildPortfolio(demands) {
  const groups = new Map();
  demands.forEach((item) => {
    const key = item.dept || '部门待定';
    const current = groups.get(key) || { dept: key, demandCount: 0, plannedHc: 0, candidateCover: 0, coverageGap: 0 };
    current.demandCount += 1;
    current.plannedHc += item.hc;
    current.candidateCover += item.candidateCover;
    current.coverageGap += item.coverageGap;
    groups.set(key, current);
  });
  return Array.from(groups.values()).map((item) => ({
    ...item,
    coverageRate: item.plannedHc ? Math.round((item.candidateCover / item.plannedHc) * 100) : 0,
  })).sort((a, b) => b.coverageGap - a.coverageGap);
}

function buildDeliveryRows(demands) {
  const urgencyWeight = { very: 0, high: 1, normal: 2, low: 3 };
  return demands.map((item) => {
    let deliveryState = '需补充';
    let deliveryStateLabel = '需要补充候选人';
    if (item.status === 'closed') {
      deliveryState = '已关闭';
      deliveryStateLabel = '项目已关闭';
    } else if (item.status === 'approval') {
      deliveryState = '待审批';
      deliveryStateLabel = '审批通过后启动';
    } else if (item.coverageRate >= 100) {
      deliveryState = '可交付';
      deliveryStateLabel = '候选人覆盖充足';
    } else if (item.urgency === 'very' || item.urgency === 'high') {
      deliveryState = '优先补齐';
      deliveryStateLabel = '高优先级缺口';
    }
    return { ...item, deliveryState, deliveryStateLabel };
  }).sort((a, b) => {
    const stateWeight = { '优先补齐': 0, '需补充': 1, '待审批': 2, '可交付': 3, '已关闭': 4 };
    return (stateWeight[a.deliveryState] ?? 9) - (stateWeight[b.deliveryState] ?? 9)
      || (urgencyWeight[a.urgency] ?? 9) - (urgencyWeight[b.urgency] ?? 9)
      || b.coverageGap - a.coverageGap;
  });
}

function buildResourceLoad(events) {
  const groups = new Map();
  const interviewers = new Map();
  const totals = { total: 0, scheduled: 0, evaluating: 0, completed: 0 };
  const getStage = (event) => {
    if (event?.status === 'evaluating') return 'evaluating';
    if (event?.status === 'done' || event?.status === 'offer' || event?.status === 'onboard') return 'completed';
    return 'scheduled';
  };
  events.forEach((event) => {
    const position = event?.position || '岗位待定';
    const stage = getStage(event);
    const current = groups.get(position) || { position, total: 0, scheduled: 0, evaluating: 0, completed: 0 };
    current.total += 1;
    current[stage] += 1;
    groups.set(position, current);
    const interviewer = event?.interviewer || '面试官待分配';
    interviewers.set(interviewer, (interviewers.get(interviewer) || 0) + 1);
    totals.total += 1;
    totals[stage] += 1;
  });
  const actionEvents = events
    .filter((event) => ['scheduled', 'evaluating', 'pending'].includes(event?.status))
    .sort((a, b) => (a?.start || '').localeCompare(b?.start || ''));
  return {
    positions: Array.from(groups.values()).sort((a, b) => b.total - a.total),
    interviewers: Array.from(interviewers.entries()).map(([name, count]) => ({ name, count })).sort((a, b) => b.count - a.count),
    actionEvents,
    ...totals,
  };
}

function Card({ eyebrow, title, description, aside, children, className = '' }) {
  return <section className={`recruit-pro-card ${className}`}>
    <header className="recruit-pro-card-header"><div><div className="recruit-pro-eyebrow">{eyebrow}</div><h2>{title}</h2>{description && <p>{description}</p>}</div>{aside && <div className="recruit-pro-card-aside">{aside}</div>}</header>
    <div className="recruit-pro-card-content">{children}</div>
  </section>;
}

function EmptyState({ title, description }) {
  return <div className="recruit-pro-empty"><strong>{title}</strong><span>{description}</span></div>;
}

function DemandTooltip(props) {
  return <ChartTooltipContent {...props} valueFormatter={(value) => formatNumber(value)} />;
}

function PortfolioCard({ data, colors }) {
  const totalHc = data.reduce((sum, item) => sum + item.plannedHc, 0);
  const gapHc = data.reduce((sum, item) => sum + item.coverageGap, 0);
  return <Card aside={<span className="recruit-pro-card-caption">{data.length ? `${data.length} 个项目群` : '暂无数据'}</span>} className="recruit-pro-card--portfolio" description="以部门为项目群观察计划 HC、候选人覆盖和覆盖缺口，折线表示项目群整体覆盖率。" eyebrow="项目组合分析" title="招聘项目组合">
    {data.length ? <>
      <div className="recruit-pro-inline-metrics"><span><b>{formatNumber(totalHc)}</b><small>计划 HC</small></span><span><b>{formatNumber(gapHc)}</b><small>覆盖缺口</small></span><span><b>{formatPercent(totalHc ? ((totalHc - gapHc) / totalHc) * 100 : 0)}</b><small>整体覆盖率</small></span></div>
      <div className="recruit-pro-chart recruit-pro-chart--portfolio"><ComposedChart data={data} height={300}>
        <ComposedChart.Grid stroke={colors.grid} vertical={false} />
        <ComposedChart.XAxis axisLine={false} dataKey="dept" tick={{ fill: colors.muted, fontSize: 11 }} tickLine={false} tickMargin={10} />
        <ComposedChart.YAxis axisLine={false} tick={{ fill: colors.muted, fontSize: 11 }} tickLine={false} width={40} yAxisId="hc" />
        <ComposedChart.YAxis axisLine={false} orientation="right" tick={{ fill: colors.muted, fontSize: 11 }} tickFormatter={(value) => `${value}%`} tickLine={false} width={36} yAxisId="rate" />
        <Tooltip content={<DemandTooltip />} cursor={{ fill: '#f7f9fc' }} />
        <Legend align="left" iconSize={8} iconType="circle" wrapperStyle={{ color: '#66738a', fontSize: 11, paddingBottom: 8 }} />
        <ComposedChart.Bar dataKey="candidateCover" fill={colors.blue} name="候选人覆盖" radius={[4, 4, 0, 0]} stackId="coverage" yAxisId="hc" />
        <ComposedChart.Bar dataKey="coverageGap" fill="#e8edf5" name="覆盖缺口" radius={[4, 4, 0, 0]} stackId="coverage" yAxisId="hc" />
        <ComposedChart.Line dataKey="coverageRate" dot={{ fill: colors.amber, r: 3, strokeWidth: 0 }} name="覆盖率" stroke={colors.amber} strokeWidth={2.5} type="monotone" yAxisId="rate" />
      </ComposedChart></div>
    </> : <EmptyState title="暂无招聘项目组合" description="当前筛选范围没有返回招聘需求。" />}
  </Card>;
}

function DeliveryHealthCard({ data }) {
  const readyCount = data.filter((item) => item.deliveryState === '可交付').length;
  const gapCount = data.filter((item) => item.coverageGap > 0 && item.deliveryState !== '已关闭').length;
  const gapHc = data.reduce((sum, item) => sum + (item.deliveryState === '已关闭' ? 0 : item.coverageGap), 0);
  const approvalCount = data.filter((item) => item.deliveryState === '待审批').length;
  return <Card aside={<span className="recruit-pro-card-caption">{data.length ? `${data.length} 个岗位项目` : '暂无数据'}</span>} className="recruit-pro-card--breakdown" description="每一行对应一个真实招聘需求，直接判断岗位是否具备交付条件，以及还差多少候选人。" eyebrow="经营判断" title="岗位交付健康度">
    {data.length ? <>
      <div className="recruit-pro-decision-strip">
        <span className="is-good"><b>{formatNumber(readyCount)}</b><small>可交付岗位</small></span>
        <span className="is-warning"><b>{formatNumber(gapCount)}</b><small>存在覆盖缺口</small></span>
        <span className="is-danger"><b>{formatNumber(gapHc)}</b><small>待补候选人</small></span>
        <span className="is-neutral"><b>{formatNumber(approvalCount)}</b><small>待审批启动</small></span>
      </div>
      <div className="recruit-pro-delivery-list" role="region" aria-label="岗位交付健康度明细" tabIndex="0">
        {data.slice(0, 7).map((item) => <div className={`recruit-pro-delivery-row is-${item.deliveryState === '可交付' ? 'ready' : item.deliveryState === '已关闭' ? 'closed' : 'watch'}`} key={item.id || item.position}>
          <div className="recruit-pro-delivery-heading"><div><strong>{item.position}</strong><span>{item.dept} · 目标入职 {item.date || '待定'}</span></div><span className={`recruit-pro-delivery-state is-${item.deliveryState === '可交付' ? 'ready' : item.deliveryState === '已关闭' ? 'closed' : item.deliveryState === '待审批' ? 'neutral' : 'warning'}`}>{item.deliveryState}</span></div>
          <div className="recruit-pro-delivery-track"><i style={{ width: `${Math.min(item.coverageRate, 100)}%` }} /><em style={{ left: `${Math.min(item.coverageRate, 100)}%` }} /></div>
          <div className="recruit-pro-delivery-meta"><span>已覆盖 <b>{formatNumber(item.candidateCover)}</b> / 计划 <b>{formatNumber(item.hc)}</b></span><span className={item.coverageGap ? 'has-gap' : ''}>{item.coverageGap ? `还差 ${formatNumber(item.coverageGap)} 人` : item.deliveryStateLabel}</span><strong>{formatPercent(item.coverageRate)}</strong></div>
        </div>)}
      </div>
      {data.length > 7 && <div className="recruit-pro-list-more">还有 {formatNumber(data.length - 7)} 个岗位，请在下方岗位明细中查看</div>}
    </> : <EmptyState title="暂无岗位交付数据" description="需求数据返回后，这里会显示每个岗位的交付准备度。" />}
  </Card>;
}

function TrendCard({ data, colors }) {
  const totals = data.reduce((result, item) => ({ resumes: result.resumes + item.resumes, interviews: result.interviews + item.interviews, hires: result.hires + item.hires }), { resumes: 0, interviews: 0, hires: 0 });
  const peak = data.reduce((current, item) => item.resumes > current.resumes ? item : current, { month: '—', resumes: 0 });
  return <Card aside={<span className="recruit-pro-card-caption">{data.length ? `${data.length} 个月 · 数据聚合` : '暂无数据'}</span>} className="recruit-pro-card--trend" description="展示候选人进入、面试承接和入职落地的年度吞吐，辅助判断招聘资源投入是否形成交付。" eyebrow="年度吞吐" title="招聘交付趋势">
    {data.length ? <>
      <div className="recruit-pro-inline-metrics recruit-pro-inline-metrics--trend"><span><b>{formatNumber(totals.resumes)}</b><small>候选人进入</small></span><span><b>{formatNumber(totals.interviews)}</b><small>面试承接</small></span><span><b>{formatNumber(totals.hires)}</b><small>入职落地</small></span><span><b>{peak.month}</b><small>简历峰值月份</small></span></div>
      <div className="recruit-pro-chart recruit-pro-chart--trend"><AreaChart data={data} height={320}>
        <AreaChart.Grid stroke={colors.grid} vertical={false} />
        <AreaChart.XAxis axisLine={false} dataKey="month" tick={{ fill: colors.muted, fontSize: 11 }} tickLine={false} tickMargin={10} />
        <AreaChart.YAxis axisLine={false} tick={{ fill: colors.muted, fontSize: 11 }} tickLine={false} width={42} />
        <Tooltip content={<DemandTooltip />} />
        <Legend align="left" iconSize={8} iconType="circle" wrapperStyle={{ color: '#66738a', fontSize: 11, paddingBottom: 8 }} />
        <AreaChart.Area dataKey="resumes" fill={colors.blue} fillOpacity={0.12} name="候选人进入" stroke={colors.blue} strokeWidth={2.3} type="monotone" />
        <AreaChart.Area dataKey="interviews" fill={colors.amber} fillOpacity={0.08} name="面试承接" stroke={colors.amber} strokeWidth={2.3} type="monotone" />
        <AreaChart.Area dataKey="hires" fill={colors.green} fillOpacity={0.09} name="入职落地" stroke={colors.green} strokeWidth={2.3} type="monotone" />
      </AreaChart></div>
    </> : <EmptyState title="暂无年度吞吐数据" description="当前年份没有候选人、面试或入职记录。" />}
  </Card>;
}

function ResourceLoadCard({ data, colors }) {
  const insight = data.evaluating > 0
    ? `当前有 ${formatNumber(data.evaluating)} 场待评价，面试已发生但岗位决策尚未完成。`
    : data.scheduled > 0
      ? `当前有 ${formatNumber(data.scheduled)} 场已排期，资源正在承接岗位交付。`
      : '当前月份没有待处理的面试节点。';
  return <Card aside={<span className="recruit-pro-card-caption">{data.total ? `${data.total} 场 · ${data.interviewers.length} 位面试官` : '暂无数据'}</span>} className="recruit-pro-card--resource" description="按岗位查看面试是否承接到交付，以及待评价是否正在阻塞招聘决策。" eyebrow="交付承接" title="面试资源调度">
    {data.total ? <>
      <div className="recruit-pro-resource-kpis"><span className="is-blue"><b>{formatNumber(data.total)}</b><small>本月面试</small></span><span className="is-amber"><b>{formatNumber(data.scheduled)}</b><small>待面试</small></span><span className="is-red"><b>{formatNumber(data.evaluating)}</b><small>待评价</small></span><span className="is-green"><b>{formatNumber(data.completed)}</b><small>已完成</small></span></div>
      <div className="recruit-pro-resource-layout"><div className="recruit-pro-chart recruit-pro-chart--resource"><BarChart data={data.positions} height={Math.max(150, data.positions.length * 52)} layout="vertical">
        <BarChart.Grid stroke={colors.grid} horizontal={false} /><XAxis axisLine={false} tick={{ fill: colors.muted, fontSize: 10 }} tickLine={false} type="number" /><YAxis axisLine={false} dataKey="position" tick={{ fill: '#526077', fontSize: 10 }} tickLine={false} type="category" width={82} /><Tooltip content={<DemandTooltip />} cursor={{ fill: '#f7f9fc' }} /><Legend align="left" iconSize={8} iconType="circle" wrapperStyle={{ color: '#66738a', fontSize: 10, paddingBottom: 5 }} /><BarChart.Bar barSize={14} dataKey="scheduled" fill={colors.blue} name="待面试" radius={[0, 4, 4, 0]} stackId="load" /><BarChart.Bar barSize={14} dataKey="evaluating" fill={colors.amber} name="待评价" radius={[0, 4, 4, 0]} stackId="load" /><BarChart.Bar barSize={14} dataKey="completed" fill={colors.green} name="已完成" radius={[0, 4, 4, 0]} stackId="load" /></BarChart></div><div className="recruit-pro-resource-insight"><div className="recruit-pro-resource-conclusion"><span>管理结论</span><strong>{insight}</strong></div><div className="recruit-pro-resource-actions"><span>近期需要处理</span>{data.actionEvents.slice(0, 3).map((event) => <div key={event.id || `${event.start}-${event.title}`}><div><strong>{event.position || '岗位待定'}</strong><small>{event.title || event.name || '候选人待定'} · {event.interviewer || '面试官待分配'}</small></div><em className={`is-${event.status}`}>{event.statusLabel || event.status}</em></div>)}{!data.actionEvents.length && <small className="recruit-pro-no-actions">没有待处理面试节点</small>}</div></div></div>
      <div className="recruit-pro-interviewer-strip"><span>参与面试官</span>{data.interviewers.slice(0, 5).map((item) => <em key={item.name}>{item.name} <b>{item.count}</b></em>)}</div>
    </> : <EmptyState title="暂无面试资源排期" description="当前月份没有面试预约记录，无法判断资源承接情况。" />}
  </Card>;
}

function DemandTableCard({ demands }) {
  return <Card aside={<span className="recruit-pro-card-caption">{demands.length ? `${demands.length} 条记录` : '暂无数据'}</span>} className="recruit-pro-card--table" description="只展示当前筛选范围内的真实需求，点击岗位进入招聘需求模块继续处理。" eyebrow="岗位执行明细" title="当前招聘岗位">
    {demands.length ? <div className="recruit-pro-table-wrap"><table className="recruit-pro-table"><thead><tr><th>岗位 / 部门</th><th>计划 HC</th><th>候选人覆盖</th><th>覆盖率</th><th>紧急程度</th><th>目标入职</th><th>状态</th><th>动作</th></tr></thead><tbody>{demands.slice(0, 10).map((item, index) => <tr data-testid="recruit-project-row" key={`${item.id || item.position}-${index}`}><td><strong>{item.position}</strong><span>{item.dept}</span></td><td className="is-number">{formatNumber(item.hc)}</td><td className="is-number">{formatNumber(item.linkedCount)}</td><td className="is-number">{formatPercent(item.coverageRate)}</td><td><span className={`recruit-pro-tag is-${item.urgency}`}>{item.urgencyLabel}</span></td><td>{item.date || '—'}</td><td><span className={`recruit-pro-status is-${item.status}`}>{item.statusLabel}</span></td><td><a href="/recruit-demand">查看需求</a></td></tr>)}</tbody></table></div> : <EmptyState title="暂无岗位需求" description="调整筛选条件或等待需求数据同步。" />}
  </Card>;
}

export default function RecruitAnalyticsPro({ demands = [], events = [], months = [], calendarMonth = '', year = '', onCalendarMonthChange }) {
  const colors = chartColors();
  const demandData = useMemo(() => normalizeDemands(demands), [demands]);
  const monthData = useMemo(() => normalizeMonths(months), [months]);
  const portfolioData = useMemo(() => buildPortfolio(demandData), [demandData]);
  const deliveryData = useMemo(() => buildDeliveryRows(demandData), [demandData]);
  const resourceData = useMemo(() => buildResourceLoad(events), [events]);

  return <section className="recruit-pro-analytics" aria-label="招聘项目运营中心" data-testid="dashboard-pro-charts">
    <section className="recruit-pro-workspace-head"><div><div className="recruit-pro-workspace-label">RECRUITMENT OPERATIONS CENTER</div><h2>招聘项目运营中心</h2><p>从项目组合、交付阶段、年度吞吐和面试资源四个维度，观察招聘项目的真实执行状态。</p></div><div className="recruit-pro-workspace-context"><span>数据范围</span><strong>{year || '—'} 年招聘项目</strong><span>岗位需求 {formatNumber(demandData.length)} 条</span><span className="recruit-pro-live"><i />数据库实时接口</span></div></section>
    <div className="recruit-pro-grid recruit-pro-grid--top"><PortfolioCard data={portfolioData} colors={colors} /><DeliveryHealthCard data={deliveryData} /></div>
    <TrendCard data={monthData} colors={colors} />
    <div className="recruit-pro-grid recruit-pro-grid--bottom"><Card className="recruit-pro-card--agenda" description="面试安排来自 InterviewBook，切换月份会重新读取对应月份的数据库事件。" eyebrow="面试资源" title="招聘 Agenda" aside={<span className="recruit-pro-card-caption">月视图</span>}><HeroAgenda events={events} month={calendarMonth} onMonthChange={onCalendarMonthChange} /></Card><ResourceLoadCard data={resourceData} colors={colors} /></div>
    <DemandTableCard demands={demandData} />
  </section>;
}
