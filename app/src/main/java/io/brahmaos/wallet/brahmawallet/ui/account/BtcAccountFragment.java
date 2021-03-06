package io.brahmaos.wallet.brahmawallet.ui.account;

import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import org.bitcoinj.kits.WalletAppKit;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import io.brahmaos.wallet.brahmawallet.R;
import io.brahmaos.wallet.brahmawallet.common.BrahmaConfig;
import io.brahmaos.wallet.brahmawallet.common.BrahmaConst;
import io.brahmaos.wallet.brahmawallet.common.IntentParam;
import io.brahmaos.wallet.brahmawallet.db.entity.AccountEntity;
import io.brahmaos.wallet.brahmawallet.event.EventTypeDef;
import io.brahmaos.wallet.brahmawallet.model.BitcoinDownloadProgress;
import io.brahmaos.wallet.brahmawallet.model.CryptoCurrency;
import io.brahmaos.wallet.brahmawallet.service.BtcAccountManager;
import io.brahmaos.wallet.brahmawallet.service.ImageManager;
import io.brahmaos.wallet.brahmawallet.service.MainService;
import io.brahmaos.wallet.brahmawallet.viewmodel.AccountViewModel;
import io.brahmaos.wallet.util.BLog;
import io.brahmaos.wallet.util.CommonUtil;
import io.brahmaos.wallet.util.RxEventBus;
import rx.Observable;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;

public class BtcAccountFragment extends Fragment {
    protected String tag() {
        return BtcAccountFragment.class.getName();
    }

    public static final String ARG_PAGE = "BITCOIN_ACCOUNT_PAGE";

    // UI references.
    private View parentView;
    private RecyclerView recyclerViewAccounts;
    private LinearLayout mLayoutCreateAccount;
    private Button mBtnCreateBtcAccount;
    private TextView mTvRestoreBtcAccount;

    private AccountViewModel mViewModel;
    private List<AccountEntity> accounts = new ArrayList<>();
    private List<CryptoCurrency> cryptoCurrencies = new ArrayList<>();
    private BitcoinDownloadProgress bitcoinDownloadProgress;
    private Observable<BitcoinDownloadProgress> btcSyncStatus;

    public static BtcAccountFragment newInstance(int page) {
        Bundle args = new Bundle();
        args.putInt(ARG_PAGE, page);
        BtcAccountFragment pageFragment = new BtcAccountFragment();
        pageFragment.setArguments(args);
        return pageFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // used to receive btc blocks sync progress
        btcSyncStatus = RxEventBus.get().register(EventTypeDef.BTC_ACCOUNT_SYNC, BitcoinDownloadProgress.class);
        btcSyncStatus.onBackpressureBuffer()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<BitcoinDownloadProgress>() {
                    @Override
                    public void onNext(BitcoinDownloadProgress progress) {
                        bitcoinDownloadProgress = progress;
                        if ((int)progress.getProgressPercentage() >= 100 ) {
                            bitcoinDownloadProgress.setDownloaded(true);
                        }
                        recyclerViewAccounts.getAdapter().notifyDataSetChanged();
                    }

                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                        Log.i(tag(), e.toString());
                    }
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        RxEventBus.get().unregister(EventTypeDef.BTC_ACCOUNT_SYNC, btcSyncStatus);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        BLog.d(tag(), "onCreateView");
        if (parentView == null) {
            parentView = LayoutInflater.from(getActivity()).inflate(R.layout.fragment_accounts_btc, container, false);
            initView();
        } else {
            ViewGroup parent = (ViewGroup)parentView.getParent();
            if (parent != null) {
                parent.removeView(parentView);
            }
        }
        return parentView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = ViewModelProviders.of(this).get(AccountViewModel.class);
        mViewModel.getAccounts().observe(this, accountEntities -> {
            accounts = new ArrayList<>();
            if (accountEntities != null && accountEntities.size() > 0) {
                for (AccountEntity accountEntity : accountEntities) {
                    if (accountEntity.getType() == BrahmaConst.BTC_ACCOUNT_TYPE) {
                        accounts.add(accountEntity);
                    }
                }
            }
            if (accounts == null || accounts.size() == 0) {
                BLog.e(tag(), "the account is null");
                mLayoutCreateAccount.setVisibility(View.VISIBLE);
                recyclerViewAccounts.setVisibility(View.GONE);
            } else {
                mLayoutCreateAccount.setVisibility(View.GONE);
                recyclerViewAccounts.setVisibility(View.VISIBLE);
                recyclerViewAccounts.getAdapter().notifyDataSetChanged();
            }
        });
    }

