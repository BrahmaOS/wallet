package io.brahmaos.wallet.brahmawallet.ui.transfer;

import android.Manifest;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.BottomSheetDialog;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.common.base.Splitter;

import org.bitcoinj.kits.WalletAppKit;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.brahmaos.wallet.brahmawallet.FingerprintCore;
import io.brahmaos.wallet.brahmawallet.R;
import io.brahmaos.wallet.brahmawallet.common.BrahmaConfig;
import io.brahmaos.wallet.brahmawallet.common.BrahmaConst;
import io.brahmaos.wallet.brahmawallet.common.IntentParam;
import io.brahmaos.wallet.brahmawallet.common.ReqCode;
import io.brahmaos.wallet.brahmawallet.db.entity.AccountEntity;
import io.brahmaos.wallet.brahmawallet.event.EventTypeDef;
import io.brahmaos.wallet.brahmawallet.model.AccountAssets;
import io.brahmaos.wallet.brahmawallet.service.BtcAccountManager;
import io.brahmaos.wallet.brahmawallet.service.ImageManager;
import io.brahmaos.wallet.brahmawallet.statistic.utils.StatisticEventAgent;
import io.brahmaos.wallet.brahmawallet.ui.account.BtcAccountAssetsActivity;
import io.brahmaos.wallet.brahmawallet.ui.base.BaseActivity;
import io.brahmaos.wallet.brahmawallet.ui.common.barcode.CaptureActivity;
import io.brahmaos.wallet.brahmawallet.ui.common.barcode.Intents;
import io.brahmaos.wallet.brahmawallet.ui.contact.ChooseContactActivity;
import io.brahmaos.wallet.brahmawallet.view.CustomStatusView;
import io.brahmaos.wallet.brahmawallet.viewmodel.AccountViewModel;
import io.brahmaos.wallet.util.BLog;
import io.brahmaos.wallet.util.BitcoinPaymentURI;
import io.brahmaos.wallet.util.CommonUtil;
import io.brahmaos.wallet.util.DataCryptoUtils;
import io.brahmaos.wallet.util.RxEventBus;
import rx.Observable;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;

public class BtcTransferActivity extends BaseActivity implements FingerprintCore.SimpleAuthenticationCallback{

    @Override
    protected String tag() {
        return BtcTransferActivity.class.getName();
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
    @BindView(R.id.tv_btc_balance)
    TextView tvBtcBalance;
    @BindView(R.id.btn_show_transfer_info)
    Button btnShowTransfer;
    @BindView(R.id.et_receiver_address)
    EditText etReceiverAddress;
    @BindView(R.id.et_amount)
    EditText etAmount;
    @BindView(R.id.tv_amount_all)
    TextView tvAllAmount;
    @BindView(R.id.layout_text_input_remark)
    TextInputLayout layoutRemarkInput;
    @BindView(R.id.et_remark)
    EditText etRemark;
    @BindView(R.id.et_btc_miner_fee)
    EditText etBtcMinerFee;
    @BindView(R.id.iv_contacts)
    ImageView ivContacts;

    private AccountEntity mAccount;
    private AccountViewModel mViewModel;
    private WalletAppKit kit;
    private List<AccountEntity> mAccounts = new ArrayList<>();
    private Observable<String> btcTxBroadcastComplete;

    private BottomSheetDialog transferInfoDialog;
    private TextView tvTransferStatus;
    private CustomStatusView customStatusView;
    private FingerprintCore mFingerprintCore;
    private AlertDialog mFingerDialog = null;
    private LinearLayout layoutTransferStatus;
    private String receiverAddress;
    private BigDecimal finalAmount;
    private long feePerKb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_btc_transfer);
        ButterKnife.bind(this);
        showNavBackBtn();
        mAccount = (AccountEntity) getIntent().getSerializableExtra(IntentParam.PARAM_ACCOUNT_INFO);

        btcTxBroadcastComplete = RxEventBus.get().register(EventTypeDef.BTC_TRANSACTION_BROADCAST_COMPLETE, String.class);
        btcTxBroadcastComplete.onBackpressureBuffer()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<String>() {
                    @Override
                    public void onNext(String hash) {
                        if (hash != null && hash.length() > 0 && transferInfoDialog != null
                                && tvTransferStatus != null && customStatusView != null) {
                            tvTransferStatus.setText(R.string.progress_transfer_success);
                            BLog.i(tag(), "the transfer success");
                            customStatusView.loadSuccess();
                            new Handler().postDelayed(() -> {
                                transferInfoDialog.cancel();
                                Intent intent = new Intent(BtcTransferActivity.this, BtcAccountAssetsActivity.class);
                                intent.putExtra(IntentParam.PARAM_ACCOUNT_ID, mAccount.getId());
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                finish();
                            }, 1200);
                        }
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

        initView();
        mFingerprintCore = new FingerprintCore(this);
        mFingerprintCore.setCallback(this);
    }

    private void initView() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            ActionBar ab = getSupportActionBar();
            if (ab != null) {
                ab.setTitle(getString(R.string.account_btc) + getString(R.string.blank_space) +
                        getString(R.string.action_transfer));
            }
        }

