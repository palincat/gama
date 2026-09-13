package com.popovicialinc.gama

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Basic lifecycle guard: the main screen must launch on every supported API. */
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun mainActivityLaunchesAndReachesResumedState() {
        activityRule.scenario.onActivity { activity ->
            check(!activity.isFinishing)
            check(!activity.isDestroyed)
        }
    }
}
