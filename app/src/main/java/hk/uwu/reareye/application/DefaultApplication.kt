package hk.uwu.reareye.application

import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
import hk.uwu.reareye.repository.contributor.ContributorRepository
import hk.uwu.reareye.utils.effect.FrameSampler


class DefaultApplication : ModuleApplication() {

    override fun onCreate() {
        super.onCreate()
        runCatching {
            FrameSampler.prepare(applicationContext)
        }
        ContributorRepository.preload()
    }
}
