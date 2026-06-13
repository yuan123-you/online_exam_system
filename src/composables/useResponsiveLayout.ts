import { computed } from 'vue'
import { useDeviceType } from './useDeviceType'

export type TableMode = 'cards' | 'compact' | 'full'
export type FormLayout = 'vertical' | 'grid'
export type SidebarMode = 'drawer' | 'collapsed' | 'expanded'

export interface ResponsiveLayout {
  isMobile: ReturnType<typeof useDeviceType>['isMobile']
  isTablet: ReturnType<typeof useDeviceType>['isTablet']
  isDesktop: ReturnType<typeof useDeviceType>['isDesktop']
  tableMode: ReturnType<typeof computed<TableMode>>
  formLayout: ReturnType<typeof computed<FormLayout>>
  sidebarMode: ReturnType<typeof computed<SidebarMode>>
}

/**
 * Composable that derives semantic layout decisions from the current device.
 *
 * Usage:
 *   const { tableMode, formLayout, sidebarMode } = useResponsiveLayout()
 *   // tableMode  – 'cards' | 'compact' | 'full'
 *   // formLayout – 'vertical' | 'grid'
 *   // sidebarMode – 'drawer' | 'collapsed' | 'expanded'
 */
export function useResponsiveLayout(): ResponsiveLayout {
  const { isMobile, isTablet, isDesktop } = useDeviceType()

  const tableMode = computed<TableMode>(() => {
    if (isMobile.value) return 'cards'
    if (isTablet.value) return 'compact'
    return 'full'
  })

  const formLayout = computed<FormLayout>(() => {
    return isMobile.value ? 'vertical' : 'grid'
  })

  const sidebarMode = computed<SidebarMode>(() => {
    if (isMobile.value) return 'drawer'
    if (isTablet.value) return 'collapsed'
    return 'expanded'
  })

  return { isMobile, isTablet, isDesktop, tableMode, formLayout, sidebarMode }
}
