package cn.happyoyster.opensdk.demo.app

import android.view.SurfaceView
import androidx.annotation.StringRes
import cn.happyoyster.opensdk.StartTravelData
import cn.happyoyster.opensdk.demo.gateway.CameraView
import cn.happyoyster.opensdk.demo.gateway.DemoWorld
import cn.happyoyster.opensdk.demo.gateway.StoryCreationModel
import cn.happyoyster.opensdk.demo.gateway.StoryResolution
import cn.happyoyster.opensdk.demo.gateway.WanderUploadMode
import cn.happyoyster.opensdk.demo.gateway.WorldKind

internal enum class DemoTab {
    Create,
    Play,
    History,
    Profile,
}

internal data class ActiveTravel(
    val world: DemoWorld,
    val data: StartTravelData,
    val videoView: SurfaceView,
)

/** Inputs of the Create tab, kept together so the form travels as one value. */
internal data class CreateWorldForm(
    val worldKind: WorldKind = WorldKind.Wander,
    val wanderUploadMode: WanderUploadMode = WanderUploadMode.FirstFrame,
    val cameraView: CameraView = CameraView.FirstPerson,
    val resolution: StoryResolution = StoryResolution.P720,
    val prompt: String = "A floating oyster city above a calm ocean",
    val firstFrameImageUrl: String = "",
    val scenePrompt: String = "",
    val sceneImageUrl: String = "",
    val rolePrompt: String = "",
    val roleImageUrl: String = "",
    val storyCreationModel: StoryCreationModel = StoryCreationModel.Simple,
    val scriptListPresetId: String? = null,
    val scriptListSynopsis: String = "",
    val scriptListDraft: String = "",
) {
    /** Drops ScriptList inputs (including the preset-provided first-frame URL) when the creation mode changes. */
    fun clearScriptListFields(): CreateWorldForm =
        copy(
            firstFrameImageUrl = "",
            scriptListPresetId = null,
            scriptListSynopsis = "",
            scriptListDraft = "",
        )
}

internal sealed interface SdkDemoError {
    data class Action(
        @StringRes val actionResId: Int,
        val detail: String,
    ) : SdkDemoError

    data class Sdk(
        val code: Int,
        val detail: String,
    ) : SdkDemoError
}
