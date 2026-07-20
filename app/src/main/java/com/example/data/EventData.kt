package com.example.data

import androidx.compose.runtime.mutableStateListOf

data class EventInfo(
    val name: String,
    val location: String,
    val date: String,
    val time: String,
    val description: String
)

data class Guest(
    val id: String,
    val name: String,
    val phone: String,
    val isVip: Boolean,
    val rsvpStatus: String, // "Attending", "Pending", "Declined"
    val dietary: String,
    var isCheckedIn: Boolean,
    val seat: String
)

data class FAQ(
    val question: String,
    val answer: String,
    val category: String
)

data class ScheduleItem(
    val id: Int,
    val title: String,
    val timeStr: String,
    val location: String,
    val description: String,
    val isCurrent: Boolean = false,
    val isNext: Boolean = false
)

data class LocationInfo(
    val name: String,
    val description: String,
    val level: String,
    val directions: String
)

data class OpenIssue(
    val id: String,
    val description: String,
    val priority: String, // "High", "Medium", "Low"
    val reportedTime: String,
    var isResolved: Boolean = false
)

data class Announcement(
    val id: String,
    val title: String,
    val content: String,
    val time: String
)

data class CallTemplate(
    val id: String,
    val title: String,
    val question: String,
    val options: List<String>
)

data class AutomatedCall(
    val id: String,
    val guestId: String,
    val guestName: String,
    val templateId: String,
    val title: String,
    val question: String,
    val options: List<String>,
    val status: String,
    val answer: String = "",
    val time: String = ""
)

data class GuestNote(
    val id: String,
    val guestName: String,
    val content: String,
    val timestamp: String
)

data class GuestWave(
    val id: String,
    val guestName: String,
    val timestamp: String
)

object InMemoryStore {
    const val DEMO_ATTENDEE_ID = "1"

    val event = EventInfo(
        name = "Aurora Foundation Gala",
        location = "Harbor House, San Francisco",
        date = "July 19, 2026",
        time = "6:00 PM - 11:00 PM PDT",
        description = "A spectacular evening of philanthropy, community, and fine dining overlooking the scenic Bay Bridge, raising critical resources for the Aurora Foundation's educational equity projects."
    )

    val guests = mutableStateListOf(
        Guest("1", "Maya Patel", "510-555-0199", isVip = true, rsvpStatus = "Attending", dietary = "None", isCheckedIn = true, seat = "Table 1"),
        Guest("2", "David Chen", "415-555-0123", isVip = true, rsvpStatus = "Attending", dietary = "Gluten-Free", isCheckedIn = false, seat = "Table 1"),
        Guest("3", "Sarah Jenkins", "650-555-0145", isVip = false, rsvpStatus = "Attending", dietary = "Vegan", isCheckedIn = false, seat = "Table 3"),
        Guest("4", "Marcus Thompson", "415-555-0188", isVip = true, rsvpStatus = "Pending", dietary = "None", isCheckedIn = false, seat = "Table 2"),
        Guest("5", "Elena Rostova", "510-555-0112", isVip = false, rsvpStatus = "Attending", dietary = "Nut Allergy", isCheckedIn = false, seat = "Table 4"),
        Guest("6", "James Wilson", "408-555-0155", isVip = false, rsvpStatus = "Declined", dietary = "None", isCheckedIn = false, seat = "N/A"),
        Guest("7", "Aisha Diop", "510-555-0177", isVip = true, rsvpStatus = "Attending", dietary = "Halal", isCheckedIn = false, seat = "Table 2"),
        Guest("8", "Carlos Mendez", "415-555-0133", isVip = false, rsvpStatus = "Attending", dietary = "None", isCheckedIn = false, seat = "Table 3"),
        Guest("9", "Chloe Dupont", "650-555-0166", isVip = false, rsvpStatus = "Pending", dietary = "None", isCheckedIn = false, seat = "Table 4"),
        Guest("10", "Liam O'Connor", "408-555-0144", isVip = true, rsvpStatus = "Attending", dietary = "Dairy-Free", isCheckedIn = false, seat = "Table 1")
    )

