import { fetchKpi, fetchFunnel, fetchDeptProgress, fetchChannel, fetchRiskAlerts } from '../api/dashboard.js';
import { fetchDemands } from '../api/demand.js';
import { fetchTalent, fetchMailLog } from '../api/talent.js';
import { fetchInterviews, fetchInterviewAlerts, fetchInterviewCalendar } from '../api/interview.js';
import { fetchNotifyTemplates } from '../api/config.js';

let prefetchStarted = false;

function currentWeekStart() {
  const d = new Date();
  const day = d.getDay() || 7;
  d.setDate(d.getDate() - day + 1);
  return d.toISOString().slice(0, 10);
}

export function prefetchWorkbenchData() {
  if (navigator.webdriver) return;
  if (prefetchStarted || !localStorage.getItem('hr_token')) return;
  prefetchStarted = true;

  const run = () => {
    const tasks = [
      fetchKpi(),
      fetchFunnel(),
      fetchDeptProgress(),
      fetchChannel(),
      fetchRiskAlerts(),
      fetchDemands({ page: 1, pageSize: 50 }),
      fetchTalent({ page: 1, pageSize: 20, sort: 'default' }),
      fetchInterviews({ tab: 'all' }),
      fetchInterviewAlerts(),
      fetchInterviewCalendar({ week_start: currentWeekStart() }),
      fetchMailLog(50),
      fetchNotifyTemplates(),
    ];
    Promise.allSettled(tasks).catch(() => {});
  };

  if ('requestIdleCallback' in window) {
    window.requestIdleCallback(run, { timeout: 2500 });
  } else {
    window.setTimeout(run, 1500);
  }
}
