package cn.ggdoc.autoscroll.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.config.AppConfig
import cn.ggdoc.autoscroll.config.SceneConfig
import cn.ggdoc.autoscroll.service.AutoScrollAccessibilityService

/**
 * 场景页：6 大场景卡片选择
 */
class SceneFragment : Fragment() {

    private lateinit var rvScenes: RecyclerView
    private var currentSelectedId: String = AppConfig.SCENE_SHORT_VIDEO

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_scene, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvScenes = view.findViewById(R.id.rvScenes)
        rvScenes.layoutManager = LinearLayoutManager(requireContext())
        currentSelectedId = AppConfig.getCurrentScene(requireContext())
        bindAdapter()
    }

    private fun bindAdapter() {
        val scenes = SceneConfig.getAllScenes()
        rvScenes.adapter = SceneAdapter(scenes, currentSelectedId) { scene ->
            currentSelectedId = scene.id
            AppConfig.setCurrentScene(requireContext(), scene.id)
            AutoScrollAccessibilityService.instance?.loadConfigFromPrefs()
            Toast.makeText(requireContext(), R.string.toast_scene_changed, Toast.LENGTH_SHORT).show()
            bindAdapter()
        }
    }
}