        mViewModel = ViewModelProviders.of(this).get(AccountViewModel.class);
        mViewModel.getAccounts().observe(this, accountEntities -> {
            if (accountEntities != null) {
                mAccounts.clear();
                for (AccountEntity accountEntity : accountEntities) {
                    if (accountEntity.getType() == BrahmaConst.BTC_ACCOUNT_TYPE) {
                        mAccounts.add(accountEntity);
                    }
                }

                if (mAccounts != null && mAccounts.size() > 1) {
                    tvChangeAccount.setVisibility(View.VISIBLE);
                } else {
                    tvChangeAccount.setVisibility(View.GONE);
                    if (mAccounts == null || mAccounts.size() == 0) {
                        finish();
                    }
                }

                if (mAccount == null) {
                    mAccount = mAccounts.get(0);
                }
                kit = BtcAccountManager.getInstance().getBtcWalletAppKit(mAccount.getFilename());
                showAccountInfo(mAccount);
            }
        });

        etBtcMinerFee.setText(String.valueOf(BrahmaConst.DEFAULT_MINER_FEE));
        btnShowTransfer.setOnClickListener(v -> {
            StatisticEventAgent.onClick(this, "btn_show_transfer_info");
            showTransferInfo();
        });

        ivContacts.setOnClickListener(v -> {
            Intent intent = new Intent(BtcTransferActivity.this, ChooseContactActivity.class);
            intent.putExtra(IntentParam.PARAM_ACCOUNT_TYPE, BrahmaConst.BTC_ACCOUNT_TYPE);
            startActivityForResult(intent, ReqCode.CHOOSE_TRANSFER_CONTACT);
        });

        tvAllAmount.setOnClickListener(v -> {
            showMaxTransferAmount();
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_transfer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_scan) {
            StatisticEventAgent.onClick(this, "menu_scan");
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                requestCameraScanPermission();
            } else {
                scanAddressCode();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
         RxEventBus.get().unregister(EventTypeDef.BTC_TRANSACTION_BROADCAST_COMPLETE, btcTxBroadcastComplete);
        if (transferInfoDialog != null && transferInfoDialog.isShowing()) {
            transferInfoDialog.cancel();
        }
    }

    private void scanAddressCode() {
        Intent intent = new Intent(this, CaptureActivity.class);
        intent.putExtra(Intents.Scan.PROMPT_MESSAGE, "");
        startActivityForResult(intent, ReqCode.SCAN_QR_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        BLog.d(tag(), "requestCode: " + requestCode + "  ;resultCode" + resultCode);
        if (requestCode == ReqCode.SCAN_QR_CODE) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    String qrCode = data.getStringExtra(Intents.Scan.RESULT);
                    BitcoinPaymentURI bitcoinUri = BitcoinPaymentURI.parse(qrCode);
                    if (bitcoinUri == null) {
                        showLongToast(R.string.invalid_btc_address);
                        return;
                    }
                    etReceiverAddress.setText(bitcoinUri.getAddress());
                    if (bitcoinUri.getAmount() != null) {
                        etAmount.setText(String.valueOf(bitcoinUri.getAmount()));
                    } else {
                        etAmount.setText("");
                    }
                }
            }
        } else if (requestCode == ReqCode.CHOOSE_TRANSFER_CONTACT) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    String address = data.getStringExtra(IntentParam.PARAM_CONTACT_ADDRESS);
                    if (address != null && address.length() > 0) {
                        etReceiverAddress.setText(address);
                    }
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void handleCameraScanPermission() {
        scanAddressCode();
    }

    private void showAccountInfo(AccountEntity account) {
        if (account != null && kit != null && kit.wallet() != null) {
            ImageManager.showAccountAvatar(this, ivAccountAvatar, account);
            tvAccountName.setText(account.getName());
            tvAccountAddress.setText(CommonUtil.generateSimpleAddress(kit.wallet().currentReceiveAddress().toBase58()));
            tvBtcBalance.setText(String.valueOf(CommonUtil.convertUnit(BrahmaConst.BITCOIN, kit.wallet().getBalance().value)));
        }
    }

    private void showMaxTransferAmount() {
        if (kit != null && kit.wallet() != null) {
            int inputSize = kit.wallet().getUnspents().size();
            // calculate transaction size
            int txBytes = inputSize * 180 + 2 * 34 + 10 + 40;
            String feePerByte = etBtcMinerFee.getText().toString().trim();
            int feePrice = Integer.valueOf(feePerByte);
            long maxAmount = kit.wallet().getBalance().value - feePrice * txBytes;
            etAmount.setText(String.valueOf(CommonUtil.convertUnit(BrahmaConst.BITCOIN, maxAmount)));
        }
    }

