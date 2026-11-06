// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.engine

import keystone.engine.FieldType.*
import keystone.engine.ViewType.*

/** Builder used to declare preset collections compactly. */
class ColBuilder(val id: String) {
    val fields = mutableListOf<Field>()
    val views = mutableListOf<ViewDef>()
    var recurrence: Recurrence? = null

    private fun add(f: Field) = f.also { fields += it }
    fun text(k: String, n: String) = add(Field(k, n, TEXT))
    fun long(k: String, n: String) = add(Field(k, n, LONG_TEXT))
    fun number(k: String, n: String, unit: String = "") = add(Field(k, n, NUMBER, unit = unit))
    fun money(k: String, n: String) = add(Field(k, n, CURRENCY))
    fun date(k: String, n: String, remind: Boolean = false, annual: Boolean = false) = add(Field(k, n, DATE, remind = remind, annual = annual))
    fun time(k: String, n: String) = add(Field(k, n, TIME))
    fun select(k: String, n: String, vararg o: String) = add(Field(k, n, SELECT, options = Engine.opts(*o)))
    fun multi(k: String, n: String, vararg o: String) = add(Field(k, n, MULTI_SELECT, options = Engine.opts(*o)))
    fun check(k: String, n: String) = add(Field(k, n, CHECKBOX))
    fun rel(k: String, n: String, target: String, multi: Boolean = true) = add(Field(k, n, RELATION, target = target, multi = multi))
    fun rating(k: String, n: String, max: Int = 5) = add(Field(k, n, RATING, max = max))
    fun url(k: String, n: String) = add(Field(k, n, URL))
    fun email(k: String, n: String) = add(Field(k, n, EMAIL))
    fun phone(k: String, n: String) = add(Field(k, n, PHONE))
    fun file(k: String, n: String) = add(Field(k, n, FILE))
    fun image(k: String, n: String) = add(Field(k, n, IMAGE))
    fun progress(k: String, n: String) = add(Field(k, n, PROGRESS))
    fun rollup(k: String, n: String, source: String, via: String, op: RollupOp, field: String? = null, value: String? = null, unit: String = "") =
        add(Field(k, n, ROLLUP, rollup = Rollup(source, via, op, field, value), unit = unit))
    fun formula(k: String, n: String, expr: String, unit: String = "") = add(Field(k, n, FORMULA, formula = expr, unit = unit))

    fun list(name: String = "All", sorts: List<Sort> = emptyList(), filters: List<Filter> = emptyList(), columns: List<String> = emptyList(), id: String = Engine.slug(name)) =
        views.add(ViewDef(id, name, LIST, sorts = sorts, filters = filters, columns = columns))
    fun board(groupBy: String, name: String = "Board", filters: List<Filter> = emptyList(), columns: List<String> = emptyList()) =
        views.add(ViewDef(Engine.slug(name), name, BOARD, groupBy = groupBy, filters = filters, columns = columns))
    fun calendar(dateField: String, name: String = "Calendar") = views.add(ViewDef(Engine.slug(name), name, CALENDAR, dateField = dateField))
    fun gallery(imageField: String?, name: String = "Gallery", columns: List<String> = emptyList(), filters: List<Filter> = emptyList()) =
        views.add(ViewDef(Engine.slug(name), name, GALLERY, imageField = imageField, columns = columns, filters = filters))
    fun table(columns: List<String> = emptyList(), name: String = "Table", sorts: List<Sort> = emptyList()) =
        views.add(ViewDef(Engine.slug(name), name, TABLE, columns = columns, sorts = sorts))
    fun stats(name: String = "Stats", groupBy: String? = null, valueField: String? = null, dateField: String? = null, filters: List<Filter> = emptyList()) =
        views.add(ViewDef(Engine.slug(name), name, STATS, groupBy = groupBy, valueField = valueField, dateField = dateField, filters = filters))
}

fun desc(f: String) = listOf(Sort(f, desc = true))
fun asc(f: String) = listOf(Sort(f))

object Presets {
    val hubs: List<Hub> = listOf(
        Hub("basics", "Basics", "dashboard", "Tasks, projects, habits, sleep, journal and periodic notes",
            listOf("tasks", "projects", "areas", "res_collections", "resources", "tags", "habits", "days", "months", "years", "journal", "sleep")),
        Hub("setup", "Life Setup", "explore", "Wheel of life, core values, goals and your vision",
            listOf("life_areas", "values", "goals")),
        Hub("health", "Health & Fitness", "fitness", "Workouts, exercises and body measurements",
            listOf("workouts", "exercises", "body")),
        Hub("food", "Food & Meals", "restaurant", "Recipes, weekly meal plan and grocery list",
            listOf("recipes", "meal_plan", "groceries")),
        Hub("career", "Career", "work", "Job applications, professional network and your CV",
            listOf("jobs", "pro_contacts", "interactions", "cv_experience", "cv_education", "cv_skills")),
        Hub("money", "Finance", "wallet", "Income, expenses, budgets, savings and subscriptions",
            listOf("transactions", "budgets", "savings", "subscriptions")),
        Hub("growth", "Learning & Growth", "school", "Courses and skills, reading, vocabulary and resources",
            listOf("learning", "books", "vocabulary", "res_collections", "resources")),
        Hub("home", "Home & Car", "home", "Maintenance, improvements, inventory, vehicles and warranties",
            listOf("home_tasks", "inventory", "vehicles", "car_service", "warranties")),
        Hub("people", "People & Love", "favorite", "Friends and birthdays, love hub, gifts, wishlist and bucket list",
            listOf("people", "love", "gifts", "wishlist", "bucket_list")),
        Hub("leisure", "Leisure & Travel", "flight", "Trips, series and movies, video games",
            listOf("trips", "trip_items", "media", "games")),
    )

