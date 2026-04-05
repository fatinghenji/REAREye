package hk.uwu.reareye.application

import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
import hk.uwu.reareye.repository.contributor.ContributorRepository


class DefaultApplication : ModuleApplication() {

    override fun onCreate() {
        super.onCreate()
        ContributorRepository.preload()
    }
}
