package com.packetloss.samjho.scan

/** Text that is on nearly every prescription and is never a medicine. Shared by the matcher and repair tests. */
object ScanFixtures {

    val EVERYDAY_WORDS = listOf(
        "doctor", "please", "practice", "patient", "hospital", "clinic", "signature", "morning", "evening",
        "before", "after", "daily", "twice", "tablet", "capsule", "syrup", "drops", "injection", "cream",
        "follow", "review", "weeks", "advice", "diagnosis", "prescription", "medicine", "registration",
        "address", "phone", "mobile", "gender", "weight", "female", "father", "husband", "temperature",
        "fever", "cough", "cold", "headache", "stomach", "breakfast", "dinner", "lunch", "water", "night",
        "Kumar", "Sharma", "Ramesh", "Sunita", "Anil", "Priya", "Mumbai", "Delhi", "Pune", "Nagpur",
        "City Clinic", "Shivaji Nagar", "Patient Name", "Blood Pressure", "Follow up", "Next visit",
    )

    val HEADER_AND_FOOTER = listOf(
        "Dr. Anil Kumar MBBS, MD (Medicine)  Reg. No. 12345",
        "City Clinic, Shivaji Nagar, Pune - 411005   Phone 020 5555 0000",
        "Patient Name: Ramesh Patil   Age: 45   Sex: M   Date: 12/03/2026",
        "Take twice daily after food for five days. Review after one week.",
        "Please come back if the fever does not settle.",
        "Signature                                        Stamp",
    )
}
