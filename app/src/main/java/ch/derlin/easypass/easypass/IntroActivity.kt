package ch.derlin.easypass.easypass

import android.animation.ArgbEvaluator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.view.ViewPager
import ch.derlin.easypass.easypass.helper.Preferences
import com.github.paolorotolo.appintro.AppIntro
import com.github.paolorotolo.appintro.AppIntroFragment
import com.github.paolorotolo.appintro.model.SliderPage


class IntroActivity : AppIntro(), ViewPager.OnPageChangeListener {

    lateinit var mPager: ViewPager
    var colors = ArrayList<Int>()
    val argbEvaluator = ArgbEvaluator()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mPager = getPager()
        mPager.addOnPageChangeListener(this)

        addSlide(
                "Welcome!",
                "One safe vault for all your credentials, anywhere, anytime.",
                getColor(R.color.colorGreeny),
                R.mipmap.ic_launcher
        )
        addSlide(
                "Synchronization",
                "Store your credentials in Dropbox for automatic backup, history and synchronization on all your devices.",
                Color.parseColor("#BBDEF0"),
                R.drawable.ic_dropbox
        )
        addSlide(
                "Security",
                "Everything is encrypted using AES-CBC-128 for high security. Passwords are cached using your fingerprints for quick access.",
                Color.parseColor("#CBB4DA"),
                R.drawable.ic_fingerprint
        )
        addSlide(
                "Integration",
                "Whatever happens, you can always use OpenSSL or another tool from the EasyPass suit" +
                        "to get your credentials back!",
                getColor(R.color.colorYellowy),
                R.drawable.ic_sync
        )
        addSlide(
                "Let's do it!",
                "Start enjoying EasyPass now.",
                Color.parseColor("#FF8634"),
                R.drawable.ic_add
        )
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
        addSlide(AppIntroFragment.newInstance(sliderPage))

        colors.add(color)
    }

    private fun launchApp() {
        Preferences().introDone = true
        // service up and running, start the actual app
        val intent = Intent(this, StartActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME
        startActivity(intent)
        this.finish()
    }

    override fun onSkipPressed(currentFragment: Fragment) {
        super.onSkipPressed(currentFragment)
        launchApp()
    }

    override fun onDonePressed(currentFragment: Fragment) {
        super.onDonePressed(currentFragment)
        launchApp()
    }

    // -------- color transitions (see https://github.com/apl-devs/AppIntro/issues/80)
    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        if (position < mPager.getAdapter().getCount() - 1 && position < colors.size - 1) {
            mPager.setBackgroundColor(argbEvaluator.evaluate(positionOffset, colors[position], colors[position + 1]) as Int)
        } else {
            mPager.setBackgroundColor(colors[colors.size - 1])
        }
    }

    override fun onPageSelected(position: Int) {

    }

    override fun onPageScrollStateChanged(state: Int) {

    }

}