    private void initView() {
        mLayoutCreateAccount = parentView.findViewById(R.id.layout_new_account);
        recyclerViewAccounts = parentView.findViewById(R.id.accounts_recycler);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        recyclerViewAccounts.setLayoutManager(layoutManager);
        recyclerViewAccounts.setAdapter(new BtcAccountRecyclerAdapter());
        cryptoCurrencies = MainService.getInstance().getCryptoCurrencies();

        mBtnCreateBtcAccount = parentView.findViewById(R.id.btn_create_account);
        mBtnCreateBtcAccount.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), CreateBtcAccountActivity.class);
            startActivity(intent);
        });
        mTvRestoreBtcAccount = parentView.findViewById(R.id.tv_restore_account);
        mTvRestoreBtcAccount.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), RestoreBtcAccountActivity.class);
            startActivity(intent);
        });
    }

    /**
     * list item account
     */
    private class BtcAccountRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View rootView = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_account_btc, parent, false);
            rootView.setOnClickListener(v -> {
                int position = recyclerViewAccounts.getChildAdapterPosition(v);
                AccountEntity account = accounts.get(position);
                Intent intent = new Intent(getActivity(), BtcAccountAssetsActivity.class);
                intent.putExtra(IntentParam.PARAM_ACCOUNT_ID, account.getId());
                startActivity(intent);
            });
            return new BtcAccountRecyclerAdapter.ItemViewHolder(rootView);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof BtcAccountRecyclerAdapter.ItemViewHolder) {
                BtcAccountRecyclerAdapter.ItemViewHolder itemViewHolder = (BtcAccountRecyclerAdapter.ItemViewHolder) holder;
                AccountEntity accountEntity = accounts.get(position);
                setData(itemViewHolder, accountEntity);
            }
        }

        /**
         * set account view
         */
        private void setData(BtcAccountRecyclerAdapter.ItemViewHolder holder, final AccountEntity account) {
            if (account == null) {
                return ;
            }
            ImageManager.showAccountAvatar(getActivity(), holder.ivAccountAvatar, account);
            ImageManager.showAccountBackground(getActivity(), holder.ivAccountBg, account);
            if (BrahmaConfig.getInstance().getCurrencyUnit().equals(BrahmaConst.UNIT_PRICE_CNY)) {
                Glide.with(BtcAccountFragment.this)
                        .load(R.drawable.currency_cny_white)
                        .into(holder.ivCurrencyUnit);
            } else {
                Glide.with(BtcAccountFragment.this)
                        .load(R.drawable.currency_usd_white)
                        .into(holder.ivCurrencyUnit);
            }
            holder.tvAccountName.setText(account.getName());
            // get current receive address and balance through walletAppKit
            BigDecimal totalAssets = BigDecimal.ZERO;
            long balance = 0;
            WalletAppKit kit = BtcAccountManager.getInstance().getBtcWalletAppKit(account.getFilename());
            if ( kit != null && kit.wallet() != null) {
                holder.tvAccountAddress.setText(CommonUtil.generateSimpleAddress(kit.wallet().currentReceiveAddress().toBase58()));
                balance = kit.wallet().getBalance().value;
                if (balance > 0) {
                    for (CryptoCurrency currency : cryptoCurrencies) {
                        if (currency.getName().toLowerCase().equals(BrahmaConst.BITCOIN)) {
                            double tokenPrice = currency.getPriceCny();
                            if (BrahmaConfig.getInstance().getCurrencyUnit().equals(BrahmaConst.UNIT_PRICE_USD)) {
                                tokenPrice = currency.getPriceUsd();
                            }
                            BigDecimal tokenValue = new BigDecimal(tokenPrice).multiply(CommonUtil.convertBTCFromSatoshi(new BigInteger(String.valueOf(balance))));
                            totalAssets = totalAssets.add(tokenValue);
                            break;
                        }
                    }
                }
            } else {
                BtcAccountManager.getInstance().initExistsWalletAppKit(account);
                holder.tvAccountAddress.setText(CommonUtil.generateSimpleAddress(account.getAddress()));
            }
            if (bitcoinDownloadProgress != null && !bitcoinDownloadProgress.isDownloaded()) {
                holder.ivSyncStatus.setVisibility(View.VISIBLE);
                Animation rotate = AnimationUtils.loadAnimation(getContext(), R.anim.sync_rotate);
                if (rotate != null) {
                    holder.ivSyncStatus.startAnimation(rotate);
                }
            } else {
                holder.ivSyncStatus.setVisibility(View.GONE);
            }

            holder.tvAccountBalance.setText(String.valueOf(CommonUtil.convertUnit(BrahmaConst.BITCOIN, new BigInteger(String.valueOf(balance)))));
            holder.tvTotalAssets.setText(String.valueOf(totalAssets.setScale(2, BigDecimal.ROUND_HALF_UP)));
        }

        @Override
        public int getItemCount() {
            return accounts.size();
        }

        class ItemViewHolder extends RecyclerView.ViewHolder {

            ImageView ivAccountBg;
            ImageView ivAccountAvatar;
            TextView tvAccountName;
            TextView tvAccountAddress;
            TextView tvAccountBalance;
            TextView tvTotalAssetsDesc;
            TextView tvTotalAssets;
            ImageView ivCurrencyUnit;
            ImageView ivSyncStatus;

            ItemViewHolder(View itemView) {
                super(itemView);
                ivAccountBg = itemView.findViewById(R.id.iv_account_bg);
                ivAccountAvatar = itemView.findViewById(R.id.iv_account_avatar);
                tvAccountName = itemView.findViewById(R.id.tv_account_name);
                tvAccountAddress = itemView.findViewById(R.id.tv_account_address);
                tvTotalAssetsDesc = itemView.findViewById(R.id.tv_total_assets_desc);
                tvAccountBalance = itemView.findViewById(R.id.tv_account_balance);
                tvTotalAssets = itemView.findViewById(R.id.tv_total_assets);
                ivCurrencyUnit = itemView.findViewById(R.id.iv_currency_amount);
                ivSyncStatus = itemView.findViewById(R.id.iv_btc_sync_status);
            }
        }
    }
}
