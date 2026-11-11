package dev.itsdropper.vanta.ui.components;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SidebarNavigationTest {

    @Test
    void everyPageHasUniqueExplicitShortcutAndRoundTrips() {
        Set<Integer> shortcuts = new HashSet<>();

        for (Sidebar.Page page : Sidebar.Page.values()) {
            assertFalse(page.title().isBlank());
            assertNotNull(page.iconType());
            if (page.shortcutNumber() > 0) {
                assertTrue(shortcuts.add(page.shortcutNumber()),
                        "Duplicate shortcut: " + page.shortcutNumber());
                assertSame(page, Sidebar.Page.fromShortcutNumber(page.shortcutNumber()));
            } else {
                assertNull(Sidebar.Page.fromShortcutNumber(page.shortcutNumber()));
            }
        }

        long shortcutPages = java.util.Arrays.stream(Sidebar.Page.values())
                .filter(page -> page.shortcutNumber() > 0)
                .count();
        assertEquals(shortcutPages, shortcuts.size());
    }

    @Test
    void unknownShortcutDoesNotResolveToAPage() {
        assertNull(Sidebar.Page.fromShortcutNumber(0));
        assertNull(Sidebar.Page.fromShortcutNumber(10));
        assertNull(Sidebar.Page.fromShortcutNumber(-1));
    }
}
