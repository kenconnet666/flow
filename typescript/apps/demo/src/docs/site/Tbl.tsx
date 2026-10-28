/**
 * 说明文字里的表格：给表头与一行行的单元格（组件库的静态 Table）。第一列是这一行说的是什么（名词、操作名），至少留五个字宽，
 * 在演示旁窄的文档面板里也不会被挤成一字一行。
 *
 *   <Tbl head={['节点', '用途']} rows={[['人工审批', '有人处理'], ['条件分支', '按条件走一条']]} />
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import * as css from '@zerodep-dev/vue-tsx-pro-tsx/styles';
import { Table, TableBody, TableCell, TableHead, TableHeaderCell, TableRow } from '@zerodep-dev/vue-tsx-pro-ui';
import type { VNodeChild } from 'vue';

// 不用 nowrap：有的第一列是一整句（常见问题的现象），要能折行
const styles = css.create({ label: { minWidth: '5em' } });

/** Tbl 的属性 */
export interface TblProps {
  head: readonly string[];
  rows: readonly (readonly VNodeChild[])[];
}

/** 一张表 */
export const Tbl = _component(({ head, rows }: TblProps) => () => (
  <Table aria-label={head.join('、')}>
    <TableHead>
      <TableRow>
        {head.map((title) => (
          <TableHeaderCell key={title}>{title}</TableHeaderCell>
        ))}
      </TableRow>
    </TableHead>
    <TableBody>
      {rows.map((row, i) => (
        <TableRow key={i}>
          {row.map((cell, j) => (
            <TableCell key={j} sx={j === 0 ? styles.label : undefined}>
              {cell}
            </TableCell>
          ))}
        </TableRow>
      ))}
    </TableBody>
  </Table>
));
