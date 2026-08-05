package cn.ggdoc.autoscroll.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cn.ggdoc.autoscroll.R
import cn.ggdoc.autoscroll.recorder.ScriptStore
import com.google.android.material.button.MaterialButton

/**
 * 脚本列表适配器
 */
class ScriptAdapter(
    private var items: List<ScriptStore.Entry>,
    private val onPlay: (ScriptStore.Entry) -> Unit,
    private val onMore: (ScriptStore.Entry, View) -> Unit
) : RecyclerView.Adapter<ScriptAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvScriptName)
        val tvMeta: TextView = view.findViewById(R.id.tvScriptMeta)
        val btnPlay: MaterialButton = view.findViewById(R.id.btnScriptPlay)
        val btnMore: ImageView = view.findViewById(R.id.btnScriptMore)
    }

    fun submit(list: List<ScriptStore.Entry>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_script, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context
        holder.tvName.text = item.name
        holder.tvMeta.text = ctx.getString(
            R.string.script_meta,
            item.actionCount,
            (item.estimatedMs / 1000f).toInt(),
            ScriptStore.formatTime(item.createdAt)
        )
        holder.btnPlay.setOnClickListener { onPlay(item) }
        holder.itemView.setOnClickListener { onPlay(item) }
        holder.btnMore.setOnClickListener { onMore(item, holder.btnMore) }
    }
}
