package io.brahmaos.wallet.brahmawallet.ui.pay;

import android.annotation.SuppressLint;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.math.BigDecimal;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.brahmaos.wallet.brahmawallet.R;
import io.brahmaos.wallet.brahmawallet.api.ApiRespResult;
import io.brahmaos.wallet.brahmawallet.common.BrahmaConfig;
import io.brahmaos.wallet.brahmawallet.common.BrahmaConst;
import io.brahmaos.wallet.brahmawallet.common.IntentParam;
import io.brahmaos.wallet.brahmawallet.common.ReqParam;
import io.brahmaos.wallet.brahmawallet.db.entity.AccountEntity;
import io.brahmaos.wallet.brahmawallet.service.ImageManager;
import io.brahmaos.wallet.brahmawallet.service.PayService;
import io.brahmaos.wallet.brahmawallet.ui.base.BaseActivity;
import io.brahmaos.wallet.brahmawallet.ui.transfer.EthTransferActivity;
import io.brahmaos.wallet.brahmawallet.view.CustomProgressDialog;
import io.brahmaos.wallet.brahmawallet.view.PassWordLayout;
import io.brahmaos.wallet.brahmawallet.viewmodel.AccountViewModel;
import io.brahmaos.wallet.util.BLog;
import io.brahmaos.wallet.util.CommonUtil;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class PayAccountAddCreditActivity extends BaseActivity {
    @Override
    protected String tag() {
        return PayAccountAddCreditActivity.class.getName();
    }

    // UI references.
    @BindView(R.id.iv_pay_account_avatar)
    ImageView ivPayAccountAvatar;
    @BindView(R.id.tv_account_name)
    TextView tvAccountName;
    @BindView(R.id.tv_account_address)
    TextView tvAccountAddress;
    @BindView(R.id.layout_choose_token)
    RelativeLayout layoutChooseToken;
    @BindView(R.id.tv_coin_name)
    TextView tvCoinName;
    @BindView(R.id.iv_coin_icon)
    ImageView ivCoinIcon;
    @BindView(R.id.iv_credit_coin_icon)
    ImageView ivCreditCoinIcon;
    @BindView(R.id.et_credit_amount)
    EditText etCreditAmount;
    @BindView(R.id.btn_credit)
    Button btnCredit;

    private int chosenCoinCode = BrahmaConst.COIN_CODE_BRM;
    private AccountEntity account;
    private AccountViewModel mViewModel;

    private CustomProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pay_add_credit);
        showNavBackBtn();
        ButterKnife.bind(this);
        mViewModel = ViewModelProviders.of(this).get(AccountViewModel.class);
        initView();
        initData();
        initCoinType(chosenCoinCode);
    }

    private void initView() {
        layoutChooseToken.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_credit_coins, null);
            builder.setView(dialogView);
            builder.setCancelable(true);
            final AlertDialog alertDialog = builder.create();
            alertDialog.show();

            RelativeLayout layoutCoinBrm = dialogView.findViewById(R.id.layout_coin_brm);
            layoutCoinBrm.setOnClickListener(v1 -> {
                initCoinType(BrahmaConst.COIN_CODE_BRM);
                alertDialog.cancel();
            });
            RelativeLayout layoutCoinEth = dialogView.findViewById(R.id.layout_coin_eth);
            layoutCoinEth.setOnClickListener(v1 -> {
                initCoinType(BrahmaConst.COIN_CODE_ETH);
                alertDialog.cancel();
            });
            RelativeLayout layoutCoinBtc = dialogView.findViewById(R.id.layout_coin_btc);
            layoutCoinBtc.setOnClickListener(v1 -> {
                initCoinType(BrahmaConst.COIN_CODE_BTC);
                alertDialog.cancel();
            });
        });

        btnCredit.setOnClickListener(v -> {
            String sendValueStr = etCreditAmount.getText().toString();
            if (sendValueStr.length() < 1) {
                showLongToast(R.string.tip_invalid_amount);
                return;
            }
            Uri uri = Uri.parse(String.format("brahmaos://wallet/pay?credit=1&amount=%s&coin_code=%d&sender=%s",
                    sendValueStr, chosenCoinCode, BrahmaConfig.getInstance().getPayAccount()));
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
            /*PayService.getInstance().createCreditPreOrder(BrahmaConfig.getInstance().getPayAccount(),
                    chosenCoinCode, sendValueStr, "")
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<Map>() {
                        @Override
                        public void onNext(Map orderInfo) {
                            if (progressDialog != null) {
                                progressDialog.cancel();
                            }
                            if (orderInfo != null && orderInfo.containsKey(ReqParam.PARAM_ORDER_ID)
                                    && orderInfo.containsKey(ReqParam.PARAM_RECEIVER)) {
                                String orderId = (String) orderInfo.get(ReqParam.PARAM_ORDER_ID);
                                String receipt = (String) orderInfo.get(ReqParam.PARAM_RECEIVER);
                                showLongToast(String.format("orderId: %s ; receipt: %s;", orderId, receipt));
                                BLog.d(tag(), String.format("orderId: %s ; receipt: %s;", orderId, receipt));
                            }
                        }

                        @Override
                        public void onError(Throwable e) {
                            e.printStackTrace();
                        }

                        @Override
                        public void onCompleted() {

                        }
                    });*/
        });
    }

    private void initData() {
        String accountAddress = BrahmaConfig.getInstance().getPayAccount();
        tvAccountAddress.setText(accountAddress);
    }

    private void initCoinType(int coinCode) {
        chosenCoinCode = coinCode;
        if (coinCode == BrahmaConst.COIN_CODE_BRM) {
            ImageManager.showTokenIcon(this, ivCoinIcon, R.drawable.icon_brm);
            ImageManager.showTokenIcon(this, ivCreditCoinIcon, R.drawable.icon_brm);
            tvCoinName.setText(String.format("%s (%s)", BrahmaConst.COIN_SYMBOL_BRM, BrahmaConst.COIN_BRM));
        } else if (coinCode == BrahmaConst.COIN_CODE_ETH) {
            ImageManager.showTokenIcon(this, ivCoinIcon, R.drawable.icon_eth);
            ImageManager.showTokenIcon(this, ivCreditCoinIcon, R.drawable.icon_eth);
            tvCoinName.setText(String.format("%s (%s)", BrahmaConst.COIN_SYMBOL_ETH, BrahmaConst.COIN_ETH));
        } else if (coinCode == BrahmaConst.COIN_CODE_BTC) {
            ImageManager.showTokenIcon(this, ivCoinIcon, R.drawable.icon_btc);
            ImageManager.showTokenIcon(this, ivCreditCoinIcon, R.drawable.icon_btc);
            tvCoinName.setText(String.format("%s (%s)", BrahmaConst.COIN_SYMBOL_BTC, BrahmaConst.COIN_BTC));
        }
    }
}
