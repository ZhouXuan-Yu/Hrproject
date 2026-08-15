import React, { useEffect, useMemo, useState } from 'react';

// Adapted from the local HeroUI Pro Agenda event model and month layout:
// HeroUIPro/herouipro-v3/src/components/agenda/{agenda.tsx,use-agenda.ts}.
const WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日'];

function monthKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

function dateKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function parseMonth(value) {
  const [year, month] = String(value || '').split('-').map(Number);
  return Number.isFinite(year) && Number.isFinite(month) ? new Date(year, month - 1, 1) : new Date();
}

function eventDate(event) {
  return String(event?.start || '').slice(0, 10);
}

function eventTime(event) {
  return String(event?.start || '').slice(11, 16) || '待定';
}

function eventTone(event) {
  if (event?.status === 'done' || event?.status === 'offer' || event?.status === 'onboard') return 'is-complete';
  if (event?.status === 'evaluating') return 'is-watch';
  return 'is-active';
}

export default function HeroAgenda({ events = [], month, onMonthChange, onSelectEvent }) {
  const [viewDate, setViewDate] = useState(() => parseMonth(month));
  const [selectedId, setSelectedId] = useState(null);

  useEffect(() => {
    if (month && monthKey(viewDate) !== month) setViewDate(parseMonth(month));
  }, [month]);

  const days = useMemo(() => {
    const year = viewDate.getFullYear();
    const currentMonth = viewDate.getMonth();
    const firstDay = (new Date(year, currentMonth, 1).getDay() + 6) % 7;
    const count = new Date(year, currentMonth + 1, 0).getDate();
    const previousCount = new Date(year, currentMonth, 0).getDate();
    return Array.from({ length: Math.ceil((firstDay + count) / 7) * 7 }, (_, index) => {
      const dayNumber = index - firstDay + 1;
      if (dayNumber < 1) {
        const date = new Date(year, currentMonth - 1, previousCount + dayNumber);
        return { date, key: dateKey(date), current: false };
      }
      if (dayNumber > count) {
        const date = new Date(year, currentMonth, dayNumber);
        return { date, key: dateKey(date), current: false };
      }
      const date = new Date(year, currentMonth, dayNumber);
      return { date, key: dateKey(date), current: true };
    });
  }, [viewDate]);

  const eventsByDay = useMemo(() => events.reduce((result, event) => {
    const key = eventDate(event);
    if (!key) return result;
    result[key] = [...(result[key] || []), event];
    return result;
  }, {}), [events]);

  const selectedEvent = events.find((event) => event.id === selectedId);
  const today = dateKey(new Date());
  const heading = viewDate.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long' });

  function moveMonth(offset) {
    const next = new Date(viewDate.getFullYear(), viewDate.getMonth() + offset, 1);
    setViewDate(next);
    onMonthChange?.(monthKey(next));
  }

  function selectEvent(event) {
    setSelectedId(event.id);
    onSelectEvent?.(event);
  }

  return (
    <div className="recruit-agenda" data-testid="recruit-agenda">
      <div className="recruit-agenda-toolbar">
        <div><strong>{heading}</strong><span>{events.length ? `${events.length} 场面试安排` : '本月暂无面试安排'}</span></div>
        <div className="recruit-agenda-actions">
          <button type="button" aria-label="上个月" onClick={() => moveMonth(-1)}>‹</button>
          <button type="button" className="recruit-agenda-today" onClick={() => { const now = new Date(); setViewDate(new Date(now.getFullYear(), now.getMonth(), 1)); onMonthChange?.(monthKey(now)); }}>今天</button>
          <button type="button" aria-label="下个月" onClick={() => moveMonth(1)}>›</button>
        </div>
      </div>
      <div className="recruit-agenda-weekdays">{WEEKDAYS.map((day) => <span key={day}>{day}</span>)}</div>
      <div className="recruit-agenda-grid">
        {days.map((day) => (
          <div className={`recruit-agenda-day ${day.current ? '' : 'is-outside'} ${day.key === today ? 'is-today' : ''}`} key={day.key}>
            <span className="recruit-agenda-day-number">{day.date.getDate()}</span>
            <div className="recruit-agenda-events">
              {(eventsByDay[day.key] || []).slice(0, 3).map((event) => (
                <button className={`recruit-agenda-event ${eventTone(event)} ${selectedId === event.id ? 'is-selected' : ''}`} key={event.id} type="button" title={`${eventTime(event)} ${event.title || '候选人待定'}`} onClick={() => selectEvent(event)}>
                  <span>{eventTime(event)}</span>{event.title || '候选人待定'}
                </button>
              ))}
              {(eventsByDay[day.key] || []).length > 3 && <span className="recruit-agenda-more">+{eventsByDay[day.key].length - 3} 场</span>}
            </div>
          </div>
        ))}
      </div>
      {selectedEvent && (
        <div className="recruit-agenda-detail" data-testid="recruit-agenda-detail">
          <div><span className="recruit-pro-eyebrow">SELECTED INTERVIEW</span><strong>{selectedEvent.title || '候选人待定'}</strong><span>{selectedEvent.position || '岗位待定'} · {eventTime(selectedEvent)} · {selectedEvent.round || '面试'}</span></div>
          <div className="recruit-agenda-detail-meta"><span>{selectedEvent.interviewer || '面试官待定'}</span><em>{selectedEvent.statusLabel || '待面试'}</em></div>
        </div>
      )}
    </div>
  );
}