    private void showTransferInfo() {
        receiverAddress = etReceiverAddress.getText().toString().trim();
        String transferAmount = etAmount.getText().toString().trim();
        String remark = etRemark.getText().toString().trim();
        BigDecimal amount = BigDecimal.ZERO;
        String feePerByte = etBtcMinerFee.getText().toString().trim();

        String tips = "";
        boolean cancel = false;
        if (!BtcAccountManager.getInstance().isValidBtcAddress(receiverAddress)) {
            tips = getString(R.string.tip_error_btc_address);
            cancel = true;
        }

        if (!cancel && feePerByte.length() < 1) {
            tips = getString(R.string.tip_invalid_fee);
            cancel = true;
        }

        BigDecimal feePrice = new BigDecimal(feePerByte);
        feePerKb = feePrice.multiply(new BigDecimal(BtcAccountManager.BYTES_PER_BTC_KB)).longValue();
        long btcTotalBalance = kit.wallet().getBalance().value;

        if (!cancel) {
            if (transferAmount.length() < 1) {
                tips = getString(R.string.tip_invalid_amount);
                cancel = true;
            } else {
                amount = new BigDecimal(transferAmount);
            }
        }

        if (!cancel && (amount.compareTo(BigDecimal.ZERO) <= 0 ||
                CommonUtil.convertSatoshiFromBTC(amount).longValue() > btcTotalBalance)) {
            tips = getString(R.string.tip_invalid_amount);
            cancel = true;
        }

        if (!cancel && btcTotalBalance <= 0) {
            tips = getString(R.string.tip_insufficient_eth);
            cancel = true;
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

        transferInfoDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_dialog_btc_transfer_info, null);
        transferInfoDialog.setContentView(view);
        transferInfoDialog.setCancelable(false);
        transferInfoDialog.show();

        ImageView ivCloseDialog = view.findViewById(R.id.iv_close_dialog);
        ivCloseDialog.setOnClickListener(v -> transferInfoDialog.cancel());

        TextView tvDialogPayToAddress = view.findViewById(R.id.tv_pay_to_address);
        tvDialogPayToAddress.setText(receiverAddress);

        TextView tvBtcTxFee = view.findViewById(R.id.tv_btc_fee);
        tvBtcTxFee.setText(feePerByte);

        TextView tvTransferAmount = view.findViewById(R.id.tv_dialog_transfer_amount);
        tvTransferAmount.setText(String.valueOf(amount));

        layoutTransferStatus = view.findViewById(R.id.layout_transfer_status);
        customStatusView = view.findViewById(R.id.as_status);
        tvTransferStatus = view.findViewById(R.id.tv_transfer_status);
        Button confirmBtn = view.findViewById(R.id.btn_commit_transfer);
        finalAmount = amount;
        confirmBtn.setOnClickListener(v -> {
            StatisticEventAgent.onClick(BtcTransferActivity.this, "btn_commit_transfer");
            if (kit != null && kit.wallet() != null) {
                final String mainAddress;
                if (kit.wallet().getActiveKeyChain() != null &&
                        kit.wallet().getActiveKeyChain().getIssuedReceiveKeys() != null &&
                        kit.wallet().getActiveKeyChain().getIssuedReceiveKeys().size() > 0) {
                    mainAddress = kit.wallet().getActiveKeyChain().getIssuedReceiveKeys().get(0).
                            toAddress(BtcAccountManager.getInstance().getNetworkParams()).toBase58();
                } else {
                    mainAddress = kit.wallet().currentChangeAddress().toBase58();
                }
                if (BrahmaConfig.getInstance().getTouchIDPayState(mainAddress)) {
                    showFingerprintDialog(mainAddress);
                } else {
                    showPasswordDialog();
                }
            }
        });
    }

    private boolean isValidPassword(String password) {
        String mnemonicsCode = DataCryptoUtils.aes128Decrypt(mAccount.getCryptoMnemonics(), password);
        if (mnemonicsCode != null) {
            List<String> mnemonicsCodes = Splitter.on(" ").splitToList(mnemonicsCode);
            if (mnemonicsCodes.size() == 0 || mnemonicsCodes.size() % 3 > 0) {
                showPasswordErrorDialog();
                return false;
            } else {
                return true;
            }
        } else {
            showPasswordErrorDialog();
            return false;
        }
    }

