package ch.derlin.easypass

import android.os.Bundle
import androidx.fragment.app.Fragment
import ch.derlin.easypass.easypass.R
import ch.derlin.easypass.helper.Preferences
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment


class IntroActivity : AppIntro() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addSlide(
            "Welcome!",
            "One safe vault for all your credentials, anywhere, anytime.",
            R.color.colorDarkGrey,
            R.drawable.splashscreen
        )
        addSlide(
            "Synchronization",
            "Store your credentials in Dropbox for automatic backup, history and synchronization on all your devices.",
            R.color.introBlue,
            R.drawable.ic_dropbox
        )
        addSlide(
            "Security",
            "Everything is encrypted using AES-CBC-128 for high security. Passwords are cached using your fingerprints for quick access.",
            R.color.colorReddy,
            R.drawable.ic_fingerprint
        )
        addSlide(
            "Integration",
            "Whatever happens, you can always use OpenSSL or another tool from the EasyPass suit" +
                    "to get your credentials back!",
            R.color.introOrange,
            R.drawable.puzzle
        )
        addSlide(
            "Let's do it!",
            "Start enjoying EasyPass now.",
            R.color.colorDarkGrey,
            R.drawable.splashscreen
        )

        setNavBarColorRes(R.color.blacky)
        isColorTransitionsEnabled = true

    }

    private fun addSlide(
        title: String,
        description: String,
        colorRes: Int,
        drawable: Int,
        fgColorRes: Int = R.color.whity
    ) {
        addSlide(
            AppIntroFragment.createInstance(
                title = title,
                description = description,
                imageDrawable = drawable,
                backgroundColorRes = colorRes,
                titleColorRes = fgColorRes,
                descriptionColorRes = fgColorRes
            )
        )
    }

    private fun exitIntro() {
        Preferences.introDone = true
        this.finish()
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        exitIntro()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        exitIntro()
    }

}
