package io.brahmaos.wallet.brahmawallet.service;

import android.content.Context;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

import io.brahmaos.wallet.brahmawallet.api.ApiRespResult;
import io.brahmaos.wallet.brahmawallet.common.BrahmaConfig;
import io.brahmaos.wallet.brahmawallet.common.BrahmaConst;
import io.brahmaos.wallet.brahmawallet.db.entity.AccountEntity;
import io.brahmaos.wallet.brahmawallet.event.EventTypeDef;
import io.brahmaos.wallet.brahmawallet.model.BitcoinDownloadProgress;
import io.brahmaos.wallet.brahmawallet.ui.pay.QuickPayActivity;
import io.brahmaos.wallet.util.BLog;
import io.brahmaos.wallet.util.CommonUtil;
import io.brahmaos.wallet.util.RxEventBus;
import rx.Observable;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class BtcAccountManager extends BaseService{
    @Override
    protected String tag() {
        return BtcAccountManager.class.getName();
    }

    // singleton
    private static BtcAccountManager instance = new BtcAccountManager();
    public static BtcAccountManager getInstance() {
        return instance;
    }

    public static int BYTES_PER_BTC_KB = 1000;
    public static int MIN_CONFIRM_BLOCK_HEIGHT = 6;
    private String CHECK_POINTS_NAME = "checkpoints_testnet";

    private Map<String, WalletAppKit> btcAccountKit = new HashMap<>();

    @Override
    public boolean init(Context context) {
        super.init(context);
        if (BrahmaConfig.debugFlag) {
            CHECK_POINTS_NAME = "checkpoints_testnet";
        } else {
            CHECK_POINTS_NAME = "checkpoints";
        }
        return true;
    }

    public NetworkParameters getNetworkParams() {
        if (BrahmaConfig.debugFlag) {
            return TestNet3Params.get();
        } else {
            return MainNetParams.get();
        }
    }

    private DownloadProgressTracker listener = new DownloadProgressTracker() {
        @Override
        public void onChainDownloadStarted(Peer peer, int blocksLeft) {
            super.onChainDownloadStarted(peer, blocksLeft);
            String tip = "the kit-----> start is" + peer.toString() + "------->:" + blocksLeft
                    + "thread id is:" + Thread.currentThread().getId();
            BLog.d(tag(), tip);
        }

        @Override
        protected void progress(double pct, int blocksSoFar, Date date) {
            String tip = String.format(Locale.US, "Chain download %d%% done with %d blocks to go, block date %s", (int) pct, blocksSoFar,
                    Utils.dateTimeFormat(date));
            System.out.println(tip);
            BitcoinDownloadProgress progress = new BitcoinDownloadProgress();
            progress.setProgressPercentage(pct);
            progress.setBlocksLeft(blocksSoFar);
            progress.setCurrentBlockDate(date);
            progress.setDownloaded(false);
            RxEventBus.get().post(EventTypeDef.BTC_ACCOUNT_SYNC, progress);
        }

        /**
         * Called when download is initiated.
         *
         * @param blocks the number of blocks to download, estimated
         */
        protected void startDownload(int blocks) {
            String tip = "the kit-----> " + "Downloading block chain of size " + blocks + ". " +
                    (blocks > 1000 ? "This may take a while." : "");
            BLog.d(tag(), tip);
            BitcoinDownloadProgress progress = new BitcoinDownloadProgress();
            progress.setBlocksLeft(blocks);
            progress.setDownloaded(false);
            RxEventBus.get().post(EventTypeDef.BTC_ACCOUNT_SYNC, progress);
        }

        /**
         * Called when we are done downloading the block chain.
         */
        protected void doneDownload() {
            BLog.d(tag(), "the kit-----> down loaded");
            BitcoinDownloadProgress progress = new BitcoinDownloadProgress();
            progress.setDownloaded(true);
            RxEventBus.get().post(EventTypeDef.BTC_ACCOUNT_SYNC, progress);
        }
    };

    private TransactionConfidenceEventListener txListener = new TransactionConfidenceEventListener() {
        @Override
        public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
            if (tx.getConfidence().getDepthInBlocks() <= MIN_CONFIRM_BLOCK_HEIGHT) {
                RxEventBus.get().post(EventTypeDef.BTC_TRANSACTION_CHANGE, tx);
            }
        }
    };

    public void initExistsWalletAppKit(AccountEntity accountEntity) {
        WalletAppKit kit = new WalletAppKit(getNetworkParams(), context.getFilesDir(),
                accountEntity.getFilename()) {
            @Override
            protected void onSetupCompleted() {
                // This is called in a background thread after startAndWait is called, as setting up various objects
                // can do disk and network IO that may cause UI jank/stuttering in wallet apps if it were to be done
                // on the main thread.
                System.out.println("the setup completed;");
                RxEventBus.get().post(EventTypeDef.BTC_APP_KIT_INIT_SET_UP, true);
            }
        };
        kit.setDownloadListener(listener);

        kit.setBlockingStartup(false);
        kit.startAsync();
        kit.awaitRunning();
        kit.wallet().addTransactionConfidenceEventListener(txListener);
        btcAccountKit.put(accountEntity.getFilename(), kit);
    }

    public void createWalletAppKit(String filePrefix, DeterministicSeed seed) {
        WalletAppKit kit = new WalletAppKit(getNetworkParams(), context.getFilesDir(), filePrefix) {
            @Override
            protected void onSetupCompleted() {
                // This is called in a background thread after startAndWait is called, as setting up various objects
                // can do disk and network IO that may cause UI jank/stuttering in wallet apps if it were to be done
                // on the main thread.
                System.out.println("the setup completed;");
            }
        };
        kit.restoreWalletFromSeed(seed);
        kit.setDownloadListener(listener);

        // set checkpoints
        InputStream ins = context.getResources().openRawResource(context.getResources().getIdentifier(CHECK_POINTS_NAME,
                "raw", context.getPackageName()));
        kit.setCheckpoints(ins);

        kit.setBlockingStartup(false);
        kit.startAsync();
        kit.awaitRunning();
        kit.wallet().addTransactionConfidenceEventListener(txListener);
        btcAccountKit.put(filePrefix, kit);
    }

    public WalletAppKit restoreWalletAppKit(String filePrefix, DeterministicSeed seed) {
        WalletAppKit kit = new WalletAppKit(getNetworkParams(), context.getFilesDir(), filePrefix){
            @Override
            protected void onSetupCompleted() {
                // This is called in a background thread after startAndWait is called, as setting up various objects
                // can do disk and network IO that may cause UI jank/stuttering in wallet apps if it were to be done
                // on the main thread.
                System.out.println("the setup completed;");
                //peerGroup().setFastCatchupTimeSecs(createTime);
            }
        };
        kit.restoreWalletFromSeed(seed);
        kit.setDownloadListener(listener);

        kit.setBlockingStartup(false);
        kit.startAsync();
        kit.awaitRunning();
        kit.wallet().addTransactionConfidenceEventListener(txListener);
        btcAccountKit.put(filePrefix, kit);
        return kit;
    }

    public WalletAppKit getBtcWalletAppKit(String filePrefix) {
        if (btcAccountKit.containsKey(filePrefix)) {
            return btcAccountKit.get(filePrefix);
        } else {
            return null;
        }
    }

    public boolean transfer(String receiveAddress, BigDecimal amount, long fee, String accountFilename) {
        try {
            WalletAppKit kit = btcAccountKit.get(accountFilename);
            Coin value = Coin.valueOf(CommonUtil.convertSatoshiFromBTC(amount).longValue());
            System.out.println("Forwarding " + value.toFriendlyString() + " BTC");
            Address to = Address.fromBase58(getNetworkParams(), receiveAddress);
            Transaction transaction = new Transaction(getNetworkParams());
            transaction.addOutput(value, to);

            SendRequest request = SendRequest.forTx(transaction);
            request.feePerKb = Coin.valueOf(fee);
            Wallet.SendResult sendResult = kit.wallet().sendCoins(kit.peerGroup(), request);

            ListeningExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

            sendResult.broadcastComplete.addListener(new Runnable() {
                @Override
                public void run() {
                    // The wallet has changed now, it'll get auto saved shortly or when the app shuts down.
                    System.out.println("Sent coins onwards! Transaction hash is " +
                            sendResult.tx.getHashAsString());
                    RxEventBus.get().post(EventTypeDef.BTC_TRANSACTION_BROADCAST_COMPLETE, sendResult.tx.getHashAsString());
                }
            }, executorService);
            return true;
        } catch (Exception e) {
            e.fillInStackTrace();
            return false;
        }
    }

    public Observable<String> accountRecharge(String receiveAddress, BigDecimal amount, long fee,
                                              String accountFilename, String orderId) {
        return Observable.create(e -> {
            try {
                WalletAppKit kit = btcAccountKit.get(accountFilename);
                Coin value = Coin.valueOf(CommonUtil.convertSatoshiFromBTC(amount).longValue());
                System.out.println("Forwarding " + value.toFriendlyString() + " BTC");
                Address to = Address.fromBase58(getNetworkParams(), receiveAddress);
                Transaction transaction = new Transaction(getNetworkParams());
                transaction.addOutput(value, to);

                SendRequest request = SendRequest.forTx(transaction);
                request.feePerKb = Coin.valueOf(fee);
                kit.wallet().completeTx(request);
                String txHash = request.tx.getHashAsString();

                if (txHash == null || txHash.isEmpty()) {
                    e.onNext(null);
                } else {
                    PayService.getInstance().rechargeOrder(orderId, txHash)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Observer<ApiRespResult>() {
                                @Override
                                public void onCompleted() {
                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    throwable.printStackTrace();
                                    e.onError(throwable);
                                }

                                @Override
                                public void onNext(ApiRespResult apr) {
                                    if (apr != null && apr.getResult() == 0) {
                                        try {
                                            kit.wallet().commitTx(request.tx);
                                            Wallet.SendResult result = new Wallet.SendResult();
                                            result.tx = request.tx;
                                            // The tx has been committed to the pending pool by this point (via sendCoinsOffline -> commitTx), so it has
                                            // a txConfidenceListener registered. Once the tx is broadcast the peers will update the memory pool with the
                                            // count of seen peers, the memory pool will update the transaction confidence object, that will invoke the
                                            // txConfidenceListener which will in turn invoke the wallets event listener onTransactionConfidenceChanged
                                            // method.
                                            result.broadcast = kit.peerGroup().broadcastTransaction(request.tx);
                                            result.broadcastComplete = result.broadcast.future();

                                            ListeningExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

                                            result.broadcastComplete.addListener(new Runnable() {
                                                @Override
                                                public void run() {
                                                    // The wallet has changed now, it'll get auto saved shortly or when the app shuts down.
                                                    System.out.println("Sent coins onwards! Transaction hash is " +
                                                            result.tx.getHashAsString());
                                                    RxEventBus.get().post(EventTypeDef.BTC_TRANSACTION_BROADCAST_COMPLETE, result.tx.getHashAsString());
                                                }
                                            }, executorService);
                                            e.onNext(result.tx.getHashAsString());
                                        } catch (Exception e1) {
                                            e1.printStackTrace();
                                            e.onNext(null);
                                        }
                                        e.onCompleted();
                                    } else {
                                        e.onError(new RuntimeException("Error eth send transaction"));
                                    }
                                }
                            });
                }
            } catch (Exception e1) {
                e1.printStackTrace();
                e.onError(e1);
            }
        });
    }

    // Ordinary payment with bitcoin
    public Observable<String> ordinaryPaymentWithBtc(String receiveAddress, BigDecimal amount, long fee,
                                                     String accountFilename, String orderId, String remark) {
        return Observable.create(e -> {
            try {
                WalletAppKit kit = btcAccountKit.get(accountFilename);
                Coin value = Coin.valueOf(CommonUtil.convertSatoshiFromBTC(amount).longValue());
                System.out.println("Forwarding " + value.toFriendlyString() + " BTC");
                Address to = Address.fromBase58(getNetworkParams(), receiveAddress);
                Transaction transaction = new Transaction(getNetworkParams());
                transaction.addOutput(value, to);

                SendRequest request = SendRequest.forTx(transaction);
                request.feePerKb = Coin.valueOf(fee);
                kit.wallet().completeTx(request);
                String txHash = request.tx.getHashAsString();

                if (txHash == null || txHash.isEmpty()) {
                    e.onNext(null);
                } else {
                    Map<String, Object> params = new HashMap<>();
                    params.put(QuickPayActivity.PARAM_PRE_PAY_ID, orderId);
                    params.put(QuickPayActivity.PARAM_SENDER, kit.wallet().currentChangeAddress().toBase58());
                    params.put(QuickPayActivity.PARAM_COIN_CODE, BrahmaConst.PAY_COIN_CODE_BTC);
                    params.put(QuickPayActivity.PARAM_RECEIVER, receiveAddress);
                    params.put(QuickPayActivity.PARAM_TX_HASH, txHash);
                    params.put(QuickPayActivity.PARAM_REMARK, remark);

                    PayService.getInstance().paymentOrder(params)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Observer<ApiRespResult>() {
                                @Override
                                public void onCompleted() {
                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    throwable.printStackTrace();
                                    e.onError(throwable);
                                }

                                @Override
                                public void onNext(ApiRespResult apr) {
                                    if (apr != null && apr.getResult() == 0) {
                                        try {
                                            kit.wallet().commitTx(request.tx);
                                            Wallet.SendResult result = new Wallet.SendResult();
                                            result.tx = request.tx;
                                            // The tx has been committed to the pending pool by this point (via sendCoinsOffline -> commitTx), so it has
                                            // a txConfidenceListener registered. Once the tx is broadcast the peers will update the memory pool with the
                                            // count of seen peers, the memory pool will update the transaction confidence object, that will invoke the
                                            // txConfidenceListener which will in turn invoke the wallets event listener onTransactionConfidenceChanged
                                            // method.
                                            result.broadcast = kit.peerGroup().broadcastTransaction(request.tx);
                                            result.broadcastComplete = result.broadcast.future();
                                            ListeningExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

                                            result.broadcastComplete.addListener(new Runnable() {
                                                @Override
                                                public void run() {
                                                    // The wallet has changed now, it'll get auto saved shortly or when the app shuts down.
                                                    System.out.println("Sent coins onwards! Transaction hash is " +
                                                            result.tx.getHashAsString());
                                                    RxEventBus.get().post(EventTypeDef.BTC_TRANSACTION_BROADCAST_COMPLETE, result.tx.getHashAsString());
                                                }
                                            }, executorService);
                                            e.onNext(result.tx.getHashAsString());
                                        } catch (Exception e1) {
                                            e1.printStackTrace();
                                            e.onNext(null);
                                        }
                                        e.onCompleted();
                                    } else {
                                        e.onError(new RuntimeException("Error eth send transaction"));
                                    }
                                }
                            });
                }
            } catch (Exception e1) {
                e1.printStackTrace();
                e.onError(e1);
            }
        });
    }

    public boolean isValidBtcAddress(String address) {
        try {
            Address.fromBase58(getNetworkParams(), address);
            return true;
        } catch (Exception e) {
            e.fillInStackTrace();
            return false;
        }
    }
}
