package com.kuriousdragon.smartindex.tools.seeder.gen

/**
 * Names, SMS templates, merchants, folders and file-name patterns (LLD §4.3). Stable data: changing any list
 * changes the generated data and therefore bumps [GENERATOR_VERSION].
 *
 * Rules the tests assert: the forbidden substrings of [FORBIDDEN] never occur here; templates of the "the" pools
 * contain `the`, the others never do (also after slot filling); `payment` only in the payment pools.
 */
object Vocabulary {
    /** Case-insensitive substrings that appear only in planted items (LLD §4.3). */
    val FORBIDDEN = listOf("qz", "kumquat", "booking reference", "sea view", "seaview", "sea_view")

    val FIRST_NAMES = listOf(
        "Aarav", "Vivaan", "Aditya", "Arjun", "Kabir", "Rohan", "Karan", "Nikhil", "Siddharth", "Amit",
        "Sanjay", "Vikram", "Manish", "Deepak", "Ankit", "Pooja", "Anjali", "Kavita", "Neha", "Divya",
        "Shreya", "Isha", "Nisha", "Ritu", "Swati", "Tanvi", "Aditi", "Pallavi", "Rekha", "Sunita",
        "Jaya", "Mohan", "Suresh", "Ganesh", "Harish", "Lalit", "Naveen", "Omkar", "Pranav", "Yash",
    )
    val LAST_NAMES = listOf(
        "Sharma", "Patil", "Deshmukh", "Kulkarni", "Joshi", "Iyer", "Nair", "Gupta", "Verma", "Reddy",
        "Mehta", "Shah", "Jain", "Bhatt", "Kapoor", "Singh", "Pawar", "More", "Gaikwad", "Chavan",
        "Kale", "Thakur", "Menon", "Rao", "Das", "Bose",
    )
    val DEVA_FIRST_NAMES = listOf(
        "अमित", "सुरेश", "गणेश", "अनिता", "सुनीता", "विजय", "संजय", "मंगेश", "सचिन", "पूजा",
        "कविता", "नेहा", "रेखा", "दीपक", "अजय", "किरण", "वैशाली", "सायली",
    )
    val DEVA_LAST_NAMES = listOf(
        "शर्मा", "पाटील", "देशमुख", "कुलकर्णी", "जोशी", "पवार", "गायकवाड", "चव्हाण", "शिंदे", "भोसले", "यादव", "मिश्रा",
    )

    val MERCHANTS = listOf(
        "SidxKart", "QuickMart", "FreshBasket", "UrbanCab", "MegaMobile", "GreenLeaf Pharmacy", "BlueBird Air",
        "SpiceRoute Foods", "TrueBill", "SidxPay",
    )
    val PLACES =
        listOf("City Mall", "Central Park", "Station Road", "Lake Road", "Market Square", "Airport", "Old Town")
    val ALPHA_SENDERS = listOf("VM-SIDXBK", "AX-SIDXPAY", "JD-SIDXKART", "VK-SIDXTEL", "BZ-SIDXMART")
    val MMS_SUBJECTS = listOf("Photos from the trip", "Weekend plan", "Look at this", "Family photo", "Birthday")
    val DEVA_MMS_SUBJECTS = listOf("सहल", "कुटुंब", "वाढदिवस")

    /** A body template; [service] templates come from an alphanumeric sender when received. */
    class Template(val text: String, val service: Boolean)

    private fun p(text: String) = Template(text, false)

    private fun s(text: String) = Template(text, true)