    fun hub(id: String) = hubs.firstOrNull { it.id == id }

    private fun c(id: String, name: String, icon: String, hub: String, desc: String, title: String = "Name",
                  para: Boolean = true, b: ColBuilder.() -> Unit): CollectionDef {
        val cb = ColBuilder(id).apply(b)
        if (cb.views.isEmpty()) cb.list()
        return CollectionDef(id, name, icon, hub, desc, cb.fields.toList(), cb.views.toList(), title, para, preset = true, recurrence = cb.recurrence)
    }

    private val EXPENSE_CATS = arrayOf("Housing", "Food", "Groceries", "Transport", "Shopping", "Health", "Entertainment", "Bills", "Subscriptions", "Education", "Travel", "Gifts", "Other")
    private val INCOME_CATS = arrayOf("Salary", "Freelance", "Investments", "Gift received", "Refund")
    private val ROOMS = arrayOf("Kitchen", "Living room", "Bedroom", "Bathroom", "Office", "Garage", "Garden", "Exterior", "Whole house")

    fun collections(): List<CollectionDef> = listOf(
        // ------------------------------------------------------------ BASICS / PARA
        c("tasks", "Tasks", "check", "basics", "Everything you need to do. Link tasks to a project or area.", "Task") {
            select("status", "Status", "To do", "Doing", "Done")
            select("priority", "Priority", "Low", "Medium", "High", "Urgent")
            date("due", "Due", remind = true)
            select("repeat", "Repeat", "None", "Daily", "Weekdays", "Weekly", "Monthly", "Yearly")
            number("estimate", "Estimate", "min")
            long("notes", "Notes")
            recurrence = Recurrence("repeat", "due", "status", "Done")
            list("Today", filters = listOf(Filter("due", FilterOp.DUE_WITHIN, "0"), Filter("status", FilterOp.NEQ, "Done")), columns = listOf("due", "priority", F_PROJECT), id = "today")
            list("Upcoming", sorts = asc("due"), filters = listOf(Filter("status", FilterOp.NEQ, "Done")), columns = listOf("due", "priority", F_PROJECT))
            board("status", columns = listOf("due", "priority"))
            calendar("due")
            list("Done", filters = listOf(Filter("status", FilterOp.EQ, "Done")))
            table(listOf("status", "priority", "due", F_PROJECT, F_AREA))
        },
        c("projects", "Projects", "rocket", "basics", "Short-term efforts with a goal and a deadline (the P in PARA).", "Project") {
            select("status", "Status", "Planned", "In progress", "On hold", "Done")
            select("priority", "Priority", "Low", "Medium", "High")
            date("start", "Start")
            date("due", "Deadline", remind = true)
            rel("goal", "Goal", "goals")
            rollup("progress", "Progress", "tasks", F_PROJECT, RollupOp.PERCENT_SELECT, "status", "Done")
            rollup("task_count", "Tasks", "tasks", F_PROJECT, RollupOp.COUNT)
            long("notes", "Notes")
            board("status", columns = listOf("progress", "due"))
            list("Active", filters = listOf(Filter("status", FilterOp.EQ, "In progress")), columns = listOf("progress", "due"))
            calendar("due")
            table(listOf("status", "progress", "due", F_AREA))
        },
        c("areas", "Areas", "layers", "basics", "Ongoing responsibilities you maintain over time (the A in PARA).", "Area") {
            select("kind", "Kind", "Personal", "Work", "Health", "Finance", "Relationships", "Home", "Growth")
            select("review", "Review", "Weekly", "Monthly", "Quarterly")
            long("standard", "Standard to maintain")
            rollup("projects", "Projects", "projects", F_AREA, RollupOp.COUNT)
            rollup("tasks", "Tasks", "tasks", F_AREA, RollupOp.COUNT)
            gallery(null, columns = listOf("kind", "projects", "tasks"))
            list()
        },
        c("res_collections", "Resource Collections", "library", "basics", "Notebooks, books, courses and topics that group your resources (Resources Manager).", "Collection") {
            select("type", "Type", "Notebook", "Book", "Course", "Topic", "Podcast", "Video series", "Documents", "Other")
            select("status", "Status", "Not started", "In progress", "Completed")
            image("cover", "Cover")
            rollup("progress", "Learning progress", "resources", "collection", RollupOp.PERCENT_CHECKED, "done")
            rollup("count", "Resources", "resources", "collection", RollupOp.COUNT)
            long("description", "Description")
            gallery("cover", columns = listOf("type", "progress"))
            board("type", columns = listOf("progress"))
            list()
        },
        c("resources", "Resources", "bookmark", "basics", "Notes, links, bookmarks and documents (the R in PARA).", "Resource") {
            select("type", "Type", "Note", "Link", "Bookmark", "Document", "File", "Video", "Article", "Lesson", "Chapter")
            rel("collection", "Collection", "res_collections", multi = false)
            url("url", "Link")
            file("file", "File")
            long("content", "Content")
            check("done", "Completed")
            rating("rating", "Rating")
            date("added", "Added")
            list(columns = listOf("type", "collection"))
            board("type", columns = listOf("collection"))
            list("To learn", filters = listOf(Filter("done", FilterOp.UNCHECKED)), columns = listOf("collection"))
            table(listOf("type", "collection", "done", "url"))
        },
        c("tags", "Tags", "tag", "basics", "One tag system across the whole app. Open a tag to see everything linked to it.", "Tag", para = false) {
            select("color", "Color", "Gray", "Red", "Orange", "Yellow", "Green", "Blue", "Purple", "Pink")
            long("description", "Description")
            list()
        },
        c("habits", "Habits", "repeat", "basics", "Habits you track each day. Check them off on the Today page.", "Habit") {
            select("frequency", "Frequency", "Daily", "Weekdays", "3x a week", "Weekly")
            select("time", "Time of day", "Morning", "Afternoon", "Evening", "Any time")
            check("active", "Active")
            text("cue", "Cue / trigger")
            long("why", "Why it matters")
            rollup("total", "Days done", "days", "habits", RollupOp.COUNT)
            list(filters = listOf(Filter("active", FilterOp.CHECKED)), columns = listOf("frequency", "total"), name = "Active")
            board("time")
            list("All habits")
        },
        c("days", "Days", "today", "basics", "Daily notes: mood, energy, habits and highlights. Created automatically.", "Day", para = false) {
            date("date", "Date")
            rating("mood", "Mood")
            rating("energy", "Energy")
            rel("habits", "Habits done", "habits")
            number("water", "Water", "glasses")
            long("highlight", "Highlight")
            long("notes", "Notes")
            rel("month", "Month", "months", multi = false)
            list(sorts = desc("date"), columns = listOf("mood", "energy", "habits"))
            calendar("date")
            stats("Mood", valueField = "mood", dateField = "date")
        },
        c("months", "Months", "calendar_month", "basics", "Monthly notes with focus and review.", "Month", para = false) {
            date("date", "Month")
            long("focus", "Focus")
            long("review", "Review")
            rel("year", "Year", "years", multi = false)
            rollup("days", "Days logged", "days", "month", RollupOp.COUNT)
            rollup("mood", "Average mood", "days", "month", RollupOp.AVG, "mood")
            list(sorts = desc("date"), columns = listOf("days", "mood"))
        },
        c("years", "Years", "event", "basics", "Yearly notes: theme and review.", "Year", para = false) {
            date("date", "Year")
            text("theme", "Theme")
            long("review", "Review")
            rollup("months", "Months", "months", "year", RollupOp.COUNT)
            list(sorts = desc("date"))
        },
        c("journal", "Journal", "book", "basics", "Daily journaling: reflections, gratitude and free writing.", "Title") {
            date("date", "Date")
            select("kind", "Type", "Morning pages", "Evening reflection", "Gratitude", "Free write", "Dream", "Idea")
            rating("mood", "Mood")
            long("content", "Entry")
            list(sorts = desc("date"), columns = listOf("date", "kind", "mood"))
            calendar("date")
            board("kind")
        },
        c("sleep", "Sleep", "bedtime", "basics", "Bedtime, wake time and quality, with trends.", "Night", para = false) {
            date("date", "Date")
            time("bed", "Bedtime")
            time("wake", "Wake up")
            formula("hours", "Hours slept", "hours_between(bed,wake)", "h")
            rating("quality", "Quality")
            long("notes", "Notes")
            list(sorts = desc("date"), columns = listOf("hours", "quality"))
            stats("Trend", valueField = "hours", dateField = "date")
            calendar("date")
        },
        // ------------------------------------------------------------ LIFE SETUP
        c("life_areas", "Wheel of Life", "donut", "setup", "Rate each area of life now and where you want it to be.", "Life area", para = false) {
            rating("current", "Current", 10)
            rating("target", "Target", 10)
            formula("gap", "Gap", "sub(target,current)")
            long("notes", "What would make it a 10?")
            list(sorts = asc("gap"), columns = listOf("current", "target", "gap"))
            stats("Wheel", groupBy = null, valueField = "current")
        },
        c("values", "Core Values", "diamond", "setup", "The principles that guide your decisions.", "Value", para = false) {
            long("meaning", "What it means to me")
            rating("importance", "Importance")
            list(sorts = desc("importance"), columns = listOf("importance"))
        },
        c("goals", "Goals", "flag", "setup", "End goals (what you want) and the means goals that get you there.", "Goal") {
            select("kind", "Kind", "End goal", "Means goal")
            select("status", "Status", "Not started", "Active", "Achieved", "Dropped")
            rel("parent", "Serves (end goal)", "goals", multi = false)
            rel("life_area", "Life area", "life_areas", multi = false)
            date("target", "Target date", remind = true)
            progress("progress", "Progress")
            rollup("means", "Means goals", "goals", "parent", RollupOp.COUNT)
            rollup("projects", "Projects", "projects", "goal", RollupOp.COUNT)
            long("why", "Why")
            board("kind", columns = listOf("progress", "target"))
            board("status", name = "By status")
            list(columns = listOf("kind", "progress", "target"))
            calendar("target")
        },
        // ------------------------------------------------------------ HEALTH & FITNESS
        c("workouts", "Workouts", "fitness", "health", "Gym sessions and training log.", "Workout") {
            date("date", "Date")
            select("type", "Type", "Strength", "Cardio", "HIIT", "Mobility", "Sport", "Yoga", "Other")
            number("duration", "Duration", "min")
            number("calories", "Calories", "kcal")
            rel("exercises", "Exercises", "exercises")
            rating("effort", "Effort")
            long("log", "Sets / reps / notes")
            list(sorts = desc("date"), columns = listOf("date", "type", "duration"))
            calendar("date")
            stats("Minutes per week", valueField = "duration", dateField = "date")
            board("type")
        },
        c("exercises", "Exercise Library", "barbell", "health", "Your exercises with personal bests.", "Exercise", para = false) {
            select("muscle", "Muscle group", "Chest", "Back", "Legs", "Shoulders", "Arms", "Core", "Full body", "Cardio")
            select("equipment", "Equipment", "Barbell", "Dumbbell", "Machine", "Cable", "Bodyweight", "Kettlebell", "Band", "None")
            number("pb", "Personal best", "kg")
            number("pb_reps", "PB reps")
            url("video", "Demo video")
            long("notes", "Form notes")
            rollup("sessions", "Sessions", "workouts", "exercises", RollupOp.COUNT)
            board("muscle", columns = listOf("pb", "equipment"))
            list(columns = listOf("muscle", "pb"))
        },
        c("body", "Body Metrics", "monitor_weight", "health", "Weight and body measurements over time.", "Entry", para = false) {
            date("date", "Date")
            number("weight", "Weight", "kg")
            number("body_fat", "Body fat", "%")
            number("waist", "Waist", "cm")
            number("resting_hr", "Resting HR", "bpm")
            long("notes", "Notes")
            list(sorts = desc("date"), columns = listOf("weight", "body_fat"))
            stats("Weight trend", valueField = "weight", dateField = "date")
        },
        // ------------------------------------------------------------ FOOD
        c("recipes", "Recipes", "restaurant", "food", "Your recipe book.", "Recipe") {
            multi("meal", "Meal", "Breakfast", "Lunch", "Dinner", "Snack", "Dessert", "Drink")
            select("cuisine", "Cuisine", "American", "Italian", "Mexican", "Asian", "Indian", "Mediterranean", "Other")
            number("prep", "Prep + cook time", "min")
            number("servings", "Servings")
            number("calories", "Calories / serving", "kcal")
            long("ingredients", "Ingredients")
            long("steps", "Instructions")
            image("photo", "Photo")
            url("source", "Source")
            rating("rating", "Rating")
            check("favorite", "Favorite")
            gallery("photo", columns = listOf("meal", "prep", "rating"))
            board("cuisine")
            list("Favorites", filters = listOf(Filter("favorite", FilterOp.CHECKED)))
        },
        c("meal_plan", "Meal Planner", "menu_book", "food", "Plan meals for the week.", "Meal") {
            date("date", "Date")
            select("slot", "Slot", "Breakfast", "Lunch", "Dinner", "Snack")
            rel("recipe", "Recipe", "recipes", multi = false)
            check("done", "Cooked")
            long("notes", "Notes")
            calendar("date")
            list("This week", sorts = asc("date"), filters = listOf(Filter("date", FilterOp.GTE, "today"), Filter("date", FilterOp.LTE, "today+7")), columns = listOf("date", "slot", "recipe"))
            board("slot")
        },
        c("groceries", "Grocery List", "shopping_cart", "food", "Shopping list grouped by aisle.", "Item", para = false) {
            select("aisle", "Aisle", "Produce", "Dairy", "Meat & Fish", "Bakery", "Pantry", "Frozen", "Drinks", "Household", "Other")
            text("qty", "Quantity")
            check("bought", "Bought")
            board("aisle", filters = listOf(Filter("bought", FilterOp.UNCHECKED)), name = "To buy")
            list("All")
        },
        // ------------------------------------------------------------ CAREER
        c("jobs", "Job Applications", "work", "career", "Track every application from wishlist to offer.", "Role") {
            text("company", "Company")
            select("status", "Status", "Wishlist", "Applied", "Interviewing", "Offer", "Accepted", "Rejected")
            date("applied", "Applied on")
            date("follow_up", "Follow up", remind = true)
            money("salary", "Salary")
            text("location", "Location")
            select("mode", "Work mode", "On-site", "Hybrid", "Remote")
            url("url", "Job post")
            rel("contact", "Contact", "pro_contacts")
            text("next", "Next step")
            long("notes", "Notes")
            board("status", columns = listOf("company", "follow_up"))
            list(sorts = desc("applied"), columns = listOf("company", "status"))
            table(listOf("company", "status", "applied", "salary", "mode"))
            stats("Pipeline", groupBy = "status")
        },
        c("pro_contacts", "Professional CRM", "badge", "career", "Your professional network.", "Name") {
            text("company", "Company")
            text("role", "Role")
            select("relationship", "Relationship", "Colleague", "Manager", "Mentor", "Recruiter", "Client", "Investor", "Other")
            email("email", "Email")
            phone("phone", "Phone")
            url("linkedin", "LinkedIn")
            date("last_contact", "Last contact")
            date("follow_up", "Next follow-up", remind = true)
            rollup("interactions", "Interactions", "interactions", "contact", RollupOp.COUNT)
            long("notes", "Notes")
            list(columns = listOf("company", "role"))
            board("relationship")
            table(listOf("company", "role", "email", "last_contact", "follow_up"))
        },
        c("interactions", "Interactions", "forum", "career", "Meetings, calls and emails with your network.", "Interaction") {
            date("date", "Date")
            rel("contact", "Contact", "pro_contacts")
            select("kind", "Type", "Meeting", "Call", "Email", "Coffee", "Event", "Message")
            date("follow_up", "Follow up", remind = true)
            long("notes", "Notes")
            list(sorts = desc("date"), columns = listOf("date", "contact", "kind"))
            calendar("date")
        },
        c("cv_experience", "CV · Experience", "history_edu", "career", "Work experience for your CV.", "Job title", para = false) {
            text("company", "Company")
            text("location", "Location")
            date("start", "Start")
            date("end", "End")
            check("current", "Current role")
            long("summary", "Achievements")
            list(sorts = desc("start"), columns = listOf("company", "start", "end"))
        },
        c("cv_education", "CV · Education", "school", "career", "Education and certifications for your CV.", "Degree", para = false) {
            text("school", "School")
            date("start", "Start")
            date("end", "End")
            long("details", "Details")
            list(sorts = desc("end"), columns = listOf("school", "end"))
        },
        c("cv_skills", "CV · Skills", "construction", "career", "Skills and languages for your CV.", "Skill", para = false) {
            select("category", "Category", "Technical", "Tools", "Soft skills", "Languages")
            rating("level", "Level")
            board("category", columns = listOf("level"))
            list(columns = listOf("category", "level"))
        },
        // ------------------------------------------------------------ FINANCE
        c("transactions", "Transactions", "receipt", "money", "Income and expenses.", "Description", para = false) {
            money("amount", "Amount")
            select("kind", "Type", "Expense", "Income")
            select("category", "Category", *EXPENSE_CATS, *INCOME_CATS)
            date("date", "Date")
            select("account", "Account", "Cash", "Checking", "Savings", "Credit card", "Other")
            long("notes", "Notes")
            list(sorts = desc("date"), columns = listOf("amount", "category", "date"))
            list("This month", sorts = desc("date"), filters = listOf(Filter("date", FilterOp.THIS_MONTH)), columns = listOf("amount", "category", "kind"))
            stats("Spending by category", groupBy = "category", valueField = "amount", filters = listOf(Filter("kind", FilterOp.EQ, "Expense"), Filter("date", FilterOp.THIS_MONTH)))
            stats("Monthly flow", valueField = "amount", dateField = "date", filters = listOf(Filter("kind", FilterOp.EQ, "Expense")))
            calendar("date")
            table(listOf("amount", "kind", "category", "date", "account"))
        },
        c("budgets", "Budgets", "savings", "money", "Monthly limits per category; spending is calculated from transactions.", "Budget", para = false) {
            select("category", "Category", *EXPENSE_CATS)
            money("limit", "Monthly limit")
            long("notes", "Notes")
            list(columns = listOf("category", "limit"))
        },
        c("savings", "Savings Goals", "paid", "money", "Money you are putting aside for something.", "Goal") {
            money("target", "Target")
            money("saved", "Saved so far")
            formula("pct", "Progress", "pct(saved,target)", "%")
            date("deadline", "Deadline", remind = true)
            long("notes", "Notes")
            list(columns = listOf("pct", "saved", "target"))
            gallery(null, columns = listOf("pct", "deadline"))
        },
        c("subscriptions", "Subscriptions", "autorenew", "money", "Recurring payments and when they renew.", "Service", para = false) {
            money("price", "Price")
            select("cycle", "Billing cycle", "Monthly", "Yearly", "Weekly", "Quarterly")
            formula("monthly", "Per month", "monthly(price,cycle)", "$")
            date("renews", "Next renewal", remind = true)
            select("category", "Category", "Streaming", "Software", "Music", "Gaming", "News", "Fitness", "Cloud", "Other")
            check("active", "Active")
            url("manage", "Manage link")
            list(sorts = asc("renews"), columns = listOf("price", "cycle", "renews"))
            stats("Cost by category", groupBy = "category", valueField = "monthly", filters = listOf(Filter("active", FilterOp.CHECKED)))
        },
        // ------------------------------------------------------------ LEARNING & GROWTH
        c("learning", "Learning", "school", "growth", "Courses, skills and certifications you are working on.", "Course / skill") {
            select("kind", "Type", "Course", "Skill", "Certification", "Workshop", "Tutorial", "Podcast")
            select("status", "Status", "Planned", "In progress", "Completed", "Paused")
            text("platform", "Platform / teacher")
            progress("progress", "Progress")
            date("start", "Started")
            date("finish", "Finish by", remind = true)
            rel("collection", "Resource collection", "res_collections")
            url("url", "Link")
            long("notes", "Key takeaways")
            board("status", columns = listOf("progress", "kind"))
            list(columns = listOf("kind", "progress"))
            calendar("finish")
        },
        c("books", "Reading Tracker", "menu_book", "growth", "Books to read, reading now and finished.", "Title") {
            text("author", "Author")
            select("status", "Status", "To read", "Reading", "Finished", "Abandoned")
            select("format", "Format", "Paperback", "Hardcover", "E-book", "Audiobook")
            multi("genre", "Genre", "Fiction", "Non-fiction", "Self-help", "Business", "Biography", "Sci-fi", "Fantasy", "History", "Science")
            number("pages", "Pages")
            number("page", "Current page")
            formula("progress", "Progress", "pct(page,pages)", "%")
            rating("rating", "Rating")
            date("started", "Started")
            date("finished", "Finished")
            image("cover", "Cover")
            long("notes", "Highlights & notes")
            board("status", columns = listOf("author", "progress"))
            gallery("cover", columns = listOf("author", "rating"))
            list("Reading now", filters = listOf(Filter("status", FilterOp.EQ, "Reading")), columns = listOf("author", "progress"))
            stats("Books per month", dateField = "finished", filters = listOf(Filter("status", FilterOp.EQ, "Finished")))
        },
        c("vocabulary", "Vocabulary", "translate", "growth", "New words with meanings and examples. Practice with flashcards.", "Word", para = false) {
            select("language", "Language", "English", "Spanish", "French", "German", "Italian", "Portuguese", "Japanese", "Korean", "Chinese", "Other")
            select("pos", "Part of speech", "Noun", "Verb", "Adjective", "Adverb", "Phrase", "Idiom")
            long("meaning", "Meaning")
            long("example", "Example sentence")
            check("learned", "Learned")
            date("added", "Added")
            list(columns = listOf("meaning", "language"))
            board("language")
            list("To learn", filters = listOf(Filter("learned", FilterOp.UNCHECKED)), columns = listOf("meaning"))
        },
        // ------------------------------------------------------------ HOME & CAR
        c("home_tasks", "Home Maintenance", "handyman", "home", "Maintenance, repairs and improvement projects around the house.", "Task") {
            select("kind", "Kind", "Maintenance", "Improvement", "Repair", "Cleaning", "Garden")
            select("room", "Room", *ROOMS)
            select("status", "Status", "To do", "Scheduled", "In progress", "Done")
            date("due", "Due", remind = true)
            select("repeat", "Repeat", "One-time", "Monthly", "Quarterly", "Every 6 months", "Yearly")
            money("cost", "Cost")
            text("pro", "Contractor")
            long("notes", "Notes")
            recurrence = Recurrence("repeat", "due", "status", "Done")
            board("status", columns = listOf("room", "due"))
            list(sorts = asc("due"), columns = listOf("kind", "due"))
            board("kind", name = "By kind")
            calendar("due")
        },
        c("inventory", "Home Inventory", "inventory", "home", "What you own, where it is and what it cost.", "Item", para = false) {
            select("room", "Room", *ROOMS, "Storage")
            select("category", "Category", "Electronics", "Furniture", "Appliance", "Tool", "Clothing", "Kitchenware", "Valuables", "Documents", "Other")
            date("bought", "Purchased")
            money("price", "Price")
            number("qty", "Quantity")
            text("serial", "Serial / model")
            image("photo", "Photo")
            rel("warranty", "Warranty", "warranties", multi = false)
            long("notes", "Notes")
            gallery("photo", columns = listOf("room", "price"))
            board("room")
            table(listOf("category", "room", "price", "bought", "serial"))
            stats("Value by room", groupBy = "room", valueField = "price")
        },
        c("vehicles", "Vehicles", "car", "home", "Your cars with insurance and registration dates.", "Vehicle", para = false) {
            text("make", "Make & model")
            number("year", "Year")
            text("plate", "Plate")
            text("vin", "VIN")
            number("mileage", "Mileage", "mi")
            date("insurance", "Insurance renewal", remind = true)
            date("registration", "Registration due", remind = true)
            image("photo", "Photo")
            rollup("spent", "Service spend", "car_service", "vehicle", RollupOp.SUM, "cost")
            gallery("photo", columns = listOf("make", "mileage"))
        },
        c("car_service", "Car Service Log", "build", "home", "Service history and what's due next.", "Service", para = false) {
            rel("vehicle", "Vehicle", "vehicles", multi = false)
            select("type", "Type", "Oil change", "Tires", "Brakes", "Inspection", "Battery", "Fluids", "Repair", "Wash", "Other")
            date("date", "Date")
            number("mileage", "Mileage", "mi")
            money("cost", "Cost")
            text("shop", "Shop")
            date("next_due", "Next due", remind = true)
            number("next_mileage", "Next due at", "mi")
            long("notes", "Notes")
            list(sorts = desc("date"), columns = listOf("vehicle", "date", "cost"))
            board("type")
            calendar("date")
            stats("Spend per month", valueField = "cost", dateField = "date")
        },
        c("warranties", "Warranties", "verified", "home", "Warranties, receipts and when they expire.", "Product", para = false) {
            text("store", "Store")
            date("bought", "Purchased")
            date("expires", "Warranty expires", remind = true)
            formula("days_left", "Days left", "days_until(expires)", "days")
            money("price", "Price")
            file("receipt", "Receipt")
            select("category", "Category", "Electronics", "Appliance", "Furniture", "Vehicle", "Tool", "Other")
            long("notes", "Claim info")
            list(sorts = asc("expires"), columns = listOf("expires", "days_left"))
            list("Active", filters = listOf(Filter("expires", FilterOp.GTE, "today")), sorts = asc("expires"), columns = listOf("days_left"))
            table(listOf("store", "bought", "expires", "price"))
        },
        // ------------------------------------------------------------ PEOPLE & LOVE
        c("people", "People & Birthdays", "group", "people", "Friends and family, birthdays and keeping in touch.", "Name") {
            select("relationship", "Relationship", "Family", "Partner", "Friend", "Colleague", "Neighbor", "Acquaintance")
            date("birthday", "Birthday", remind = true, annual = true)
            formula("age", "Age", "age(birthday)", "yrs")
            formula("bday_in", "Birthday in", "days_until_annual(birthday)", "days")
            phone("phone", "Phone")
            email("email", "Email")
            text("address", "Address")
            date("last_contact", "Last contact")
            select("cadence", "Keep in touch", "Weekly", "Monthly", "Quarterly", "Yearly")
            long("likes", "Likes, sizes & ideas")
            image("photo", "Photo")
            long("notes", "Notes")
            list(sorts = asc("bday_in"), columns = listOf("birthday", "bday_in"), name = "Next birthdays")
            board("relationship")
            gallery("photo", columns = listOf("relationship"))
            table(listOf("relationship", "birthday", "phone", "last_contact"))
        },
        c("love", "Love Hub", "favorite", "people", "Date ideas, date nights, memories and special dates with your partner.", "Moment") {
            select("kind", "Kind", "Date idea", "Date night", "Memory", "Anniversary", "Love note", "Plan")
            date("date", "Date", remind = true)
            text("place", "Place")
            money("cost", "Cost")
            check("done", "Done")
            rating("rating", "Rating")
            image("photo", "Photo")
            long("notes", "Notes")
            board("kind")
            list("Date ideas", filters = listOf(Filter("kind", FilterOp.EQ, "Date idea"), Filter("done", FilterOp.UNCHECKED)))
            gallery("photo", name = "Memories", filters = listOf(Filter("kind", FilterOp.EQ, "Memory")))
            calendar("date")
        },
        c("gifts", "Gift Ideas", "redeem", "people", "Gift ideas for the people you love.", "Gift") {
            rel("for", "For", "people")
            select("occasion", "Occasion", "Birthday", "Holidays", "Anniversary", "Valentine's", "Graduation", "Just because")
            select("status", "Status", "Idea", "Bought", "Given")
            money("price", "Price")
            url("url", "Link")
            date("date", "Give on", remind = true)
            long("notes", "Notes")
            board("status", columns = listOf("for", "price"))
            list(columns = listOf("for", "occasion"))
        },
        c("wishlist", "Wishlist", "card_giftcard", "people", "Things you want for yourself.", "Item") {
            money("price", "Price")
            url("url", "Link")
            select("priority", "Priority", "Someday", "Want", "Need")
            select("category", "Category", "Tech", "Clothing", "Home", "Books", "Games", "Hobby", "Experience", "Other")
            check("bought", "Bought")
            image("image", "Image")
            gallery("image", columns = listOf("price", "priority"), filters = listOf(Filter("bought", FilterOp.UNCHECKED)))
            board("priority")
            list("All")
        },
        c("bucket_list", "Bucket List", "beach", "people", "Experiences you want to have in your life.", "Dream") {
            select("category", "Category", "Travel", "Adventure", "Learning", "Career", "Creative", "Personal", "Experience")
            date("target", "Target date")
            check("done", "Done")
            date("done_on", "Done on")
            image("image", "Image")
            long("notes", "Notes")
            gallery("image", columns = listOf("category", "done"))
            board("category")
            list("Still to do", filters = listOf(Filter("done", FilterOp.UNCHECKED)))
        },
        // ------------------------------------------------------------ LEISURE & TRAVEL
        c("trips", "Travel Planner", "flight", "leisure", "Trips from dreaming to done.", "Trip") {
            text("destination", "Destination")
            select("status", "Status", "Dreaming", "Planning", "Booked", "Done")
            date("start", "Start", remind = true)
            date("end", "End")
            formula("nights", "Nights", "days_between(start,end)")
            money("budget", "Budget")
            rollup("spent", "Planned cost", "trip_items", "trip", RollupOp.SUM, "cost")
            rollup("packed", "Checklist done", "trip_items", "trip", RollupOp.PERCENT_CHECKED, "done")
            image("cover", "Cover")
            long("notes", "Notes")
            gallery("cover", columns = listOf("destination", "start"))
            board("status")
            calendar("start")
        },
        c("trip_items", "Itinerary & Packing", "luggage", "leisure", "Bookings, activities and packing list for each trip.", "Item", para = false) {
            rel("trip", "Trip", "trips", multi = false)
            select("kind", "Kind", "Flight", "Stay", "Transport", "Activity", "Restaurant", "Packing", "To-do", "Note")
            date("date", "Date")
            time("time", "Time")
            money("cost", "Cost")
            check("done", "Booked / packed")
            url("url", "Link")
            long("notes", "Notes / confirmation")
            list(sorts = asc("date"), columns = listOf("trip", "kind", "date", "time"))
            board("kind")
            list("Packing", filters = listOf(Filter("kind", FilterOp.EQ, "Packing")), columns = listOf("trip", "done"))
            calendar("date")
        },
        c("media", "Series & Movies", "movie", "leisure", "Watchlist, currently watching and ratings.", "Title") {
            select("kind", "Kind", "Movie", "Series", "Documentary", "Anime")
            select("status", "Status", "Watchlist", "Watching", "Watched", "Dropped")
            multi("genre", "Genre", "Action", "Comedy", "Drama", "Thriller", "Sci-fi", "Fantasy", "Horror", "Romance", "Animation", "Documentary")
            select("platform", "Where", "Netflix", "Prime Video", "Disney+", "Max", "Hulu", "Apple TV+", "Cinema", "Other")
            number("season", "Season")
            number("episode", "Episode")
            rating("rating", "Rating")
            date("watched", "Watched on")
            image("poster", "Poster")
            long("notes", "Notes")
            board("status", columns = listOf("kind", "platform"))
            gallery("poster", columns = listOf("kind", "rating"))
            list("Watching", filters = listOf(Filter("status", FilterOp.EQ, "Watching")), columns = listOf("season", "episode"))
            stats("By genre", groupBy = "genre")
        },
        c("games", "Video Games", "sports_esports", "leisure", "Your game library, backlog and playtime.", "Game") {
            multi("platform", "Platform", "PC", "Steam Deck", "PlayStation", "Xbox", "Switch", "Mobile", "Retro")
            select("status", "Status", "Wishlist", "Backlog", "Playing", "Completed", "100%", "Dropped")
            multi("genre", "Genre", "RPG", "Action", "Adventure", "Shooter", "Strategy", "Puzzle", "Platformer", "Simulation", "Indie", "Horror", "Roguelike")
            number("hours", "Hours played", "h")
            rating("rating", "Rating")
            date("started", "Started")
            date("finished", "Finished")
            image("cover", "Cover")
            url("store", "Store page")
            long("notes", "Notes")
            board("status", columns = listOf("platform", "hours"))
            gallery("cover", columns = listOf("platform", "rating"))
            list("Playing", filters = listOf(Filter("status", FilterOp.EQ, "Playing")), columns = listOf("hours"))
            stats("Hours by genre", groupBy = "genre", valueField = "hours")
        },
    )

