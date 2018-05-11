package io.brahmaos.wallet.brahmawallet.service;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.Wallet;
import org.web3j.crypto.WalletFile;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jFactory;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.request.RawTransaction;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionTimeoutException;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Contract;
import org.web3j.tx.ManagedTransaction;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.brahmaos.wallet.brahmawallet.BuildConfig;
import io.brahmaos.wallet.brahmawallet.common.BrahmaConfig;
import io.brahmaos.wallet.brahmawallet.common.BrahmaConst;
import io.brahmaos.wallet.brahmawallet.db.entity.AccountEntity;
import io.brahmaos.wallet.brahmawallet.db.entity.TokenEntity;
import io.brahmaos.wallet.util.BLog;
import rx.Observable;
import rx.Subscription;

public class BrahmaWeb3jService extends BaseService{
    @Override
    protected String tag() {
        return BrahmaWeb3jService.class.getName();
    }

    // singleton
    private static BrahmaWeb3jService instance = new BrahmaWeb3jService();
    public static BrahmaWeb3jService getInstance() {
        return instance;
    }

    public String generateLightNewWalletFile(String password, File destinationDirectory)
            throws CipherException, InvalidAlgorithmParameterException,
            NoSuchAlgorithmException, NoSuchProviderException, IOException {
        return WalletUtils.generateLightNewWalletFile(password, destinationDirectory);
    }

    public String getWalletAddress(String password, String filePath)
            throws IOException, CipherException {
        Credentials credentials = WalletUtils.loadCredentials(
                password, filePath);
        return credentials.getAddress();
    }

    public boolean isValidKeystore(WalletFile walletFile, String password)
            throws Exception {
        Credentials credentials = Credentials.create(Wallet.decrypt(password, walletFile));
        BigInteger privateKey = credentials.getEcKeyPair().getPrivateKey();
        BLog.e("viewModel", "the private key is:" + privateKey.toString(16));
        return WalletUtils.isValidPrivateKey(privateKey.toString(16));
    }

    public String prependHexPrefix(String input) {
        return Numeric.prependHexPrefix(input);
    }

    public Request<?, EthGetBalance> getEthBalance(AccountEntity account) {
        Web3j web3 = Web3jFactory.build(
                new HttpService(BrahmaConfig.getInstance().getNetworkUrl()));
        return web3.ethGetBalance(account.getAddress(), DefaultBlockParameterName.LATEST);
    }

    public Request<?, EthCall> getTokenBalance(AccountEntity account, TokenEntity token) {
        Web3j web3 = Web3jFactory.build(
                new HttpService(BrahmaConfig.getInstance().getNetworkUrl()));
        Function function = new Function(
                "balanceOf",
                Arrays.asList(new Address(account.getAddress())),  // Solidity Types in smart contract functions
                Arrays.asList(new TypeReference<Uint256>(){}));

        String encodedFunction = FunctionEncoder.encode(function);
        return web3.ethCall(
                Transaction.createEthCallTransaction(account.getAddress(), token.getAddress(), encodedFunction),
        DefaultBlockParameterName.LATEST);
    }

    public boolean isValidAddress(String address) {
        return address != null && address.length() >= 2 &&
                address.charAt(0) == '0' && address.charAt(1) == 'x' &&
                WalletUtils.isValidAddress(address);
    }

    /*
     * transfer eth
     */
    public void sendTransferEth(AccountEntity account, String password,
                                String destinationAddress, BigDecimal amount)
            throws IOException, CipherException,
            TransactionTimeoutException, InterruptedException {
        Web3j web3 = Web3jFactory.build(
                new HttpService(BrahmaConfig.getInstance().getNetworkUrl()));
        BLog.i(tag(), "Sending ETHER ");
        Credentials credentials = WalletUtils.loadCredentials(
                password, context.getFilesDir() + "/" +  account.getFilename());
        BLog.i(tag(), "load credential success");
        TransactionReceipt transferReceipt = Transfer.sendFunds(
                web3, credentials, destinationAddress, amount,
                Convert.Unit.ETHER);
        BLog.i(tag(), "Transaction complete, view it at https://rinkeby.etherscan.io/tx/"
                + transferReceipt.getTransactionHash());
    }

