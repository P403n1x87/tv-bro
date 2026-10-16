package com.phlox.tvwebbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [NewTabPositionPolicy], the position a page-requested tab takes in the tab list.
 */
class NewTabPositionPolicyTest {

    @Test
    fun aTabRequestedByTheSelectedTabLandsRightAfterIt() {
        assertEquals(1, NewTabPositionPolicy.insertIndexAfter(requestingTabIndex = 0, tabCount = 3))
        assertEquals(3, NewTabPositionPolicy.insertIndexAfter(requestingTabIndex = 2, tabCount = 3))
    }

    @Test
    fun withNoSelectedTabTheNewTabIsAppended() {
        assertEquals(0, NewTabPositionPolicy.insertIndexAfter(requestingTabIndex = -1, tabCount = 0))
        assertEquals(4, NewTabPositionPolicy.insertIndexAfter(requestingTabIndex = -1, tabCount = 4))
    }
}
