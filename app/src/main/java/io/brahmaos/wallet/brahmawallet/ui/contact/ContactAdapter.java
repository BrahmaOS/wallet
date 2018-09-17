package io.brahmaos.wallet.brahmawallet.ui.contact;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import io.brahmaos.wallet.brahmawallet.R;
import io.brahmaos.wallet.brahmawallet.db.entity.ContactEntity;
import me.yokeyword.indexablerv.IndexableAdapter;


public class ContactAdapter extends IndexableAdapter<ContactEntity> {
    private LayoutInflater mInflater;

    ContactAdapter(Context context) {
        mInflater = LayoutInflater.from(context);
    }

    @Override
    public RecyclerView.ViewHolder onCreateTitleViewHolder(ViewGroup parent) {
        View view = mInflater.inflate(R.layout.item_index_contact, parent, false);
        return new IndexVH(view);
    }

    @Override
    public RecyclerView.ViewHolder onCreateContentViewHolder(ViewGroup parent) {
        View view = mInflater.inflate(R.layout.item_contact, parent, false);
        return new ContentVH(view);
    }

    @Override
    public void onBindTitleViewHolder(RecyclerView.ViewHolder holder, String indexTitle) {
        IndexVH vh = (IndexVH) holder;
        vh.tv.setText(indexTitle);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindContentViewHolder(RecyclerView.ViewHolder holder, ContactEntity entity) {
        ContentVH vh = (ContentVH) holder;
        vh.tvName.setText(entity.getName() + " " + entity.getFamilyName());
        vh.tvAddress.setText(entity.getAddress());
    }

    private class IndexVH extends RecyclerView.ViewHolder {
        TextView tv;

        IndexVH(View itemView) {
            super(itemView);
            tv = itemView.findViewById(R.id.tv_index);
        }
    }

    private class ContentVH extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvAddress;
        ImageView ivAvatar;

        ContentVH(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_name);
            tvAddress = itemView.findViewById(R.id.tv_address);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
        }
    }
}
