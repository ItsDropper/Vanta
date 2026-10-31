package dev.itsdropper.vanta.ui.components;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SidebarPageTest {

    @Test
    void everyNavigationPageHasUniqueShortcutAndUsefulMetadata() {
        Sidebar.Page[] pages = Sidebar.Page.values();
        Set<Integer> shortcuts = Arrays.stream(pages)
                .map(Sidebar.Page::shortcutNumber)
                .collect(Collectors.toSet());

        assertEquals(pages.length, shortcuts.size());
        for (Sidebar.Page page : pages) {
            assertFalse(page.title().isBlank());
            assertFalse(page.description().isBlank());
            assertSame(page, Sidebar.Page.fromShortcutNumber(page.shortcutNumber()));
        }
    }

    @Test
    void unknownShortcutDoesNotResolveToANavigationPage() {
        assertNull(Sidebar.Page.fromShortcutNumber(0));
        assertNull(Sidebar.Page.fromShortcutNumber(10));
    }
}
