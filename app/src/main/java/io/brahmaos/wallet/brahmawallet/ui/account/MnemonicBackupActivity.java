package io.brahmaos.wallet.brahmawallet.ui.account;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import io.brahmaos.wallet.brahmawallet.R;
import io.brahmaos.wallet.brahmawallet.common.IntentParam;
import io.brahmaos.wallet.brahmawallet.common.ReqCode;
import io.brahmaos.wallet.brahmawallet.db.entity.AccountEntity;
import io.brahmaos.wallet.brahmawallet.service.MainService;
import io.brahmaos.wallet.brahmawallet.ui.base.BaseActivity;
import io.brahmaos.wallet.util.BLog;

public class MnemonicBackupActivity extends BaseActivity {

    @Override
    protected String tag() {
        return MnemonicBackupActivity.class.getName();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mnemonic_backup);
        showNavBackBtn();
    }

    @Override
    protected void onStart() {
        super.onStart();
        List<String> mnemonicCode = getIntent().getStringArrayListExtra(IntentParam.PARAM_MNEMONIC_CODE);
        if (mnemonicCode == null) {
            mnemonicCode = MainService.getInstance().getMnemonicCode();
        }
        if (mnemonicCode == null || mnemonicCode.size() == 0 || mnemonicCode.size() % 3 > 0) {
            finish();
            return;
        }
        TextView tvMnemonics = findViewById(R.id.tv_account_mnemonics);
        StringBuilder mnemonicStr = new StringBuilder();
        for (String mnemonic : mnemonicCode) {
            mnemonicStr.append(mnemonic).append(" ");
        }
        tvMnemonics.setText(mnemonicStr.toString());

        Button nextBtn = findViewById(R.id.btn_next);
        ArrayList<String> finalMnemonicCode = (ArrayList<String>) mnemonicCode;
        nextBtn.setOnClickListener(v -> {
            Intent intent = new Intent(MnemonicBackupActivity.this,
                    ConfirmMnemonicActivity.class);
            intent.putStringArrayListExtra(IntentParam.PARAM_MNEMONIC_CODE, finalMnemonicCode);
            startActivityForResult(intent, ReqCode.CONFIRM_MNEMONIC);
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        BLog.d(tag(), "requestCode: " + requestCode + "  ;resultCode" + resultCode);
        if (requestCode == ReqCode.CONFIRM_MNEMONIC) {
            if (resultCode == RESULT_OK) {
                finish();
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
}