    val FAQs = listOf(
        FAQ("What time does the gala start?", "The reception begins at 6:00 PM PDT, followed by opening remarks at 7:00 PM and dinner at 7:30 PM.", "time"),
        FAQ("Where is Harbor House located?", "Harbor House is at Pier 26, San Francisco, right on the water under the Bay Bridge.", "venue"),
        FAQ("Is valet parking available?", "Yes, complimentary valet parking is offered at the main front entrance starting at 5:30 PM.", "parking"),
        FAQ("What is the dress code?", "The dress code is elegant formal or black-tie optional. Warm layers are recommended.", "dress code"),
        FAQ("Is the venue wheelchair accessible?", "Yes, Harbor House is fully ADA compliant with ramp entrances and an elevator.", "accessibility"),
        FAQ("Are dietary needs accommodated?", "Yes, dinner features gourmet options including vegan, gluten-free, halal, and nut-allergy safety.", "food")
    )

    val schedule = listOf(
        ScheduleItem(1, "Welcome Reception & Cocktails", "6:00 PM - 7:00 PM", "Bayview Terrace", "Mingle with fellow supporters and enjoy artisan cocktails on the waterfront terrace.", isCurrent = false),
        ScheduleItem(2, "Opening Remarks & Vision 2026", "7:00 PM - 7:30 PM", "Grand Ballroom", "A warm welcome from our director, followed by a keynote presentation of our global impact.", isCurrent = true),
        ScheduleItem(3, "Dinner & Live Charity Auction", "7:30 PM - 9:30 PM", "Grand Ballroom", "An exquisite three-course coastal dinner paired with our highly anticipated annual charity auction.", isNext = true),
        ScheduleItem(4, "Dessert & Jazz Performance", "9:30 PM - 11:00 PM", "Sunset Pier", "End the night under the stars with dessert, coffee, and live acoustic jazz music.", isCurrent = false),
        ScheduleItem(5, "Closing Toast & Departure", "11:00 PM - 11:15 PM", "Main Foyer", "Final words of appreciation and gift-bag collection at the reception lobby.", isCurrent = false)
    )

    val locations = listOf(
        LocationInfo("Bayview Terrace", "Stunning open-air seaside terrace", "Level 1", "To the left of the main entrance foyer"),
        LocationInfo("Grand Ballroom", "Elegant main hall with high ceilings", "Level 1", "Straight through the foyer archway"),
        LocationInfo("Sunset Pier", "Over-water wooden deck under the Bridge", "Level 1", "Exiting through the rear Ballroom glass doors"),
        LocationInfo("Aurora VIP Lounge", "Private premium guest parlor", "Level 2", "Up the grand foyer staircase on the right"),
        LocationInfo("Main Foyer & Reception", "Entry foyer and registration desk", "Level 1", "Main front double doors"),
        LocationInfo("Valet Parking Deck", "Valet vehicle queue and loading area", "Level 1", "Directly in front of the venue entrance")
    )

    val issues = mutableStateListOf(
        OpenIssue("I101", "Table 4 is missing a chair for guest Elena Rostova", "High", "12:15 PM"),
        OpenIssue("I102", "Valet queue backup at the main east entrance", "Medium", "12:30 PM")
    )

    val announcements = mutableStateListOf(
        Announcement("A1", "Live Auction starting in 15 minutes!", "Prepare your bids for the private Napa Valley wine excursion.", "7:15 PM"),
        Announcement("A2", "Souvenir brochures available", "Don't forget to grab your commemorative gala handbook at the front desk.", "6:05 PM")
    )

    val callTemplates = listOf(
        CallTemplate(
            "food",
            "Food preference",
            "Which dinner would you prefer?",
            listOf("Regular", "Vegetarian", "Vegan", "Gluten-free")
        ),
        CallTemplate(
            "rsvp",
            "RSVP confirmation",
            "Are you still planning to attend the Aurora Foundation Gala?",
            listOf("Attending", "Not attending", "Not sure")
        ),
        CallTemplate(
            "arrival",
            "Arrival status",
            "What is your current arrival status?",
            listOf("Already here", "Under 15 minutes", "15–30 minutes", "Running late")
        )
    )

