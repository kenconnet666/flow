/**
 * 流转记录：实例上发生过的事（发起、到达、认领、处理、转办、加签、留言、撤回……），新的在前。
 * 只列人看得懂的几种：节点的进出（node.entered、node.left）是引擎推进的细节，不列。
 *
 *   <FlowTimeline events={detail.timeline} />
 */
import type { FlowEvent } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Timeline, useFormat, type StatusTone, type TimelineItem } from '@zerodep-dev/vue-tsx-pro-ui';
import {
  AlarmClock,
  ArrowRightLeft,
  Bell,
  Check,
  CirclePlay,
  CircleStop,
  CornerDownLeft,
  Forward,
  Hand,
  Inbox,
  Mail,
  MessageSquare,
  Pause,
  Play,
  Redo2,
  Undo2,
  UserRoundCog,
  UserRoundMinus,
  UserRoundPlus,
  TriangleAlert,
  X,
  Zap,
} from '@lucide/icons';
import { useFlowMessages } from '../lang/messages';

/** FlowTimeline 的属性 */
export interface FlowTimelineProps {
  /** 实例的流转记录（实例详情的 timeline），按先后 */
  events: readonly FlowEvent[];
}

/** 每种事件的图标与色调；不在这里的不列 */
const LOOKS: Readonly<Record<string, { icon: TimelineItem['icon']; tone?: StatusTone }>> = {
  'instance.started': { icon: CirclePlay, tone: 'accent' },
  'workItem.created': { icon: Inbox },
  'workItem.claimed': { icon: Hand },
  'workItem.completed': { icon: Check, tone: 'success' },
  'workItem.canceled': { icon: X },
  'instance.withdrawn': { icon: Undo2, tone: 'warning' },
  'instance.terminated': { icon: CircleStop, tone: 'danger' },
  'instance.finished': { icon: CircleStop, tone: 'success' },
  'workItem.transferred': { icon: ArrowRightLeft },
  'workItem.delegated': { icon: Forward },
  'workItem.signersAdded': { icon: UserRoundPlus },
  'workItem.signersRemoved': { icon: UserRoundMinus },
  'workItem.resumed': { icon: CornerDownLeft },
  'workItem.released': { icon: Undo2 },
  'workItem.reassigned': { icon: UserRoundCog, tone: 'warning' },
  'workItem.reminded': { icon: Bell },
  'workItem.overdue': { icon: AlarmClock, tone: 'danger' },
  'workItem.unclaimed': { icon: Inbox, tone: 'warning' },
  'instance.commented': { icon: MessageSquare },
  'instance.cc': { icon: Mail },
  'signal.received': { icon: Zap },
  'signal.ignored': { icon: Zap },
  'node.overdue': { icon: AlarmClock, tone: 'warning' },
  'job.failed': { icon: TriangleAlert, tone: 'danger' },
  'instance.suspended': { icon: Pause, tone: 'warning' },
  'instance.resumed': { icon: Play },
  'instance.jumped': { icon: Redo2, tone: 'warning' },
};

/** 处理结果的名字：引擎在处理的记录上存了快照（data.outcomeLabel） */
const outcomeLabel = (event: FlowEvent) => {
  const label = event.data?.['outcomeLabel'];
  return typeof label === 'string' ? label : null;
};

/** 流转记录 */
export const FlowTimeline = _component(({ events }: FlowTimelineProps) => {
  const messages = useFlowMessages();
  const format = useFormat();
  return () => (
    <Timeline
      reverse
      items={events
        .filter((event) => event.type in LOOKS)
        .map(
          (event): TimelineItem => ({
            key: String(event.id),
            // 处理结果显示名字（引擎存的快照），没有时显示键
            title: messages.event(event.type, event.nodeName ?? event.node, outcomeLabel(event) ?? event.outcome),
            actor: event.actorName ?? messages.system,
            dateTime: event.at,
            time: format.date(event.at, { dateStyle: 'medium', timeStyle: 'short' }),
            // 交给了谁、加了谁（引擎存的名字快照），再接意见、说明
            description: [messages.eventDetail(event.type, event.data ?? {}), event.comment].filter(Boolean).join(' · ') || undefined,
            ...LOOKS[event.type],
          }),
        )}
    />
  );
});
