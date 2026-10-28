/** 站点的主题：内置主题或跟随系统明暗，偏好记在 localStorage；顶栏的主题切换改它 */
import { builtinThemes, createThemePreference } from '@zerodep-dev/vue-tsx-pro-ui';

export const preference = createThemePreference({ themes: builtinThemes });