    val guestNotes = mutableStateListOf<GuestNote>()
    val guestWaves = mutableStateListOf<GuestWave>()

    fun getDeterministicAnswer(query: String): String {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return "Please type a question and I will assist you."

        return when {
            q.contains("time") || q.contains("when") || q.contains("clock") || q.contains("hours") || q.contains("duration") || q.contains("start") -> {
                "The Aurora Foundation Gala is held tonight, starting with the Welcome Reception at 6:00 PM PDT, followed by dinner and auction at 7:30 PM PDT, and ending at 11:00 PM PDT."
            }
            q.contains("venue") || q.contains("place") || q.contains("house") || q.contains("harbor") || q.contains("address") || q.contains("where is") || q.contains("san francisco") || q.contains("sf") -> {
                "The gala is held at the historic Harbor House, located at Pier 26 on the San Francisco waterfront, offering panoramic views of the Bay Bridge."
            }
            q.contains("park") || q.contains("parking") || q.contains("valet") || q.contains("car") || q.contains("garage") || q.contains("vehicle") -> {
                "Complimentary valet parking is available at the main entrance of Harbor House on Pier 26. Rideshare drop-off zone is also clearly marked directly in front."
            }
            q.contains("dress") || q.contains("clothes") || q.contains("wear") || q.contains("attire") || q.contains("suit") || q.contains("tuxedo") || q.contains("formal") || q.contains("dresscode") || q.contains("dress-code") -> {
                "The dress code for the gala is Elegant Formal or Black Tie optional. Guests are encouraged to wear warm layers as the Bay breeze can be cool in the evening."
            }
            q.contains("access") || q.contains("wheelchair") || q.contains("elevator") || q.contains("ramp") || q.contains("accessible") || q.contains("disability") || q.contains("disabled") -> {
                "Harbor House is fully ADA compliant with ramps at all entrances, accessible restrooms on all floors, and an elevator near the grand staircase."
            }
            q.contains("food") || q.contains("drink") || q.contains("eat") || q.contains("beverage") || q.contains("dietary") || q.contains("vegan") || q.contains("menu") || q.contains("gluten") || q.contains("dinner") || q.contains("appetizer") -> {
                "Dinner features a gourmet coastal-Californian menu with vegan and gluten-free options. Main courses include pan-seared sea bass, grass-fed ribeye, or roasted butternut squash steak. Please note any dietary requirements on check-in."
            }
            q.contains("schedule") || q.contains("program") || q.contains("agenda") || q.contains("timeline") || q.contains("what's on") || q.contains("now") || q.contains("next") -> {
                "The evening includes: 6:00 PM Welcome Reception, 7:00 PM Opening Remarks, 7:30 PM Dinner & Live Auction, 9:30 PM Dessert & Live Music by the Bay, and 11:00 PM Gala Concludes."
            }
            q.contains("locations") || q.contains("rooms") || q.contains("restrooms") || q.contains("restroom") || q.contains("map") || q.contains("ballroom") || q.contains("terrace") || q.contains("pier") || q.contains("table") -> {
                "Key event spaces include the Grand Ballroom (Dinner), Bayview Terrace (Reception), Sunset Pier (Live Music), and the Aurora VIP Lounge on Level 2."
            }
            q.contains("check-in") || q.contains("checkin") || q.contains("check in") || q.contains("ticket") || q.contains("register") || q.contains("entry") || q.contains("badge") || q.contains("rsvp") -> {
                "Check-in is located in the Main Foyer. Please have your name or phone number ready. Self-check-in can be completed under the Check In tab in this app."
            }
            q.contains("help") || q.contains("staff") || q.contains("emergency") || q.contains("contact") || q.contains("question") || q.contains("request") || q.contains("support") || q.contains("organizer") || q.contains("coordinator") || q.contains("concierge") -> {
                "Our on-site event coordinators and concierges are wearing Amber badges. You can also request assistance directly through the Host panel or get support at the front desk."
            }
            else -> "UNKNOWN"
        }
    }
}