    val WHEEL_AREAS = listOf("Health & Fitness", "Career & Work", "Finance", "Relationships & Love", "Family & Friends", "Personal Growth", "Fun & Recreation", "Home & Environment")

    val VALUE_SUGGESTIONS = listOf("Honesty", "Growth", "Family", "Freedom", "Health", "Creativity", "Courage", "Kindness", "Discipline",
        "Adventure", "Curiosity", "Loyalty", "Gratitude", "Balance", "Excellence", "Faith", "Humor", "Independence", "Learning",
        "Love", "Peace", "Respect", "Responsibility", "Security", "Simplicity", "Wisdom", "Community", "Impact", "Joy", "Integrity")

    /** First-run content: tags, life areas for the wheel, a PARA area per hub and a couple of starter habits. */
    fun seed(e: Engine) {
        listOf("Important" to "Red", "Idea" to "Yellow", "Waiting" to "Orange", "Someday" to "Gray", "Quick win" to "Green")
            .forEach { (n, c) -> e.create("tags", n, mapOf("color" to V.str(c))) }
        WHEEL_AREAS.forEach { e.create("life_areas", it, mapOf("current" to V.num(5), "target" to V.num(8))) }
        listOf("Health" to "Health", "Work" to "Work", "Finances" to "Finance", "Home" to "Home", "Relationships" to "Relationships", "Learning" to "Growth")
            .forEach { (n, k) -> e.create("areas", n, mapOf("kind" to V.str(k), "review" to V.str("Weekly"))) }
        listOf("Drink water" to "Morning", "Move 30 minutes" to "Any time", "Read 10 pages" to "Evening")
            .forEach { (n, t) -> e.create("habits", n, mapOf("frequency" to V.str("Daily"), "time" to V.str(t), "active" to V.bool(true))) }
    }

