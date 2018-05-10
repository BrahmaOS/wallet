package io.brahmaos.wallet.brahmawallet.ui.transfer;

import android.app.Activity;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.BottomSheetDialog;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.web3j.crypto.CipherException;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3jService;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.brahmaos.wallet.brahmawallet.R;
import io.brahmaos.wallet.brahmawallet.common.BrahmaConst;
import io.brahmaos.wallet.brahmawallet.common.IntentParam;
import io.brahmaos.wallet.brahmawallet.db.entity.AccountEntity;
import io.brahmaos.wallet.brahmawallet.db.entity.TokenEntity;
import io.brahmaos.wallet.brahmawallet.model.AccountAssets;
import io.brahmaos.wallet.brahmawallet.service.BrahmaWeb3jService;
import io.brahmaos.wallet.brahmawallet.service.ImageManager;
import io.brahmaos.wallet.brahmawallet.service.MainService;
import io.brahmaos.wallet.brahmawallet.ui.account.ImportAccountActivity;
import io.brahmaos.wallet.brahmawallet.ui.base.BaseActivity;
import io.brahmaos.wallet.brahmawallet.view.CustomStatusView;
import io.brahmaos.wallet.brahmawallet.viewmodel.AccountViewModel;
import io.brahmaos.wallet.util.BLog;
import io.brahmaos.wallet.util.CommonUtil;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class TransferActivity extends BaseActivity {

    @Override
    protected String tag() {
        return TransferActivity.class.getName();
    }

    // UI references.
    @BindView(R.id.iv_account_avatar)
    ImageView ivAccountAvatar;
    @BindView(R.id.tv_account_name)
    TextView tvAccountName;
    @BindView(R.id.tv_account_address)
    TextView tvAccountAddress;
    @BindView(R.id.tv_change_account)
    TextView tvChangeAccount;
    @BindView(R.id.btn_show_transfer_info)
    Button btnShowTransfer;
    @BindView(R.id.et_receiver_address)
    EditText etReceiverAddress;
    @BindView(R.id.et_amount)
    EditText etAmount;
    @BindView(R.id.et_remark)
    EditText etRemark;

    private AccountEntity mAccount;
    private TokenEntity mToken;
    private AccountViewModel mViewModel;
    private List<AccountEntity> mAccounts = new ArrayList<>();
    private List<AccountAssets> mAccountAssetsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);
        ButterKnife.bind(this);
        showNavBackBtn();
        mAccount = (AccountEntity) getIntent().getSerializableExtra(IntentParam.PARAM_ACCOUNT_INFO);
        mToken = (TokenEntity) getIntent().getSerializableExtra(IntentParam.PARAM_TOKEN_INFO);

        if (mToken == null) {
            finish();
        }
        initView();
    }

    private void initView() {
        String tokenShortName = mToken.getShortName();
        setToolbarTitle(getString(R.string.action_transfer) + getString(R.string.blank_space)
                + tokenShortName);
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            ActionBar ab = getSupportActionBar();
            if (ab != null) {
                ab.setTitle(getString(R.string.action_transfer) + getString(R.string.blank_space)
                        + tokenShortName);
            }
        }

        mAccountAssetsList = MainService.getInstance().getAccountAssetsList();

        mViewModel = ViewModelProviders.of(this).get(AccountViewModel.class);
        mViewModel.getAccounts().observe(this, accountEntities -> {
            mAccounts = accountEntities;
            if ((mAccount == null || mAccount.getAddress().length() == 0) &&
                    accountEntities != null) {
                mAccount = mAccounts.get(0);
            }
            if (mAccounts != null && mAccounts.size() > 1) {
                tvChangeAccount.setVisibility(View.VISIBLE);
            }
            showAccountInfo(mAccount);
        });
        tvChangeAccount.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_account_list, null);
            builder.setView(dialogView);
            builder.setCancelable(true);
            final AlertDialog alertDialog = builder.create();
            alertDialog.show();

            LinearLayout layoutBabyList = dialogView.findViewById(R.id.layout_accounts);

            for (final AccountEntity account : mAccounts) {
                final AccountItemView accountItemView = new AccountItemView();
                accountItemView.layoutAccountItem = LayoutInflater.from(this).inflate(R.layout.dialog_list_item_account, null);
                accountItemView.ivAccountAvatar = accountItemView.layoutAccountItem.findViewById(R.id.iv_account_avatar);
                accountItemView.tvAccountName = accountItemView.layoutAccountItem.findViewById(R.id.tv_account_name);
                accountItemView.tvAccountAddress = accountItemView.layoutAccountItem.findViewById(R.id.tv_account_address);
                accountItemView.layoutDivider = accountItemView.layoutAccountItem.findViewById(R.id.layout_divider);

                accountItemView.tvAccountName.setText(account.getName());
                ImageManager.showAccountAvatar(this, accountItemView.ivAccountAvatar, account);
                accountItemView.tvAccountAddress.setText(CommonUtil.generateSimpleAddress(account.getAddress()));

                accountItemView.layoutAccountItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.cancel();
                        mAccount = account;
                        showAccountInfo(account);
                    }
                });

                if (mAccounts.indexOf(account) == mAccounts.size() - 1) {
                    accountItemView.layoutDivider.setVisibility(View.GONE);
                }

                layoutBabyList.addView(accountItemView.layoutAccountItem);
            }
        });

        btnShowTransfer.setOnClickListener(v -> {
            showTransferInfo();
        });
    }

    private class AccountItemView {
        View layoutAccountItem;
        ImageView ivAccountAvatar;
        TextView tvAccountName;
        TextView tvAccountAddress;
        LinearLayout layoutDivider;
    }

    private void showAccountInfo(AccountEntity account) {
        ImageManager.showAccountAvatar(this, ivAccountAvatar, account);
        tvAccountName.setText(account.getName());
        tvAccountAddress.setText(CommonUtil.generateSimpleAddress(account.getAddress()));
    }

    private void showTransferInfo() {
        String receiverAddress = etReceiverAddress.getText().toString().trim();
        String transferAmount = etAmount.getText().toString().trim();
        String remark = etRemark.getText().toString().trim();
        BigDecimal amount = BigDecimal.ZERO;

        String tips = "";
        boolean cancel = false;
        if (!BrahmaWeb3jService.getInstance().isValidAddress(receiverAddress)) {
            tips = getString(R.string.tip_error_address);
            cancel = true;
        }
        if (!cancel && receiverAddress.equals(mAccount.getAddress())) {
            tips = getString(R.string.tip_same_address);
            cancel = true;
        }

        BigInteger totalBalance = BigInteger.ZERO;
        BigInteger ethTotalBalance = BigInteger.ZERO;
        for (AccountAssets assets : mAccountAssetsList) {
            if (assets.getAccountEntity().getAddress().equals(mAccount.getAddress())) {
                if (assets.getTokenEntity().getAddress().equals(mToken.getAddress())) {
                    totalBalance = assets.getBalance();
                }
                if (assets.getTokenEntity().getName().toLowerCase().equals(BrahmaConst.ETHEREUM.toLowerCase())) {
                    ethTotalBalance = assets.getBalance();
                }
            }
        }

        if (!cancel) {
            if (transferAmount.length() < 1) {
                tips = getString(R.string.tip_invalid_amount);
                cancel = true;
            } else {
                amount = new BigDecimal(transferAmount);
            }
        }

        if (!cancel && (amount.compareTo(BigDecimal.ZERO) <= 0 ||
                CommonUtil.convertFormWeiToEther(amount).compareTo(totalBalance) > 0)) {
            tips = getString(R.string.tip_invalid_amount);
            cancel = true;
        }

        if (!cancel && ethTotalBalance.compareTo(BigInteger.ZERO) <= 0) {
            tips = getString(R.string.tip_insufficient_eth);
            cancel = true;
        }

        // check the ether is enough
        if (!cancel && mToken.getName().toLowerCase().equals(BrahmaConst.ETHEREUM)) {
            totalBalance = ethTotalBalance;
            if (CommonUtil.convertFormWeiToEther(amount.add(BrahmaConst.DEFAULT_FEE)).compareTo(totalBalance) > 0) {
                tips = getString(R.string.tip_insufficient_eth);
                cancel = true;
            }
        }

        if (cancel) {
            // dialog show tip
            AlertDialog dialogTip = new AlertDialog.Builder(this)
                    .setMessage(tips)
                    .setNegativeButton(R.string.ok, (dialog, which) -> dialog.cancel())
                    .create();
            dialogTip.show();
            return;
        }

        final BottomSheetDialog transferInfoDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_dialog_transfer_info, null);
        transferInfoDialog.setContentView(view);
        transferInfoDialog.setCancelable(false);
        transferInfoDialog.show();

        ImageView ivCloseDialog = view.findViewById(R.id.iv_close_dialog);
        ivCloseDialog.setOnClickListener(v -> {
            transferInfoDialog.cancel();
        });

        TextView tvDialogPayToAddress = view.findViewById(R.id.tv_pay_to_address);
        tvDialogPayToAddress.setText(receiverAddress);

        TextView tvDialogPayByAddress = view.findViewById(R.id.tv_pay_by_address);
        tvDialogPayByAddress.setText(CommonUtil.generateSimpleAddress(mAccount.getAddress()));

        TextView tvTransferAmount = view.findViewById(R.id.tv_dialog_transfer_amount);
        tvTransferAmount.setText(String.valueOf(amount));

        TextView tvTransferToken = view.findViewById(R.id.tv_dialog_transfer_token);
        tvTransferToken.setText(mToken.getShortName());

        LinearLayout layoutTransferInfo = view.findViewById(R.id.layout_transfer_info);
        FrameLayout layoutTransferStatus = view.findViewById(R.id.layout_transfer_status);
        CustomStatusView customStatusView = view.findViewById(R.id.as_status);

        Button confirmBtn = view.findViewById(R.id.btn_commit_transfer);
        BigDecimal finalAmount = amount;
        confirmBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final View dialogView = getLayoutInflater().inflate(R.layout.dialog_account_password, null);

                AlertDialog passwordDialog = new AlertDialog.Builder(TransferActivity.this)
                        .setView(dialogView)
                        .setCancelable(false)
                        .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel())
                        .setPositiveButton(R.string.ok, (dialog, which) -> {
                            dialog.cancel();
                            // show transfer progress
                            layoutTransferStatus.setVisibility(View.VISIBLE);
                            customStatusView.loadLoading();
                            String password = ((EditText) dialogView.findViewById(R.id.et_password)).getText().toString();
                            mViewModel.sendTransfer(mAccount, mToken, password, receiverAddress, finalAmount)
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(new Observer<Boolean>() {
                                        @Override
                                        public void onNext(Boolean flag) {
                                            if (flag) {
                                                BLog.i(tag(), "the transfer success");
                                                customStatusView.loadSuccess();
                                                new Handler().postDelayed(new Runnable() {
                                                    public void run() {
                                                        transferInfoDialog.cancel();
                                                        Intent intent = new Intent();
                                                        setResult(Activity.RESULT_OK, intent);
                                                        finish();
                                                    }
                                                }, 1200);
                                            }
                                        }

                                        @Override
                                        public void onError(Throwable e) {
                                            e.printStackTrace();
                                            customStatusView.loadFailure();
                                            new Handler().postDelayed(new Runnable() {
                                                public void run() {
                                                    layoutTransferStatus.setVisibility(View.GONE);
                                                    int resId = R.string.tip_error_transfer;
                                                    if (e instanceof CipherException) {
                                                        resId = R.string.tip_error_password;
                                                    }
                                                    new AlertDialog.Builder(TransferActivity.this)
                                                            .setMessage(resId)
                                                            .setNegativeButton(R.string.ok, (dialog, which) -> dialog.cancel())
                                                            .create().show();
                                                }
                                            }, 1500);

                                            BLog.i(tag(), "the transfer failed");
                                        }

                                        @Override
                                        public void onCompleted() {
                                        }
                                    });
                            })
                        .create();
                passwordDialog.show();
            }
        });
    }

}
