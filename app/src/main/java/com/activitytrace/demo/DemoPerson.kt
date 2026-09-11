package com.activitytrace.demo

/**
 * Recurring people in the demo universe. No random name generation: the same
 * stable characters appear across apps and days, which is what makes search
 * (and the stories) interesting.
 */
enum class DemoPerson(val firstName: String) {
    ALEX("Alex"),
    MAYA("Maya"),
    JONAS("Jonas"),
    PRIYA("Priya"),
    SAM("Sam"),
}