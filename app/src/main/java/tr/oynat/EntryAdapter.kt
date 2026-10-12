package tr.oynat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EntryAdapter(
    private val subtitleFor: (Entry) -> String,
    private val onClick: (Entry) -> Unit,
    private val onLongClick: (Entry) -> Unit
) : RecyclerView.Adapter<EntryAdapter.VH>() {

    var items: List<Entry> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.title)
        val sub: TextView = v.findViewById(R.id.sub)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_entry, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        holder.title.text = e.title
        holder.sub.text = subtitleFor(e)
        holder.itemView.setOnClickListener { onClick(e) }
        holder.itemView.setOnLongClickListener { onLongClick(e); true }
    }
}