    private var wid = 0
    private fun w(type: WidgetType, collection: String? = null, view: String? = null, limit: Int = 5, title: String? = null) =
        Widget("w${wid++}", type, collection, view, limit, title)

    fun dashboards(): List<Dashboard> {
        wid = 0
        return listOf(
            Dashboard("simple", "Simple", listOf(
                w(WidgetType.GREETING), w(WidgetType.HABITS_TODAY), w(WidgetType.TODAY_TASKS), w(WidgetType.UPCOMING, limit = 4),
            )),
            Dashboard("full", "Command Center", listOf(
                w(WidgetType.GREETING), w(WidgetType.VISION), w(WidgetType.QUICK_LINKS), w(WidgetType.HABITS_TODAY), w(WidgetType.TODAY_TASKS),
                w(WidgetType.DAY_NOTE), w(WidgetType.PROJECTS), w(WidgetType.GOALS), w(WidgetType.UPCOMING),
                w(WidgetType.MONEY_MONTH), w(WidgetType.BUDGETS), w(WidgetType.SLEEP_CHART), w(WidgetType.WEIGHT_CHART),
                w(WidgetType.READING_NOW), w(WidgetType.LEARNING_PROGRESS), w(WidgetType.BIRTHDAYS), w(WidgetType.WHEEL_OF_LIFE), w(WidgetType.CORE_VALUES),
            )),
            Dashboard("mobile", "Mobile", listOf(
                w(WidgetType.GREETING), w(WidgetType.QUICK_ADD, "tasks", title = "Quick task"), w(WidgetType.HABITS_TODAY), w(WidgetType.TODAY_TASKS, limit = 3),
            )),
            Dashboard("custom", "My Dashboard", listOf(
                w(WidgetType.GREETING), w(WidgetType.COLLECTION_LIST, "tasks", "today", title = "Today"),
            )),
        )
    }