    private void showPasswordErrorDialog() {
        AlertDialog errorDialog = new AlertDialog.Builder(this)
                .setMessage(R.string.error_current_password)
                .setCancelable(true)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    dialog.cancel();
                })
                .create();
        errorDialog.show();
    }


    private void showFingerprintDialog(String mainAddress) {
        mFingerDialog = new AlertDialog.Builder(this)
                .setCancelable(false)
                .create();
        View fingerView = View.inflate(this, R.layout.fingerdialog, null);
        TextView cancelTv = fingerView.findViewById(R.id.fingerprint_cancel_tv);
        cancelTv.setOnClickListener(tv -> {
            mFingerprintCore.stopListening();
            if (!BrahmaConfig.getInstance().getTouchIDPayState(mainAddress)) {
                mFingerprintCore.clearTouchIDPay(mainAddress);
            }
            mFingerDialog.cancel();
        });
        mFingerDialog.show();
        mFingerDialog.setContentView(fingerView);

        try {
            mFingerprintCore.decryptWithFingerprint(mainAddress);
        } catch (Exception e) {
            BLog.d(tag(), "Failed to decryptWithFingerprint: " + e.toString());
            mFingerDialog.cancel();
            showPayChoiceDialog(getString(R.string.fingerprint_changed));
        }
    }

    private void showPasswordDialog() {
        final View dialogView = getLayoutInflater().inflate(R.layout.dialog_account_password, null);
        EditText etPassword = dialogView.findViewById(R.id.et_password);
        AlertDialog passwordDialog = new AlertDialog.Builder(BtcTransferActivity.this, R.style.Theme_AppCompat_Light_Dialog_Alert_Self)
                .setView(dialogView)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel())
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    dialog.cancel();
                    String password = etPassword.getText().toString();
                    if (isValidPassword(password)) {
                        // show transfer progress
                        layoutTransferStatus.setVisibility(View.VISIBLE);
                        if (BtcAccountManager.getInstance().transfer(receiverAddress, finalAmount, feePerKb, mAccount.getFilename())) {
                            customStatusView.loadLoading();
                        } else {
                            customStatusView.loadFailure();
                            tvTransferStatus.setText(R.string.progress_transfer_fail);
                            new Handler().postDelayed(() -> {
                                layoutTransferStatus.setVisibility(View.GONE);
                                int resId = R.string.tip_error_transfer;
                                new AlertDialog.Builder(BtcTransferActivity.this)
                                        .setMessage(resId)
                                        .setNegativeButton(R.string.ok, (dialog1, which1) -> dialog1.cancel())
                                        .create().show();
                            }, 1500);
                        }
                    }
                })
                .create();
        passwordDialog.setOnShowListener(dialog -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(etPassword, InputMethodManager.SHOW_IMPLICIT);
        });
        passwordDialog.show();
    }

    private void showPayChoiceDialog(String msg) {
        new AlertDialog.Builder(this)
                .setMessage(msg)
                .setCancelable(false)
                .setNegativeButton(R.string.ok, (dialog1, which1) -> dialog1.cancel())
                .setPositiveButton(getString(R.string.btn_use_password), (dialog1, which1) -> {
                    dialog1.cancel();
                    showPasswordDialog();
                })
                .create().show();
    }

    @Override
    public void onAuthenticationFail() {
        showLongToast(getString(R.string.fail_fingerprint_verification));
        if (mFingerDialog != null) {
            mFingerDialog.cancel();
        }
        showPayChoiceDialog(getString(R.string.fail_fingerprint_verification));
    }

    @Override
    public void onAuthenticationError(int errorCode, CharSequence errString) {
        showLongToast("" + errString);
        if (FingerprintManager.FINGERPRINT_ERROR_LOCKOUT == errorCode ||
                FingerprintManager.FINGERPRINT_ERROR_LOCKOUT_PERMANENT == errorCode) {
            if (mFingerDialog != null) {
                mFingerDialog.cancel();
            }
            showPayChoiceDialog("" + errString);
        }
    }

    @Override
    public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
        showLongToast("" + helpString);
    }

    @Override
    public void onAuthenticationSucceeded(String data) {
        if (mFingerDialog != null) {
            mFingerDialog.cancel();
        }
        if (isValidPassword(data)) {
            // show transfer progress
            layoutTransferStatus.setVisibility(View.VISIBLE);
            if (BtcAccountManager.getInstance().transfer(receiverAddress, finalAmount, feePerKb, mAccount.getFilename())) {
                customStatusView.loadLoading();
            } else {
                customStatusView.loadFailure();
                tvTransferStatus.setText(R.string.progress_transfer_fail);
                new Handler().postDelayed(() -> {
                    layoutTransferStatus.setVisibility(View.GONE);
                    int resId = R.string.tip_error_transfer;
                    new AlertDialog.Builder(BtcTransferActivity.this)
                            .setMessage(resId)
                            .setNegativeButton(R.string.ok, (dialog1, which1) -> dialog1.cancel())
                            .create().show();
                }, 1500);
            }
        }
    }
}
