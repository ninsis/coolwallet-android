package com.coolbitx.coolwallet.general;

import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;

import com.coolbitx.coolwallet.DataBase.DatabaseHelper;
import com.coolbitx.coolwallet.DataBase.DbName;
import com.coolbitx.coolwallet.bean.Account;
import com.coolbitx.coolwallet.bean.CWAccountKeyInfo;
import com.coolbitx.coolwallet.bean.Constant;
import com.coolbitx.coolwallet.bean.dbAddress;
import com.coolbitx.coolwallet.callback.RefreshCallback;
import com.coolbitx.coolwallet.httpRequest.BlockChainAPI;
import com.coolbitx.coolwallet.httpRequest.CwBtcNetWork;
import com.coolbitx.coolwallet.util.BTCUtils;
import com.coolbitx.coolwallet.util.ExtendedKey;
import com.crashlytics.android.Crashlytics;
import com.snscity.egdwlib.CmdManager;
import com.snscity.egdwlib.cmd.CmdResultCallback;
import com.snscity.egdwlib.utils.LogUtil;

import org.json.JSONException;

import java.util.ArrayList;

import static com.coolbitx.coolwallet.ui.Fragment.FragMainActivity.ACCOUNT_CNT;

/**
 * Created by ShihYi on 2016/3/10.
 * by account Refresh
 */
public class RefreshBlockChainInfo {

