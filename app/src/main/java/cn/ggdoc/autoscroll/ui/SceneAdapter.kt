package cn.ggdoc.autoscroll.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.config.SceneConfig

/**
 * 场景列表适配器
 */
class SceneAdapter(
    private val scenes: List<SceneConfig.Scene>,
    private val selectedId: String,
    private val onSelect: (SceneConfig.Scene) -> Unit
) : RecyclerView.Adapter<SceneAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.ivSceneIcon)
        val tvName: TextView = itemView.findViewById(R.id.tvSceneName)
        val tvDesc: TextView = itemView.findViewById(R.id.tvSceneDesc)
        val tvTags: TextView = itemView.findViewById(R.id.tvSceneTags)
        val ivSelected: ImageView = itemView.findViewById(R.id.ivSelected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_scene, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val scene = scenes[position]
        val ctx = holder.itemView.context
        holder.ivIcon.setImageResource(scene.iconRes)
        holder.tvName.text = ctx.getString(scene.nameRes)
        holder.tvDesc.text = ctx.getString(scene.descRes)
        holder.tvTags.text = buildString {
            append("推荐间隔 ").append(scene.recommendMinInterval).append("-").append(scene.recommendMaxInterval).append("s")
            if (scene.supportAutoLike) append(" · 支持点赞")
        }
        holder.ivSelected.visibility = if (scene.id == selectedId) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onSelect(scene) }
    }

    override fun getItemCount(): Int = scenes.size
}
