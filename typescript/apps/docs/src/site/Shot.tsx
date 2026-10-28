/**
 * 配图：截图铺满正文宽度，点开看大图；下面一行说明。图在 public/shots（截图脚本 shots/run.ts 生成）。
 *
 *   <Shot src="build/expense/03-condition.png" caption="选中连线，在右边写条件：金额 大于等于 5000" />
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import * as css from '@zerodep-dev/vue-tsx-pro-tsx/styles';
import { Image, Small } from '@zerodep-dev/vue-tsx-pro-ui';

const styles = css.create({
  figure: { display: 'flex', flexDirection: 'column', gap: '_xs', margin: 0 },
  frame: { borderWidth: '_xs', borderStyle: 'solid', borderColor: '_border', borderRadius: '_md', overflow: 'hidden' },
  caption: { textAlign: 'center' },
});

/** Shot 的属性 */
export interface ShotProps {
  /** public/shots 下的路径 */
  src: string;
  /** 说明：图下一行，也是读屏念的替代文字 */
  caption: string;
}

/** 一张截图 */
export const Shot = _component(({ src, caption }: ShotProps) => () => (
  <figure {...css.attrs(styles.figure)}>
    <div {...css.attrs(styles.frame)}>
      <Image src={`${import.meta.env.BASE_URL}shots/${src}`} alt={caption} width="100%" preview={{ caption }} />
    </div>
    <figcaption {...css.attrs(styles.caption)}>
      <Small tone="muted">{caption}</Small>
    </figcaption>
  </figure>
));
