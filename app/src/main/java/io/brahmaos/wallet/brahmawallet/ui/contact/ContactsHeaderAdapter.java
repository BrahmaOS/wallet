package io.brahmaos.wallet.brahmawallet.ui.contact;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import io.brahmaos.wallet.brahmawallet.R;
import io.brahmaos.wallet.brahmawallet.db.entity.AccountEntity;
import io.brahmaos.wallet.brahmawallet.service.ImageManager;
import me.yokeyword.indexablerv.IndexableHeaderAdapter;


public class ContactsHeaderAdapter extends IndexableHeaderAdapter<AccountEntity> {

    public static final String TAG = ContactsHeaderAdapter.class.getName();

    private LayoutInflater mInflater;
    private Context ctx;

    ContactsHeaderAdapter(Context context, String index, String indexTitle, List<AccountEntity> datas) {
        super(index, indexTitle, datas);
        this.ctx = context;
        mInflater = LayoutInflater.from(context);
    }

    @Override
    public int getItemViewType() {
        return 0;
    }

    @Override
    public RecyclerView.ViewHolder onCreateContentViewHolder(ViewGroup parent) {
        View view = mInflater.inflate(R.layout.item_contact_my_account, parent, false);
        return new ContentVH(view);
    }

    @Override
    public void onBindContentViewHolder(RecyclerView.ViewHolder holder, AccountEntity entity) {

        Log.i(TAG, "entity - " + entity);
        ContentVH vh = (ContentVH) holder;
        vh.tvName.setText(entity.getName());
        vh.tvAddress.setText(entity.getAddress());
        ImageManager.showAccountAvatar(ctx, vh.ivAvatar, entity);
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