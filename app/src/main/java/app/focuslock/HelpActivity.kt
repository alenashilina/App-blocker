package app.focuslock

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import java.util.Locale

/** "How to use" guide in English and Russian. Opens in Russian if the phone is set to Russian. */
class HelpActivity : Activity() {
    private lateinit var ui: Ui
    private var russian = Locale.getDefault().language == "ru"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this, screen())
        render()
    }

    private fun render() {
        ui.clear()
        title = if (russian) "Как пользоваться" else "How to use"

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((label, ru) in listOf("English" to false, "Русский" to true)) {
            tabs.addView(Button(this).apply {
                text = label
                isAllCaps = false
                isEnabled = russian != ru // the current language is greyed out
                setOnClickListener {
                    russian = ru
                    render()
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        ui.add(tabs)

        for ((heading, body) in if (russian) RU else EN) {
            ui.header(heading)
            ui.text(body)
        }
        ui.text("", size = 8f)
        ui.button(if (russian) "Понятно" else "Got it") { finish() }
    }

    companion object {
        private val EN = listOf(
            "1. Turn on the blocker" to
                "Focus Lock needs the Accessibility permission to see which app is opening. On the main screen tap " +
                "\"Turn on blocker\" → Installed apps → Focus Lock → turn on.\n\n" +
                "If the switch is greyed out: Settings → Apps → Focus Lock → ⋮ (top right) → " +
                "Allow restricted settings, then try again.",

            "2. Modes" to
                "A mode is a set of apps blocked on a schedule. Each mode has an emoji, a name, apps and days of the week. " +
                "There are two kinds:\n\n" +
                "• At set times: for example 💼 Work, Mon–Fri 09:00–18:00. If the end is earlier than the start " +
                "(22:00–07:00), the mode ends the next morning.\n\n" +
                "• Morning: after I wake up: blocks for a set time after the first time you unlock your phone after a chosen " +
                "hour (04:00 by default). Unlock at 7:15 with a 60-minute mode, and the apps are blocked until 8:15. " +
                "Turning off an alarm doesn't count, because you don't unlock the phone for that. " +
                "Unlocking before the chosen hour (say, at 3 am) doesn't count either.",

            "3. Turning a mode off" to
                "Every mode has a switch. When you turn it off, you choose:\n\n" +
                "• Just for today: for public holidays and days off. It turns itself back on tomorrow.\n" +
                "• Until I turn it back on.\n\n" +
                "If the mode is blocking right now, turning it off needs an emergency unlock. " +
                "A mode can't be edited while it's blocking.",

            "4. Quick blocks" to
                "Like a timer. Tap \"Block apps now\", choose apps, then tap 30 min, 1 h, 2 h, 4 h, or pick a time " +
                "(a time that has already passed today means tomorrow). Tap \"Start block\".\n\n" +
                "The block starts right away and disappears when it ends. To end it early, tap it on the main screen " +
                "(this needs an emergency unlock).",

            "5. Emergency unlock" to
                "On the block screen, tap \"Emergency unlock\". Type the sentence shown on the screen (capital letters " +
                "don't matter, pasting is turned off). Wait for the countdown, then unlock that app for 5, 15 or 30 minutes.\n\n" +
                "Each mode and quick block sets its own countdown (30 seconds by default, up to 2 minutes). If two modes block the same " +
                "app, the longer countdown applies. Leaving the screen restarts the countdown.",

            "6. Good to know" to
                "• An app that's already open when a block starts closes within 30 seconds.\n" +
                "• Notifications still arrive. Only opening the app is blocked.\n" +
                "• Focus Lock doesn't read your screen and has no internet access.",
        )

        private val RU = listOf(
            "1. Включите блокировщик" to
                "Focus Lock нужен доступ к специальным возможностям, чтобы видеть, какое приложение открывается. " +
                "На главном экране нажмите «Turn on blocker» → Установленные приложения → Focus Lock → включите.\n\n" +
                "Если переключатель неактивен: Настройки → Приложения → Focus Lock → ⋮ (вверху справа) → " +
                "«Разрешить ограниченные настройки», затем попробуйте ещё раз.",

            "2. Режимы" to
                "Режим — это набор приложений, которые блокируются по расписанию. У каждого режима есть эмодзи, " +
                "название, приложения и дни недели. Режимы бывают двух видов:\n\n" +
                "• At set times (в заданное время): например, 💼 Работа, Пн–Пт 09:00–18:00. Если конец раньше начала " +
                "(22:00–07:00), режим закончится на следующее утро.\n\n" +
                "• Morning: after I wake up (утром, после пробуждения): блокирует на заданное время после первой разблокировки " +
                "телефона после выбранного часа (по умолчанию 04:00). Разблокировали телефон в 7:15, режим на 60 минут — " +
                "приложения заблокированы до 8:15. Выключение будильника не считается: для этого телефон не нужно " +
                "разблокировать. Разблокировка раньше выбранного часа (например, в 3 ночи) тоже не считается.",

            "3. Как выключить режим" to
                "У каждого режима есть переключатель. При выключении нужно выбрать:\n\n" +
                "• Just for today (только на сегодня): для праздников и выходных. Завтра режим включится сам.\n" +
                "• Until I turn it back on (пока не включу снова).\n\n" +
                "Если режим блокирует прямо сейчас, для выключения понадобится экстренная разблокировка. " +
                "Пока режим блокирует, его нельзя изменить.",

            "4. Быстрые блокировки" to
                "Работают как таймер. Нажмите «Block apps now», выберите приложения, затем 30 мин, 1 ч, 2 ч, 4 ч или " +
                "конкретное время (если это время сегодня уже прошло, блокировка продлится до завтра). " +
                "Нажмите «Start block».\n\n" +
                "Блокировка начинается сразу и исчезает сама, когда заканчивается. Чтобы закончить раньше, нажмите " +
                "на неё на главном экране (понадобится экстренная разблокировка).",

            "5. Экстренная разблокировка" to
                "На экране блокировки нажмите «Emergency unlock». Введите фразу, показанную на экране (регистр букв " +
                "не важен, вставка отключена). Дождитесь окончания отсчёта и разблокируйте приложение на 5, 15 или 30 минут.\n\n" +
                "Длительность отсчёта задаётся в каждом режиме и быстрой блокировке (по умолчанию 30 секунд, максимум 2 минуты). Если " +
                "приложение блокируют два режима, действует более долгий отсчёт. Если уйти с экрана, отсчёт начнётся заново.",

            "6. Полезно знать" to
                "• Приложение, открытое в момент начала блокировки, закроется в течение 30 секунд.\n" +
                "• Уведомления продолжают приходить. Блокируется только открытие приложения.\n" +
                "• Focus Lock не читает содержимое экрана и не имеет доступа к интернету.",
        )
    }
}
