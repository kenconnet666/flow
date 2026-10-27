/**
 * 表单设计器画布上的位置：画布照常渲染表单，设计时编译给每个节点的元素加了 data-form-node（路径），
 * 这里按它们在页面上的位置算选中框、指针下的节点、拖放时放在哪里与插入线。坐标都相对画布的左上角。
 */
import { findNode, parentOf, type DesignNode } from '../../form-tools';

/** 画布上的一个框 */
export interface Rect {
  readonly left: number;
  readonly top: number;
  readonly width: number;
  readonly height: number;
}

/** 放下的位置：容器与第几个子节点之前，加上画插入线的位置 */
export interface Drop {
  readonly parent: string;
  readonly index: number;
  readonly line: Rect;
}

/** 一个节点在画布上的框（代码块可能有几个元素，取并起来的）；没渲染出来时为 null */
export function rectOf(canvas: HTMLElement, path: string): Rect | null {
  const elements = [...canvas.querySelectorAll<HTMLElement>(`[data-form-node="${path}"]`)];
  const rects = elements.map((e) => e.getBoundingClientRect()).filter((r) => r.width > 0 || r.height > 0);
  if (rects.length === 0) return null;
  const origin = canvas.getBoundingClientRect();
  const left = Math.min(...rects.map((r) => r.left));
  const top = Math.min(...rects.map((r) => r.top));
  const right = Math.max(...rects.map((r) => r.right));
  const bottom = Math.max(...rects.map((r) => r.bottom));
  return { left: left - origin.left, top: top - origin.top, width: right - left, height: bottom - top };
}

/** 指针下面最里层的节点（跳过盖在上面的操作层）；不在任何节点上时为 null */
export function nodeAt(canvas: HTMLElement, x: number, y: number): string | null {
  for (const element of document.elementsFromPoint(x, y)) {
    if (!canvas.contains(element) || element.closest('[data-designer-overlay]')) continue;
    const hit = element.closest<HTMLElement>('[data-form-node]');
    if (hit && canvas.contains(hit)) return hit.dataset.formNode ?? null;
  }
  return null;
}

/** 指针在哪放下：容器里按子节点的位置排，别的节点放它前面或后面（按上下半） */
export function dropAt(canvas: HTMLElement, root: DesignNode, x: number, y: number): Drop | null {
  const py = y - canvas.getBoundingClientRect().top;
  const node = findNode(root, nodeAt(canvas, x, y) ?? root.path) ?? root;
  if (node.container) return dropInside(canvas, node, py);
  const at = parentOf(root, node.path);
  const rect = rectOf(canvas, node.path);
  if (!at || !rect) return dropInside(canvas, root, py);
  const after = py > rect.top + rect.height / 2;
  return { parent: at.parent.path, index: at.index + (after ? 1 : 0), line: lineAt(rect, after) };
}

/** 放进容器：落在哪个子节点的上半就在它前面，都不是就放最后；空的容器在底边画线 */
function dropInside(canvas: HTMLElement, container: DesignNode, py: number): Drop | null {
  const rects = container.children.map((c) => rectOf(canvas, c.path));
  const own = rectOf(canvas, container.path);
  if (!own) return null;
  const index = rects.findIndex((r) => r !== null && py < r.top + r.height / 2);
  if (index >= 0) return { parent: container.path, index, line: lineAt(rects[index]!, false) };
  const last = [...rects].reverse().find((r) => r !== null);
  return {
    parent: container.path,
    index: container.children.length,
    line: last ? lineAt(last, true) : { left: own.left + 8, top: own.top + own.height - 6, width: Math.max(own.width - 16, 24), height: 2 },
  };
}

/** 一个框的上边或下边外面一点的插入线 */
const lineAt = (rect: Rect, after: boolean): Rect => ({ left: rect.left, top: after ? rect.top + rect.height + 2 : rect.top - 3, width: rect.width, height: 2 });

/** 框写成绝对定位的样式 */
export const px = (rect: Rect) => ({ left: `${rect.left}px`, top: `${rect.top}px`, width: `${rect.width}px`, height: `${rect.height}px` });
