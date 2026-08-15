package cn.happyoyster.opensdk.demo.sdk

import android.content.Context
import android.view.SurfaceView
import cn.happyoyster.opensdk.AdventureCommand
import cn.happyoyster.opensdk.EndTravelData
import cn.happyoyster.opensdk.HappyOyster
import cn.happyoyster.opensdk.HappyOysterListener
import cn.happyoyster.opensdk.LogLevel
import cn.happyoyster.opensdk.RewindTravelData
import cn.happyoyster.opensdk.SDKConfig
import cn.happyoyster.opensdk.SDKError
import cn.happyoyster.opensdk.SendInstructData
import cn.happyoyster.opensdk.StartTravelData
import cn.happyoyster.opensdk.TravelStateData

private const val TRAVEL_BUSY_ERROR_CODE = 103004

internal fun Throwable.isTravelBusyError(): Boolean =
    this is SDKError && code == TRAVEL_BUSY_ERROR_CODE

internal class DemoSdkSession(
    private val context: Context,
) {
    fun initialize(sdkApiHost: String, token: String) {
        HappyOyster.initialize(
            context.applicationContext,
            SDKConfig(
                apiHost = sdkApiHost,
                logLevel = LogLevel.DEBUG,
                logcatEnabled = true,
            ),
        )
        HappyOyster.updateToken(token)
    }

    fun updateToken(token: String) {
        HappyOyster.updateToken(token)
    }

    fun addListener(listener: HappyOysterListener) {
        HappyOyster.addListener(listener)
    }

    fun removeListener(listener: HappyOysterListener) {
        HappyOyster.removeListener(listener)
    }

    suspend fun startTravel(ticket: String): StartTravelData =
        HappyOyster.startTravel(ticket)

    suspend fun pauseTravel(): TravelStateData =
        HappyOyster.pauseTravel()

    suspend fun resumeTravel(): TravelStateData =
        HappyOyster.resumeTravel()

    suspend fun rewindTravel(rewindToSec: Double): RewindTravelData =
        HappyOyster.rewindTravel(rewindToSec)

    suspend fun sendInstruct(content: String): SendInstructData =
        HappyOyster.sendInstruct(content)

    suspend fun endTravel(): EndTravelData =
        HappyOyster.endTravel()

    fun attachVideo(): SurfaceView =
        HappyOyster.attachVideo()

    fun sendCommand(command: AdventureCommand) {
        HappyOyster.sendCommand(command)
    }
}
