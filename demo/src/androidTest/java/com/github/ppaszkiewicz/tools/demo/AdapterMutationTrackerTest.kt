package com.github.ppaszkiewicz.tools.demo

import androidx.test.runner.AndroidJUnit4
import com.github.ppaszkiewicz.tools.toolbox.recyclerView.AdapterMutationTracker
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdapterMutationTrackerTest {

    @Test
    fun testMutationTracking(){
        val tracker = AdapterMutationTracker()

        tracker.onItemRangeRemoved(1, 5)
        assert(tracker.getPrepositionFor(1) == 6)
        assert(tracker.getPrepositionFor(0) == 0)
        tracker.clear()

        tracker.onItemRangeInserted(1, 5)
        assert(tracker.getPrepositionFor(0) == 0)
        assert(tracker.getPrepositionFor(6) == 1)
        tracker.clear()

        tracker.onItemRangeMoved(1, 5, 2)
        assert(tracker.getPrepositionFor(0) == 0)
        assert(tracker.getPrepositionFor(5) == 1)
        assert(tracker.getPrepositionFor(6) == 2)
        assert(tracker.getPrepositionFor(7) == 7)
        assert(tracker.getPrepositionFor(2) == 4)
        tracker.clear()

        tracker.onItemRangeMoved(5, 1, 2)
        assert(tracker.getPrepositionFor(0) == 0)
        assert(tracker.getPrepositionFor(7) == 7)
        assert(tracker.getPrepositionFor(4) == 2)
        assert(tracker.getPrepositionFor(1) == 5)
        assert(tracker.getPrepositionFor(2) == 6)
        tracker.clear()
    }
}