    // [payment][the] pools. Slots: {name} {n} {amt} {ref} {otp} {merchant} {place} {time} {date} {n4}
    private val plainNoThe = listOf(
        p("Call me when you are free"),
        p("Reached home safely"),
        p("Dinner at {time}? I reserved a table"),
        p("Happy birthday {name}! Have a great year"),
        p("Running late, will reach in {n} minutes"),
        p("Did you get my message about Sunday"),
        p("Meeting moved to {time} tomorrow"),
        p("Can you pick up milk on your way home"),
        p("ok thanks"),
        p("Good morning! Let us meet near {place} at {time}"),
        s("{otp} is your one time code for {merchant}. Do not share it with anyone."),
        s("Your order {ref} from {merchant} is out for delivery today."),
        s("Your package {ref} was delivered. Rate your experience on {merchant}."),
        s("Dear customer, your {merchant} recharge of Rs {amt} was successful."),
        s("Flight {ref} is on time. Boarding at {time}."),
        s("Your appointment at {place} is confirmed for {time}."),
    )
    private val plainThe = listOf(
        p("Did you see the match last night"),
        p("I will send the photos tonight"),
        p("Please call the plumber about the leak"),
        p("Lunch at the usual place at {time}?"),
        p("Do not forget the tickets for Saturday"),
        p("I left the keys with {name}"),
        p("Can you check the mail"),
        s("Use {otp} to log in. Do not share the code with anyone."),
        s("Your order {ref} will reach the address by {time}."),
        s("The delivery partner for order {ref} is on the way."),
        s("Reminder: the sale at {merchant} ends tonight."),
        s("Your bill of Rs {amt} is due on the 5th. Thanks, {merchant}."),
    )
    private val payNoThe = listOf(
        p("Payment done, check your account"),
        p("Sent you Rs {amt}. Payment ref {ref}"),
        p("Please confirm payment of Rs {amt} when you can"),
        s("Payment of Rs {amt} received for order {ref}. Thank you for shopping with {merchant}."),
        s("Your payment of Rs {amt} to {merchant} was successful. Ref {ref}."),
        s("Payment failed for order {ref}. Please retry with a different card."),
        s("Payment reminder: Rs {amt} due on {date}. Pay now to avoid a late fee."),
    )
    private val payThe = listOf(
        p("I made the payment this morning, please check"),
        p("Did the payment go through? I see nothing yet"),
        p("Will do the payment tonight after work"),
        p("Waiting for the payment confirmation"),
        s("The payment of Rs {amt} to {merchant} was successful. Ref {ref}."),
        s("Your payment for the order {ref} is pending. Complete it in the app."),
        s("Payment of Rs {amt} was debited from the account ending {n4}."),
    )

    private val devaTemplates = listOf(
        p("कल मिलते हैं, समय {time}"),
        p("घर पहुँच गया, चिंता मत करना"),
        p("जन्मदिन की बहुत बहुत शुभकामनाएँ {name}"),
        p("खाना खा लिया क्या"),
        p("उद्या भेटू या {time} वाजता"),
        p("मी घरी पोहोचलो आहे"),
        p("वाढदिवसाच्या हार्दिक शुभेच्छा {name}"),
        p("जेवण झालं का"),
        s("{otp} आपका ओटीपी है। किसी को न बताएँ।"),
        s("आपका ऑर्डर {ref} आज डिलीवर होगा"),
        s("तुमची ऑर्डर {ref} आज येईल"),
        s("{otp} हा तुमचा ओटीपी आहे. कोणालाही सांगू नका."),
    )

    fun templates(devanagari: Boolean, payment: Boolean, the: Boolean, personalOnly: Boolean): List<Template> {
        val pool = when {
            devanagari -> devaTemplates
            payment && the -> payThe
            payment -> payNoThe
            the -> plainThe
            else -> plainNoThe
        }
        return if (personalOnly) pool.filter { !it.service } else pool
    }

    val ALL_TEMPLATES: List<Template> = plainNoThe + plainThe + payNoThe + payThe + devaTemplates

    /** Fills the slots of [t] drawing from [rng] in a fixed order (order of appearance). */
    fun fill(t: Template, rng: SeedRandom, names: List<String>): String {
        val sb = StringBuilder()
        var i = 0
        val text = t.text
        while (i < text.length) {
            val c = text[i]
            if (c != '{') {
                sb.append(c)
                i++
                continue
            }
            val end = text.indexOf('}', i)
            sb.append(slot(text.substring(i + 1, end), rng, names))
            i = end + 1
        }
        return sb.toString()
    }

    private fun slot(name: String, rng: SeedRandom, names: List<String>): String = when (name) {
        "name" -> names[rng.nextInt(names.size)].substringBefore(' ')
        "n" -> (5 + rng.nextInt(40)).toString()
        "amt" -> (50 + rng.nextInt(9950)).toString()
        "ref" -> "SX" + (100000 + rng.nextInt(900000))
        "otp" -> (100000 + rng.nextInt(900000)).toString()
        "n4" -> (1000 + rng.nextInt(9000)).toString()
        "merchant" -> MERCHANTS[rng.nextInt(MERCHANTS.size)]
        "place" -> PLACES[rng.nextInt(PLACES.size)]
        "time" -> "%d:%02d".format(rng.nextInt(1, 12), rng.nextInt(12) * 5)
        "date" -> "%02d/%02d".format(rng.nextInt(1, 28), rng.nextInt(1, 12))
        else -> error("unknown slot $name")
    }