    /**
     *  transfer erc20 token
     */
    public void sendTransferToken(AccountEntity account, TokenEntity token, String password,
                                  String destinationAddress, BigDecimal amount)
            throws IOException, CipherException,
            TransactionTimeoutException, InterruptedException {
        BLog.i(tag(), "Sending Token ");
        Web3j web3 = Web3jFactory.build(
                new HttpService(BrahmaConfig.getInstance().getNetworkUrl()));
        Credentials credentials = WalletUtils.loadCredentials(
                password, context.getFilesDir() + "/" +  account.getFilename());
        BLog.i(tag(), "load credential success");
        Function function = new Function(
                "transfer",
                Arrays.<Type>asList(new Address(destinationAddress),
                        new Uint256(amount.multiply(new BigDecimal(Math.pow(10, 18))).toBigInteger())),
                Collections.<TypeReference<?>>emptyList());
        String encodedFunction = FunctionEncoder.encode(function);

        RawTransactionManager txManager = new RawTransactionManager(web3, credentials);
        EthSendTransaction transactionResponse = txManager.sendTransaction(
                ManagedTransaction.GAS_PRICE, Contract.GAS_LIMIT, token.getAddress(), encodedFunction, BigInteger.ZERO);

        if (transactionResponse.hasError()) {
            throw new RuntimeException("Error processing transaction request: "
                    + transactionResponse.getError().getMessage());
        }
        String transactionHash = transactionResponse.getTransactionHash();
        BLog.i(tag(), "===> transactionHash: " + transactionHash);
    }

    /**
     * Initiate a transaction request and
     * issue different events at different stages of the transaction,
     * for example: verifying the account, sending request
     * @return 1: verifying the account 2: sending request 10: transfer success
     */
    public Observable<Integer> sendTransfer(AccountEntity account, TokenEntity token, String password,
                                            String destinationAddress, BigDecimal amount) {
        return Observable.create(e -> {
            try {
                e.onNext(1);
                Web3j web3 = Web3jFactory.build(
                        new HttpService(BrahmaConfig.getInstance().getNetworkUrl()));
                Credentials credentials = WalletUtils.loadCredentials(
                        password, context.getFilesDir() + "/" +  account.getFilename());
                BLog.i(tag(), "load credential success");
                e.onNext(2);
                if (token.getName().toLowerCase().equals(BrahmaConst.ETHEREUM)) {
                    TransactionReceipt transferReceipt = Transfer.sendFunds(
                            web3, credentials, destinationAddress, amount,
                            Convert.Unit.ETHER);
                    BLog.i(tag(), "Transaction complete, view it at https://rinkeby.etherscan.io/tx/"
                            + transferReceipt.getTransactionHash());
                } else {
                    Function function = new Function(
                            "transfer",
                            Arrays.<Type>asList(new Address(destinationAddress),
                                    new Uint256(amount.multiply(new BigDecimal(Math.pow(10, 18))).toBigInteger())),
                            Collections.<TypeReference<?>>emptyList());
                    String encodedFunction = FunctionEncoder.encode(function);

                    RawTransactionManager txManager = new RawTransactionManager(web3, credentials);
                    EthSendTransaction transactionResponse = txManager.sendTransaction(
                            ManagedTransaction.GAS_PRICE, Contract.GAS_LIMIT, token.getAddress(), encodedFunction, BigInteger.ZERO);

                    if (transactionResponse.hasError()) {
                        throw new RuntimeException("Error processing transaction request: "
                                + transactionResponse.getError().getMessage());
                    }
                    String transactionHash = transactionResponse.getTransactionHash();
                    BLog.i(tag(), "===> transactionHash: " + transactionHash);
                }
                e.onNext(10);
            } catch (IOException | CipherException | TransactionTimeoutException | InterruptedException e1) {
                e1.printStackTrace();
                e.onError(e1);
            }
            e.onCompleted();
        });
    }

    public Observable<String> getPrivateKeyByPassword(String fileName, String password) {
        return Observable.create(e -> {
            try {
                Credentials credentials = WalletUtils.loadCredentials(
                        password, context.getFilesDir() + "/" +  fileName);
                BigInteger privateKey = credentials.getEcKeyPair().getPrivateKey();
                BLog.e("viewModel", "the private key is:" + privateKey.toString(16));
                if (WalletUtils.isValidPrivateKey(privateKey.toString(16))) {
                    e.onNext(privateKey.toString(16));
                } else {
                    e.onNext("");
                }
            } catch (IOException | CipherException e1) {
                e1.printStackTrace();
                e.onError(e1);
            }
            e.onCompleted();
        });
    }

    public boolean isValidPrivateKey(String privateKey) {
        return WalletUtils.isValidPrivateKey(privateKey);
    }

    public Observable<String> getKeystore(String fileName, String password) {
        return Observable.create(e -> {
            try {
                Credentials credentials = WalletUtils.loadCredentials(
                        password, context.getFilesDir() + "/" +  fileName);
                BigInteger privateKey = credentials.getEcKeyPair().getPrivateKey();
                BLog.e("viewModel", "the private key is:" + privateKey.toString(16));
                if (WalletUtils.isValidPrivateKey(privateKey.toString(16))) {
                    File file = new File(context.getFilesDir() + "/" +  fileName);
                    FileInputStream fis = new FileInputStream(file);
                    int length = fis.available();
                    byte [] buffer = new byte[length];
                    fis.read(buffer);
                    String keystore = new String(buffer);
                    fis.close();
                    e.onNext(keystore);
                } else {
                    e.onNext("");
                }
            } catch (IOException | CipherException e1) {
                e1.printStackTrace();
                e.onError(e1);
            }
            e.onCompleted();
        });
    }
}