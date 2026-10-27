/**
 * 字段类型 → 默认的控件：自动排版的表单（auto-form）与明细表（form.List）的格子都按它挑。值的绑定、步长、选项、
 * 人员数据源由 form.Field 按字段补上（controls.ts），这里只给控件本身：
 *
 *   text      TextField；maxLength 超过 100 时 Textarea
 *   number    NumberField              decimal   NumberField（按 Decimal 读写）
 *   boolean   Switch                   date      DatePicker              datetime  DatePicker（到分钟）
 *   select    Select（多选时 multiple） people    PeoplePicker（容器给了人员数据源时；没给时只显示名字）
 *   file      只显示文件名（上传由接入方的表单代码做）
 */
import type { FieldDefinition } from '@zerodep-dev/flow-model';
import { DatePicker, NumberField, PeoplePicker, Select, Switch, Text, Textarea, TextField, type PeopleEntry, type PeopleSource } from '@zerodep-dev/vue-tsx-pro-ui';
import { h, type VNodeChild } from 'vue';

/** 一个字段的默认控件；value 读当前值（人员、文件只显示名字时用） */
export function defaultControl(field: FieldDefinition, people: PeopleSource | undefined, value: () => unknown): VNodeChild {
  switch (field.type) {
    case 'number':
    case 'decimal':
      return <NumberField />;
    case 'boolean':
      return <Switch />;
    case 'date':
    case 'datetime':
      return <DatePicker />;
    case 'select':
      // 选项由 form.Field 按清单补上（Select 的类型要求写 items）
      return h(Select);
    case 'people':
      return people ? <PeoplePicker source={people} /> : <Text>{((value() ?? []) as PeopleEntry[]).map((p) => p.name).join('、')}</Text>;
    case 'file':
      return <Text>{((value() ?? []) as { name: string }[]).map((f) => f.name).join('、')}</Text>;
    default:
      return (field.maxLength ?? 0) > 100 ? <Textarea /> : <TextField />;
  }
}