    // ---- files -----------------------------------------------------------------------------------------------

    val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val DEVA_MONTHS = listOf("जानेवारी", "मार्च", "मे", "जुलै", "सप्टेंबर", "नोव्हेंबर")
    val PROJECTS = listOf("Apollo", "Zenith", "Orion", "Lotus", "Falcon")

    /** (extension, weight %) of documents (LLD §4.3). */
    val DOC_EXT_MIX = listOf(
        "pdf" to 45, "docx" to 15, "doc" to 5, "xlsx" to 12, "xls" to 3, "pptx" to 6, "ppt" to 2, "txt" to 10,
        "csv" to 2,
    )

    /** Stems per extension; {yyyy} {mm} {Mon} {n} are filled from the file's modified date / a counter. */
    val DOC_STEMS = mapOf(
        "pdf" to listOf(
            "Invoice_{yyyy}_{mm}",
            "Salary slip {Mon} {yyyy}",
            "Bank statement {Mon} {yyyy}",
            "Electricity bill {Mon}",
            "Insurance policy",
            "Ticket_{n}",
            "Receipt {n}",
            "Report_{yyyy}",
        ),
        "docx" to listOf("Rent agreement {yyyy}", "Resume v{n}", "Meeting notes {Mon}", "Letter_{n}"),
        "doc" to listOf("Old notes {n}", "Form {n}"),
        "xlsx" to listOf("Budget {yyyy}", "Expenses {Mon}", "Inventory {n}"),
        "xls" to listOf("Sales {yyyy}", "Attendance {Mon}"),
        "pptx" to listOf("Project plan v{n}", "Quarterly review {yyyy}"),
        "ppt" to listOf("Training deck {n}"),
        "txt" to listOf("notes", "todo", "ideas", "readme"),
        "csv" to listOf("export_{n}", "contacts_{yyyy}"),
    )
    val DEVA_DOC_STEMS = listOf("पावती {Mon}", "किराया करार", "वेतन पर्ची", "बिल {n}", "प्रवेश पत्र", "पावती मार्च")

    /** Document folders (LLD §4.3); `{project}` is filled from [PROJECTS]. */
    val DOC_FOLDERS = listOf(
        "Download/" to 35,
        "Documents/" to 25,
        "Documents/Bills/" to 15,
        "Documents/Work/{project}/" to 15,
        "Download/WhatsApp Documents/" to 10,
    )

    /** (extension, weight %, folder) of other files. */
    val OTHER_MIX = listOf(
        Triple("zip", 25, "Download/"), Triple("apk", 10, "Download/"), Triple("rar", 5, "Download/"),
        Triple("json", 10, "Documents/"), Triple("ics", 5, "Download/"), Triple("vcf", 5, "Download/"),
        Triple("mp3", 20, "Music/"), Triple("xyz", 5, "Download/"), Triple("", 5, "Download/"),
        Triple("m4a", 10, "Recordings/Voice Recorder/"),
    )
    val OTHER_STEMS = listOf("archive", "backup", "photos", "app", "data", "event", "contact", "song", "track", "blob")
    val DEVA_OTHER_STEMS = listOf("बॅकअप", "फोटो संग्रह", "गाणी", "जुनी माहिती")

    fun mimeOf(ext: String): String = when (ext.lowercase()) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "doc" -> "application/msword"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "xls" -> "application/vnd.ms-excel"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "ppt" -> "application/vnd.ms-powerpoint"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "zip" -> "application/zip"
        "apk" -> "application/vnd.android.package-archive"
        "rar" -> "application/vnd.rar"
        "json" -> "application/json"
        "ics" -> "text/calendar"
        "vcf" -> "text/x-vcard"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "jpg" -> "image/jpeg"
        "mp4" -> "video/mp4"
        "amr" -> "audio/amr"
        else -> "application/octet-stream"
    }
}