    final int accountID;
    final Context mContext;
    CwBtcNetWork cwBtcNetWork;
    boolean mResult;
    int IntExtKey;
    int IntIntKey;
    boolean isPointerSame;
    int needToRefreshCnt;
    int addrAuccess;
    CmdManager vCmdmanager;
    private RefreshCallback cmdResultCallback;
    Handler BIP32Handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Bundle data = msg.getData();
            String result = data.getString("resultMsg");
            switch (msg.what) {
                case 1:
                    addrAuccess++;
                    LogUtil.i("BIP32 handler addrAuccess=" + addrAuccess + " & needToRefreshCnt=" + needToRefreshCnt);
                    if (addrAuccess == needToRefreshCnt) {
                        LogUtil.i("BIP32 handler RUN callTxsRunnable");
                        cmdResultCallback.onSuccess();

                    }
                    break;
                case 0:
                    cmdResultCallback.onFailure(result);
                    break;
            }
        }
    };
    Handler txsHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Bundle data = msg.getData();
            int mAccountID = data.getInt("accountID");
            switch (msg.what) {
                case 1:
                    cmdResultCallback.onSuccess();
                    break;
                case -1:
                    cmdResultCallback.onFailure("Account:" + (mAccountID + 1) + " get transaction data from BLOCKCHAIN failed, Please do again later.");
//                    showNoticeDialog("Erro Message", "Get Txs data from BLOCKCHAIN failed!");
                    break;
            }
        }
    };

    public RefreshBlockChainInfo(Context mContext, int accountID) {
        this.accountID = accountID;
        this.mContext = mContext;
        cwBtcNetWork = new CwBtcNetWork();
        mResult = true;
        IntExtKey = 0;
        IntIntKey = 0;
        isPointerSame = true;
        needToRefreshCnt = 0;
        addrAuccess = 0;

    }

    public void FunQueryAccountInfo(final CmdManager cmdManager, final RefreshCallback cmdResultCallback) {
        this.cmdResultCallback = cmdResultCallback;
        this.vCmdmanager = cmdManager;
        final byte cwHdwAccountInfoName = 0x00;
        final byte cwHdwAccountInfoBalance = 0x01;
        final byte cwHdwAccountInfoExtKeyPtr = 0x02;
        final byte cwHdwAccountInfoIntKeyPtr = 0x03;
        final byte cwHdwAccountInfoBlockAmount = 0x04;

        final byte[] cwHdwAccountInfo = new byte[]{cwHdwAccountInfoName, cwHdwAccountInfoBalance,
                cwHdwAccountInfoExtKeyPtr, cwHdwAccountInfoIntKeyPtr, cwHdwAccountInfoBlockAmount};

        final boolean[] flag = new boolean[5];

        long dbTotalBalance = 0;
        int dbExtKey = 0;
        int dbIntKey = 0;
        ArrayList<dbAddress> listAddress = new ArrayList<dbAddress>();
        listAddress = DatabaseHelper.queryAddress(mContext, accountID, -1);// all addr (ext int)

        for (int i = 0; i < listAddress.size(); i++) {
            dbTotalBalance += listAddress.get(i).getBalance();
            if (listAddress.get(i).getKcid() == 0) {
                dbExtKey++;
            }
            if (listAddress.get(i).getKcid() == 1) {
                dbIntKey++;
            }
        }

        LogUtil.e("getDB accountId=" + accountID + " ;TotalBalance=" + dbTotalBalance + " ;extKey=" + dbExtKey + " ;intKey=" + dbIntKey);

        final Account account = new Account();
        account.setId(accountID);

        for (int i = 0; i < cwHdwAccountInfo.length; i++) {
            final int qryAcctInfoIndex = i;
            final int ChkDbExtKey = dbExtKey;
            final int ChkDbIntKey = dbIntKey;

            vCmdmanager.hdwQueryAccountInfo(cwHdwAccountInfo[i], accountID, new CmdResultCallback() {
                @Override
                public void onSuccess(int status, byte[] outputData) {
                    if ((status + 65536) == 0x9000) {
                        if (outputData != null) {

                            LogUtil.i("SWITCH=" + cwHdwAccountInfo[qryAcctInfoIndex]);
                            switch (cwHdwAccountInfo[qryAcctInfoIndex]) {
                                case cwHdwAccountInfoName:

                                    String accName = new String(outputData, Constant.UTF8).trim();
                                    LogUtil.e("accName=" + accName);
                                    account.setName(accName);
                                    flag[0] = true;
                                    break;
                                case cwHdwAccountInfoBalance:

//                                    String strbalance = PublicPun.byte2HexString(outputData).replace(" ", "");
//                                    long balance = Long.valueOf(strbalance, 16);
//
//                                    double TotalBalance = balance * PublicPun.SATOSHI_RATE;
//
//                                    LogUtil.e("balance hex=" + strbalance);
//                                    LogUtil.i("balance long=" + balance);
//                                    LogUtil.i("balance double=" + TotalBalance);
//                                    account.setTotalBalance(TotalBalance);
                                    flag[1] = true;
                                    break;

                                case cwHdwAccountInfoExtKeyPtr:

                                    String extKey = PublicPun.byte2HexString(BTCUtils.reverse(outputData)).replace(" ", "");
                                    IntExtKey = Integer.valueOf(extKey, 16);
                                    LogUtil.i("hdwQueryAccountInfo accID=" + accountID + "; InfoExtKeyPtr=" + cwHdwAccountInfoExtKeyPtr + "; ExtKey pointer=" + IntExtKey);
                                    account.setOutputIndex(IntExtKey);

                                    //卡片的地址數跟DB裡的資料不一樣,要重抓資料
                                    //→ 1.DEL DB
                                    //→ 2.Gen addresses
                                    //→ 3.Get BlockChain
                                    //→ 4.Save DB
                                    //→ 5.Show data
                                    if (IntExtKey == ChkDbExtKey) {
                                        LogUtil.i("Account " + accountID + " 的卡片externalPointer跟DB一樣!");

                                        ArrayList<CWAccountKeyInfo> cwList =
                                                DatabaseHelper.queryAccountKeyInfo(mContext, -1);
                                        LogUtil.e("KEYINFO account cwList.size()=" + cwList.size() + "; cnt=" + ACCOUNT_CNT);
                                        if (cwList.size() != ACCOUNT_CNT * 2) {//如果keyinfo個數不等於account數，重新從卡片讀取
                                            LogUtil.d("cwList size=" + cwList.size());
                                            for (int i = 0; i < ACCOUNT_CNT; i++) {

                                                getAccountKeyInfo(i, IntExtKey, Constant.CwAddressKeyChainExternal, false);
                                            }
                                        }

                                    } else {
                                        needToRefreshCnt++;
                                        isPointerSame = false;
                                        LogUtil.d("Account " + accountID + " 的卡片externalPointer跟DB不一樣!");
                                        DatabaseHelper.deleteTableByAccountAndKcid(mContext, DbName.DB_TABLE_ADDR, accountID, Constant.CwAddressKeyChainExternal);
                                        getAccountKeyInfo(accountID, IntExtKey, Constant.CwAddressKeyChainExternal, true);
                                    }
                                    flag[2] = true;
                                    break;

                                case cwHdwAccountInfoIntKeyPtr:

                                    String intKey = PublicPun.byte2HexString(BTCUtils.reverse(outputData)).replace(" ", "");
                                    IntIntKey = Integer.valueOf(intKey, 16);
                                    LogUtil.d("hdwQueryAccountInfo accID=" + accountID + "; key=" + cwHdwAccountInfoIntKeyPtr + "; intKey pointer=" + IntIntKey);
                                    account.setInputIndex(IntIntKey);

                                    //卡片的地址數跟DB裡的資料不一樣,要重抓資料
                                    //→ 1.DEL DB
                                    //→ 2.Gen addresses
                                    //→ 3.Get BlockChain
                                    //→ 4.Save DB
                                    //→ 5.Show data (progress dismiss)

                                    if (IntIntKey == ChkDbIntKey) {
                                        LogUtil.d("Account " + accountID + " 的卡片internalPointer跟DB一樣!");
                                        //query全跑完後作業
                                        ArrayList<CWAccountKeyInfo> cwList =
                                                DatabaseHelper.queryAccountKeyInfo(mContext, -1);
                                        if (cwList.size() != ACCOUNT_CNT * 2) {
                                            for (int i = 0; i < ACCOUNT_CNT; i++) {
                                                getAccountKeyInfo(i, IntIntKey, Constant.CwAddressKeyChainInternal, false);
                                            }
                                        }
                                    } else {
                                        needToRefreshCnt++;
                                        isPointerSame = false;
                                        LogUtil.d("Account " + accountID + " 的卡片internalPointer跟DB不一樣!");
                                        DatabaseHelper.deleteTableByAccountAndKcid(mContext, DbName.DB_TABLE_ADDR, accountID, Constant.CwAddressKeyChainInternal);
                                        getAccountKeyInfo(accountID, IntIntKey, Constant.CwAddressKeyChainInternal, true);
                                    }
                                    flag[3] = true;
                                    break;

                                case cwHdwAccountInfoBlockAmount:

                                    String blockAmount = PublicPun.byte2HexString(outputData).replace(" ", "");
                                    LogUtil.e("blockAmount=" + blockAmount);
//                                    account.setBlockAmount(Double.parseDouble(blockAmount));
                                    flag[4] = true;
                                    break;
                            }

                            if (flag[0] && flag[1] && flag[2] && flag[3] && flag[4]) {
                                LogUtil.i("QryAccInfo跑完=" + accountID);
                                if (isPointerSame) {
                                    cmdResultCallback.onSuccess();

                                }
                            }
                        }
                    }
                }
            });
        }
    }

    private void getAccountKeyInfo(final int accountId, final int kid, final byte kcId, final boolean runBIP32) {
//        getAccountKeyInfo(accountID, IntIntKey, Constant.CwAddressKeyChainInternal);
        vCmdmanager.hdwQueryAccountKeyInfo(Constant.CwHdwAccountKeyInfoPubKeyAndChainCd,
                kcId,
                accountId,
                kid,
                new CmdResultCallback() {
                    @Override
                    public void onSuccess(int status, byte[] outputData) {
                        if ((status + 65536) == 0x9000) {
                            if (outputData != null) {
                                byte[] publicKeyBytes = new byte[64];
                                byte[] chainCodeBytes = new byte[32];
                                int length = outputData.length;
                                byte[] extendPub = new byte[33];
                                if (length >= 96) {

                                    for (int i = 0; i < 64; i++) {
                                        publicKeyBytes[i] = outputData[i];
                                    }
                                    for (int j = 64; j < 96; j++) {
                                        chainCodeBytes[j - 64] = outputData[j];
                                    }
                                    //最後兩個字元一起
                                    LogUtil.i("account=" + accountId + " ;kcid=" + kcId + " ;kid=" + kid + " ;建地址的public key=" + LogUtil.byte2HexString(publicKeyBytes));
                                    LogUtil.i("account=" + accountId + " ;kcid=" + kcId + " ;kid=" + kid + " ;建地址的chainCodeBytes=" + LogUtil.byte2HexString(chainCodeBytes));

                                    int mFirstKey = Integer.parseInt(PublicPun.byte2HexString(publicKeyBytes[63]), 16);
//                                    LogUtil.i("public key的最後字元=" + PublicPun.byte2HexString(publicKeyBytes[63]) +
//                                            ";轉int=" + mFirstKey);

                                    //format last charactors
                                    if (mFirstKey % 2 == 0) {
                                        extendPub[0] = 02;
                                    } else {
                                        extendPub[0] = 03;
                                    }
                                    for (int a = 0; a < 32; a++) {
                                        extendPub[a + 1] = publicKeyBytes[a];
                                    }
                                }

                                DatabaseHelper.insertAccountKeyInfo(mContext, accountId, kcId,
                                        PublicPun.byte2HexStringNoBlank(extendPub), PublicPun.byte2HexStringNoBlank(chainCodeBytes));

                                if (runBIP32) {
                                    LogUtil.i("go BIP32Runnable account=" + accountId + "; KCID=" + kcId + "; kid=" + kid);
                                    ContentValues cv = new ContentValues();
                                    cv.put("URL", BtcUrl.URL_BLICKCHAIN_TXS_MULTIADDR);
                                    cv.put("ACCOUNT_ID", accountId);
                                    cv.put("KID", kid);
                                    cv.put("KCID", kcId);
                                    cv.put("PUBLIC_KEY", extendPub);
                                    cv.put("CHAIN_CODE", chainCodeBytes);
                                    //getAddressesTxs

                                    new Thread(new BIP32Runnable(BIP32Handler, cv, 0, 0)).start();
                                }
                            }
                        }
                    }
                });
    }

    private class BIP32Runnable implements Runnable {
        ContentValues cv;
        int what = -1;
        Handler handler;
        int interval;
        String extralUrl;
        byte kcId;
        byte[] extendPub;
        byte[] chainCodeBytes;
        int getAddrID;
        int haveTrsCnt;
        int accountId;
        int msgCnt;
        int kid;
        ExtendedKey km;

        public BIP32Runnable(Handler handler, ContentValues cv, int what, int interval) {
            this.cv = cv;
            this.what = what;
            this.handler = handler;
            this.interval = interval;

            this.extralUrl = cv.getAsString("URL");
            this.accountId = cv.getAsInteger("ACCOUNT_ID");
            this.kcId = cv.getAsByte("KCID");
            this.extendPub = cv.getAsByteArray("PUBLIC_KEY");
            this.chainCodeBytes = cv.getAsByteArray("CHAIN_CODE");
            this.kid = cv.getAsInteger("KID");
            this.haveTrsCnt = 0;
            this.getAddrID = 0;
            this.msgCnt = 0;
            km = ExtendedKey.createCwEk(extendPub, chainCodeBytes);
        }

        @Override
        public void run() {
            String result = "";
            for (int i = 0; i < kid; i++) {
                LogUtil.i("kcid=" + kcId + ", 產地址的serializepub=" + km.serializepub(true));
                ExtendedKey k = null;
                String addr = "";
                try {
                    k = km.getChild(i);
                    addr = k.getAddress();
                    LogUtil.i("ExtendedKey:第 " + accountId + "account的" + +kcId + "kcid的第 " + String.valueOf(i) + " 個地址= " + k.getAddress());
                    //18xNmo8ZoiZwuCfUaLjACUtntbpwpZ2jc9; address which is no trs.
                    cv.put("addresses", addr);
                    DatabaseHelper.insertAddress(mContext, accountId, addr, kcId, i, 0, 0);
                } catch (Exception e) {
                    LogUtil.i("ExtendedKey:" + kcId + " 第 " + String.valueOf(getAddrID) + " 個地址,error:" + e.getMessage());
                    Message msg = handler.obtainMessage();
                    Bundle data = new Bundle();
                    data.putString("resultMsg", e.getMessage() + " create error!");
                    msg.setData(data);
                    msgCnt = 0;
                    msg.what = msgCnt;
                    handler.sendMessage(msg);
//                    mProgress.dismiss();
                }
            }
            msgCnt = 1;
            LogUtil.i("sendMessage:" + msgCnt);
            Message msg = handler.obtainMessage();
            Bundle data = new Bundle();
            data.putString("result", String.valueOf(accountId) + String.valueOf(kcId));
            msg.setData(data);
            msg.what = msgCnt;
            handler.sendMessage(msg);
            LogUtil.i("ACCOUNT:" + accountId + "-" + kcId + "的haveTrsCnt 數量:" + haveTrsCnt);
        }
    }

    public void callTxsRunnable(RefreshCallback cmdResultCallback) {
        this.cmdResultCallback = cmdResultCallback;
        ContentValues cv = new ContentValues();
        cv.put("ACCOUNT_ID", accountID);
        new Thread(new GetTxsRunnable(txsHandler, cv, 0, 0)).start();
    }

    private class GetTxsRunnable implements Runnable {

        ContentValues cv;
        int AccountID;

        public GetTxsRunnable(Handler handler, ContentValues cv, int what, int interval) {
            this.cv = cv;
            this.AccountID = cv.getAsInteger("ACCOUNT_ID");
        }

        @Override
        public void run() {
            getTxsFromBlockchain(AccountID);
        }
    }

    int updateAddrResult;

    private void getTxsFromBlockchain(final int mAccount) {
        LogUtil.i("refresh跑account:" + mAccount);
        //分5個account跑addr的txs.
        ArrayList<dbAddress> listAddress = new ArrayList<dbAddress>();

        String mTxsAddresses = "";
        listAddress = DatabaseHelper.queryAddress(mContext, mAccount, -1);//ext+int
        for (int j = 0; j < listAddress.size(); j++) {
            mTxsAddresses += listAddress.get(j).getAddress() + '|';
        }
        mTxsAddresses = mTxsAddresses.substring(0, mTxsAddresses.length() - 1);

        final ContentValues cv = new ContentValues();
        cv.put("addresses", mTxsAddresses);

        String result = null;
        int msgResult = -1;
        //使用Handler和Message把資料丟給主UI去後續處理
        Message msg = txsHandler.obtainMessage();

        result = cwBtcNetWork.doGet(cv, BtcUrl.URL_BLICKCHAIN_TXS_MULTIADDR, null);
//        result = "{\"errorCode\": " + "404" + "}"; //for test node API
        if (!TextUtils.isEmpty(result) && !result.contains("errorCode")) {
            //call blockchain success
            DatabaseHelper.deleteTableByAccount(mContext, DbName.DB_TABLE_TXS, mAccount);
            try {
                int mWalletTxsN = PublicPun.jsonParserRefresh(mContext, result, mAccount, true, false);
                if (mWalletTxsN > 50) {
                    int mRunTxsCnt = (int) Math.round((mWalletTxsN / 50) + 0.5);
                    LogUtil.i("refresh超過50筆,要跑幾次=" + mRunTxsCnt);
                    for (int i = 1; i < mRunTxsCnt; i++) {
                        int param = i * 50 + 1;

                        //https://blockchain.info/multiaddr?offset=51&active=
                        LogUtil.i("refresh超過50筆,跑param=" + param);
                        result = cwBtcNetWork.doGet(cv, BtcUrl.URL_BLICKCHAIN_TXS_MULTIADDR, String.valueOf(param));
                        PublicPun.jsonParserRefresh(mContext, result, mAccount, false, false);
                    }
                }
                msgResult = 1;
            } catch (Exception e) {
                // TODO Auto-generated catch block
                msgResult = -1;
                e.printStackTrace();
                Crashlytics.logException(e);
            }
        } else {
            //call blockchain failed , call Node to update balance
            msgResult = new BlockChainAPI(mContext).updateBalance(cv);
        }
        Bundle data = new Bundle();
        data.putInt("accountID", mAccount);
        data.putString("msg", result);
        msg.setData(data);
        msg.what = msgResult;
        txsHandler.sendMessage(msg);

    }
}
