/**
 * 工作日历（管理端，0002 §7）：时限写成工作日时按它算。每份日历有名字（流程定义的 settings.calendar 写它，不写用 default）、
 * 时区、一周哪几天上班，加上节假日（不上班）与调休（补班要上班）。一份都没建时按周一到周五算。
 *
 *   <FlowCalendars />
 */
import { CalendarDate } from '@internationalized/date';
import { Plus, Trash2 } from '@lucide/icons';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  DatePicker,
  Field,
  Flex,
  IconButton,
  Select,
  Switch,
  Text,
  TextField,
  useErrorMessage,
  useRequest,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { useFlowClient } from '../client/flow-client';
import type { WorkCalendar, WorkCalendarDay } from '../client/types';
import { useFlowMessages } from '../lang/messages';

/** 编辑中的一个特殊日子 */
interface DayRow {
  key: number;
  day: CalendarDate | null;
  workday: boolean;
  note: string;
}

/** 工作日历的维护 */
export const FlowCalendars = _component(() => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const errorText = useErrorMessage();
  const calendars = useRequest((_: null, signal) => flow.calendars(signal), { params: () => null });
  let name = _state('default');
  let zone = _state(Intl.DateTimeFormat().resolvedOptions().timeZone);
  let workweek = _state<number[]>([1, 2, 3, 4, 5]);
  let days = _state<DayRow[]>([]);
  let failure = _state<string | null>(null);
  let notice = _state<string | null>(null);
  let busy = _state(false);
  let nextKey = 0;

  /** 换成某一份日历来改 */
  function edit(calendar: WorkCalendar) {
    name = calendar.name;
    zone = calendar.zone;
    workweek = calendar.workweek.split(',').filter(Boolean).map(Number);
    days = calendar.days.map((d: WorkCalendarDay) => ({ key: nextKey++, day: d.day, workday: d.workday, note: d.note ?? '' }));
  }

  async function save() {
    busy = true;
    failure = null;
    notice = null;
    try {
      const saved = await flow.saveCalendar({
        name: name.trim(),
        zone: zone.trim(),
        workweek: [...workweek].sort().join(','),
        days: days.filter((d) => d.day).map((d) => ({ day: d.day!, workday: d.workday, note: d.note.trim() || null })),
      });
      edit(saved);
      notice = messages.calendarSaved;
      await calendars.refresh();
    } catch (error) {
      failure = errorText(error);
    } finally {
      busy = false;
    }
  }

  async function remove() {
    busy = true;
    failure = null;
    try {
      await flow.deleteCalendar(name.trim());
      await calendars.refresh();
      days = [];
    } catch (error) {
      failure = errorText(error);
    } finally {
      busy = false;
    }
  }

  return () => (
    <Card header={messages.calendars}>
      <Flex direction="column" gap="md">
        <Text tone="muted">{messages.calendarsHint}</Text>
        {failure && <Alert tone="danger" onClose={() => (failure = null)}>{failure}</Alert>}
        {notice && <Alert tone="success" onClose={() => (notice = null)}>{notice}</Alert>}
        {(calendars.data?.length ?? 0) > 0 && (
          <Field label={messages.calendarPick}>
            <Select
              items={(calendars.data ?? []).map((c) => ({ value: c.name, label: c.name, description: c.zone }))}
              _value={null}
              onUpdate:_value={(picked: string | null) => {
                const found = calendars.data?.find((c) => c.name === picked);
                if (found) edit(found);
              }}
            />
          </Field>
        )}
        <Flex gap="md" wrap>
          <Field label={messages.calendarName} required>
            <TextField _value={name} />
          </Field>
          <Field label={messages.calendarZone} required>
            <TextField _value={zone} />
          </Field>
        </Flex>
        <Field label={messages.calendarWorkweek}>
          <Flex gap="md" wrap>
            {messages.weekdays.map((label, i) => (
              <Checkbox
                key={i}
                _checked={workweek.includes(i + 1)}
                onUpdate:_checked={(on: boolean) => (workweek = on ? [...workweek, i + 1] : workweek.filter((d) => d !== i + 1))}
              >
                {label}
              </Checkbox>
            ))}
          </Flex>
        </Field>
        <Text weight="medium">{messages.calendarDays}</Text>
        {days.map((row) => (
          <Flex key={row.key} gap="sm" align="end" wrap>
            <Field label={messages.calendarDay}>
              <DatePicker valueType="dateValue" _value={row.day} onUpdate:_value={(value: CalendarDate | null) => (row.day = value)} />
            </Field>
            <Switch _checked={row.workday} onUpdate:_checked={(on: boolean) => (row.workday = on)}>
              {messages.calendarWorkday}
            </Switch>
            <Field label={messages.calendarNote}>
              <TextField _value={row.note} onUpdate:_value={(text: string) => (row.note = text)} />
            </Field>
            <IconButton icon={Trash2} variant="ghost" aria-label={messages.remove} onClick={() => (days = days.filter((d) => d !== row))} />
          </Flex>
        ))}
        <Flex gap="sm">
          <Button variant="outline" icon={Plus} onClick={() => (days = [...days, { key: nextKey++, day: null, workday: false, note: '' }])}>
            {messages.calendarAddDay}
          </Button>
        </Flex>
        <Flex gap="sm">
          <Button loading={busy} disabled={!name.trim() || !zone.trim()} onClick={save}>
            {messages.save}
          </Button>
          <Button variant="outline" tone="danger" disabled={busy || !calendars.data?.some((c) => c.name === name.trim())} onClick={remove}>
            {messages.remove}
          </Button>
        </Flex>
      </Flex>
    </Card>
  );
});
