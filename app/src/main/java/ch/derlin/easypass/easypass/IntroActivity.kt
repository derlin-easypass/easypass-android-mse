package ch.derlin.easypass.easypass

import android.graphics.Color
import android.os.Bundle
import android.support.v4.app.Fragment
import ch.derlin.easypass.easypass.helper.MiscUtils.attrColor
import ch.derlin.easypass.easypass.helper.Preferences
import com.github.paolorotolo.appintro.AppIntro
import com.github.paolorotolo.appintro.AppIntroFragment
import com.github.paolorotolo.appintro.model.SliderPage


class IntroActivity : AppIntro(){


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addSlide(
                "Welcome!",
                "One safe vault for all your credentials, anywhere, anytime.",
                attrColor(R.attr.colorPrimary),
                R.mipmap.ic_launcher
        )
        addSlide(
                "Synchronization",
                "Store your credentials in Dropbox for automatic backup, history and synchronization on all your devices.",
                getColor(R.color.colorDropbox),//Color.parseColor("#2F5EA9"),
                R.drawable.ic_dropbox
        )
        addSlide(
                "Security",
                "Everything is encrypted using AES-CBC-128 for high security. Passwords are cached using your fingerprints for quick access.",
                Color.parseColor("#934DC6"),
                R.drawable.ic_fingerprint
        )
        addSlide(
                "Integration",
                "Whatever happens, you can always use OpenSSL or another tool from the EasyPass suit" +
                        "to get your credentials back!",
                Color.parseColor("#E97C29"),
                R.drawable.puzzle
        )
        addSlide(
                "Let's do it!",
                "Start enjoying EasyPass now.",
                attrColor(R.attr.colorPrimary),
                R.drawable.octo
        )

        setNavBarColor(R.color.blacky)
        setColorTransitionsEnabled(true)

    }

    private fun addSlide(title: String, description: String, color: Int, drawable: Int, fgColor: Int = -1) {
        val fg = if(fgColor == -1) getColor(R.color.whity) else fgColor

        val sliderPage = SliderPage()
        sliderPage.title = title
        sliderPage.description = description
        sliderPage.imageDrawable = drawable
        sliderPage.bgColor = Color.TRANSPARENT
        sliderPage.titleColor = fg
        sliderPage.descColor = fg
        sliderPage.bgColor = color
        addSlide(AppIntroFragment.newInstance(sliderPage))
    }

    private fun exitIntro() {
        Preferences().introDone = true
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
        val INTENT_INTRO = 5553
    }

}