    /** Every widget a user can add: fixed widgets plus list / counter / quick-add / chart variants for each collection. */
    fun widgetCatalog(e: Engine): List<Widget> {
        val fixed = WidgetType.entries.filter { it !in setOf(WidgetType.COLLECTION_LIST, WidgetType.COUNTER, WidgetType.QUICK_ADD, WidgetType.STATS_CHART) }
            .map { Widget("cat_${it.name}", it) }
        val perCollection = e.collections().flatMap { c ->
            val views = c.views.filter { it.type != ViewType.STATS && it.type != ViewType.CALENDAR }.take(2).map {
                Widget("cat_list_${c.id}_${it.id}", WidgetType.COLLECTION_LIST, c.id, it.id, title = "${c.name} · ${it.name}")
            }
            val charts = c.views.filter { it.type == ViewType.STATS }.map {
                Widget("cat_chart_${c.id}_${it.id}", WidgetType.STATS_CHART, c.id, it.id, title = "${c.name} · ${it.name}")
            }
            views + charts + Widget("cat_count_${c.id}", WidgetType.COUNTER, c.id, c.views.first().id, title = c.name) +
                Widget("cat_add_${c.id}", WidgetType.QUICK_ADD, c.id, title = "Add to ${c.name}")
        }
        return fixed + perCollection
    }
}
