package ch.derlin.easypass

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import ch.derlin.easypass.easypass.R
import ch.derlin.easypass.helper.MiscUtils.attrColor
import ch.derlin.easypass.helper.Preferences
import com.github.paolorotolo.appintro.AppIntro
import com.github.paolorotolo.appintro.AppIntroFragment
import com.github.paolorotolo.appintro.model.SliderPage


class IntroActivity : AppIntro() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addSlide(
                "Welcome!",
                "One safe vault for all your credentials, anywhere, anytime.",
                attrColor(R.attr.colorPrimary),
                R.drawable.splashscreen
        )
        addSlide(
                "Synchronization",
                "Store your credentials in Dropbox for automatic backup, history and synchronization on all your devices.",
                Color.parseColor("#0093D8"),
                R.drawable.ic_dropbox
        )
        addSlide(
                "Security",
                "Everything is encrypted using AES-CBC-128 for high security. Passwords are cached using your fingerprints for quick access.",
                Color.parseColor("#EA4865"),
                R.drawable.ic_fingerprint
        )
        addSlide(
                "Integration",
                "Whatever happens, you can always use OpenSSL or another tool from the EasyPass suit" +
                        "to get your credentials back!",
                Color.parseColor("#F18805"),
                R.drawable.puzzle
        )
        addSlide(
                "Let's do it!",
                "Start enjoying EasyPass now.",
                attrColor(R.attr.colorPrimary),
                R.drawable.splashscreen
        )

        setNavBarColor(R.color.blacky)
        setColorTransitionsEnabled(true)

    }

    private fun addSlide(title: String, description: String, color: Int, drawable: Int, fgColor: Int = -1) {
        val fg = if (fgColor == -1) getColor(R.color.whity) else fgColor

        SliderPage().let { sliderPage ->
            sliderPage.title = title
            sliderPage.description = description
            sliderPage.imageDrawable = drawable
            sliderPage.bgColor = Color.TRANSPARENT
            sliderPage.titleColor = fg
            sliderPage.descColor = fg
            sliderPage.bgColor = color
            addSlide(AppIntroFragment.newInstance(sliderPage))
        }
    }

    private fun exitIntro() {
        Preferences.introDone = true
        this.finish()
    }

    override fun onSkipPressed(currentFragment: Fragment) {
        super.onSkipPressed(currentFragment)
        exitIntro()
    }

    override fun onDonePressed(currentFragment: Fragment) {
        super.onDonePressed(currentFragment)
        exitIntro()
    }

    companion object {
        const val INTENT_INTRO = 5553
    }

}
