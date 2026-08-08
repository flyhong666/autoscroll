package cn.ggdoc.autoscroll.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cn.ggdoc.autoscroll.config.CustomGestureStep
import cn.ggdoc.autoscroll.databinding.ItemGestureStepBinding

class GestureStepAdapter(
    private val onUp: (Int) -> Unit,
    private val onDown: (Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<GestureStepAdapter.VH>() {

    private val items = mutableListOf<CustomGestureStep>()

    fun submit(list: List<CustomGestureStep>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun getItem(pos: Int): CustomGestureStep = items[pos]

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemGestureStepBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val step = items[position]
        holder.binding.tvStepIndex.text = (position + 1).toString()
        holder.binding.tvStepGesture.text = step.gestureName()
        val detail = if (step.isWaitOnly()) {
            "等待 ${step.waitSec} 秒"
        } else {
            "位置 ${step.xPct}%,${step.yPct}%" + (
                if (step.isSwipe()) " · 距离 ${step.distPct}%" else ""
            ) + " · 等待 ${step.waitSec} 秒"
        }
        holder.binding.tvStepDetail.text = detail
        holder.binding.btnUp.isEnabled = position > 0
        holder.binding.btnDown.isEnabled = position < items.size - 1
        holder.binding.btnUp.setOnClickListener { onUp(position) }
        holder.binding.btnDown.setOnClickListener { onDown(position) }
        holder.binding.btnDelete.setOnClickListener { onDelete(position) }
    }

    class VH(val binding: ItemGestureStepBinding) : RecyclerView.ViewHolder(binding.root)
}
